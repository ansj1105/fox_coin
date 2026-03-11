#!/usr/bin/env bash

set -euo pipefail

: "${PRIMARY_HOST:?PRIMARY_HOST is required}"
: "${PRIMARY_PORT:=5432}"
: "${REPL_USER:=replicator}"
: "${REPL_PASSWORD:?REPL_PASSWORD is required}"
: "${REPLICATION_SLOT:=fox_coin_standby_1}"
: "${STANDBY_VOLUME:=postgres-standby-data}"
: "${STANDBY_APPLICATION_NAME:=db_standby}"

docker volume create "${STANDBY_VOLUME}" >/dev/null

docker run --rm \
  -e PGPASSWORD="${REPL_PASSWORD}" \
  -v "${STANDBY_VOLUME}:/var/lib/postgresql/data" \
  postgres:15-alpine \
  sh -lc "
    rm -rf /var/lib/postgresql/data/* &&
    chown -R postgres:postgres /var/lib/postgresql/data &&
    su-exec postgres pg_basebackup \
      -h '${PRIMARY_HOST}' \
      -p '${PRIMARY_PORT}' \
      -D /var/lib/postgresql/data \
      -U '${REPL_USER}' \
      -X stream \
      -R \
      -S '${REPLICATION_SLOT}' &&
    printf \"primary_conninfo = 'host=${PRIMARY_HOST} port=${PRIMARY_PORT} user=${REPL_USER} password=${REPL_PASSWORD} application_name=${STANDBY_APPLICATION_NAME}'\nprimary_slot_name = '${REPLICATION_SLOT}'\nhot_standby = 'on'\n\" >> /var/lib/postgresql/data/postgresql.auto.conf &&
    touch /var/lib/postgresql/data/standby.signal &&
    chown -R postgres:postgres /var/lib/postgresql/data
  "

echo "Docker standby bootstrap completed for volume ${STANDBY_VOLUME}."
