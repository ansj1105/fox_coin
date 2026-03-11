#!/usr/bin/env bash

set -euo pipefail

: "${PGHOST:=127.0.0.1}"
: "${PGPORT:=5432}"
: "${PGUSER:=postgres}"
: "${PGDATABASE:=postgres}"

ROLE="$(psql -Atc "select case when pg_is_in_recovery() then 'standby' else 'primary' end")"

echo "node_role=${ROLE}"

if [ "${ROLE}" = "primary" ]; then
  psql -P pager=off -c "
    SELECT
      application_name,
      client_addr,
      state,
      sync_state,
      write_lag,
      flush_lag,
      replay_lag
    FROM pg_stat_replication
    ORDER BY application_name;
  "
  exit 0
fi

psql -P pager=off -c "
  SELECT
    pg_last_wal_receive_lsn() AS receive_lsn,
    pg_last_wal_replay_lsn() AS replay_lsn,
    now() - pg_last_xact_replay_timestamp() AS replay_delay;
"
