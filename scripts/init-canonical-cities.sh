#!/bin/sh
set -eu

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set ON_ERROR_STOP=1 \
  --single-transaction \
  --file /migrations/schema/017_canonical_cities.sql
