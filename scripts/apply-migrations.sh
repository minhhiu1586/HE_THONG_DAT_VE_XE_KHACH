#!/bin/sh
set -eu

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
export PGPASSWORD="$POSTGRES_PASSWORD"

psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    version varchar(16) PRIMARY KEY,
    filename varchar(255) NOT NULL,
    checksum char(64) NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);
SQL

for file in /migrations/schema/[0-9][0-9][0-9]_*.sql; do
    [ -f "$file" ] || continue
    filename=${file##*/}
    version=${filename%%_*}
    # Git on Windows may check out CRLF. Compare SQL content consistently,
    # while accepting the raw checksum already recorded by older runners.
    checksum=$(sed 's/\r$//' "$file" | sha256sum | cut -d ' ' -f 1)
    raw_checksum=$(sha256sum "$file" | cut -d ' ' -f 1)
    recorded=$(psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atq \
        -c "SELECT checksum FROM schema_migrations WHERE version = '$version'")
    if [ -n "$recorded" ]; then
        if [ "$recorded" != "$checksum" ] && [ "$recorded" != "$raw_checksum" ]; then
            echo "Migration $filename changed after application; stop and create a new migration." >&2
            exit 1
        fi
        echo "Already applied: $filename"
        continue
    fi
    echo "Applying: $filename"
    {
        printf 'BEGIN;\n'
        cat "$file"
        printf "\nINSERT INTO schema_migrations(version, filename, checksum) VALUES ('%s', '%s', '%s');\nCOMMIT;\n" \
            "$version" "$filename" "$checksum"
    } | psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q
done
