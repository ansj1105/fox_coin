#!/usr/bin/env bash

set -euo pipefail

: "${PGHOST:=127.0.0.1}"
: "${PGPORT:=5432}"
: "${PGUSER:=postgres}"
: "${PGDATABASE:=postgres}"
: "${REPL_USER:=replicator}"
: "${REPL_PASSWORD:?REPL_PASSWORD is required}"
: "${REPLICATION_SLOT:=fox_coin_standby_1}"

psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${REPL_USER}') THEN
    EXECUTE format('CREATE ROLE %I WITH REPLICATION LOGIN PASSWORD %L', '${REPL_USER}', '${REPL_PASSWORD}');
  ELSE
    EXECUTE format('ALTER ROLE %I WITH REPLICATION LOGIN PASSWORD %L', '${REPL_USER}', '${REPL_PASSWORD}');
    EXECUTE format('ALTER ROLE %I WITH REPLICATION LOGIN', '${REPL_USER}');
  END IF;
END
\$\$;

SELECT slot_name
FROM pg_create_physical_replication_slot('${REPLICATION_SLOT}')
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_replication_slots
  WHERE slot_name = '${REPLICATION_SLOT}'
);
SQL

echo "Primary replication role and slot are ready."
