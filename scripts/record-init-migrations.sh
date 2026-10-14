#!/bin/sh
set -eu

# Docker init has already applied every mounted schema file before this script.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
    version varchar(16) PRIMARY KEY,
    filename varchar(255) NOT NULL,
    checksum char(64) NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);
SQL

for file in /migrations/schema/[0-9][0-9][0-9]_*.sql; do
    filename=${file##*/}
    version=${filename%%_*}
    checksum=$(sed 's/\r$//' "$file" | sha256sum | cut -d ' ' -f 1)
    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q \
        -c "INSERT INTO schema_migrations(version,filename,checksum) VALUES ('$version','$filename','$checksum');"
done
