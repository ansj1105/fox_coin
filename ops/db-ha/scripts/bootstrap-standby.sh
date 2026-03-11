#!/usr/bin/env bash

set -euo pipefail

: "${PGDATA:?PGDATA is required}"
: "${PRIMARY_HOST:?PRIMARY_HOST is required}"
: "${PRIMARY_PORT:=5432}"
: "${REPL_USER:=replicator}"
: "${REPL_PASSWORD:?REPL_PASSWORD is required}"
: "${REPLICATION_SLOT:=fox_coin_standby_1}"
: "${STANDBY_APPLICATION_NAME:=db_standby}"

if [ ! -d "${PGDATA}" ]; then
  echo "PGDATA directory does not exist: ${PGDATA}" >&2
  exit 1
fi

if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl stop postgresql || true
fi

rm -rf "${PGDATA:?}/"*

export PGPASSWORD="${REPL_PASSWORD}"

pg_basebackup \
  -h "${PRIMARY_HOST}" \
  -p "${PRIMARY_PORT}" \
  -D "${PGDATA}" \
  -U "${REPL_USER}" \
  -X stream \
  -R \
  -C \
  -S "${REPLICATION_SLOT}"

cat >> "${PGDATA}/postgresql.auto.conf" <<EOF
primary_conninfo = 'host=${PRIMARY_HOST} port=${PRIMARY_PORT} user=${REPL_USER} password=${REPL_PASSWORD} application_name=${STANDBY_APPLICATION_NAME}'
primary_slot_name = '${REPLICATION_SLOT}'
hot_standby = 'on'
EOF

touch "${PGDATA}/standby.signal"

echo "Standby bootstrap completed."
echo "Start PostgreSQL on the standby node and verify with check-replication.sh."
