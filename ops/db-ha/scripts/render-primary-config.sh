#!/usr/bin/env bash

set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF_DIR="${CONF_DIR:-${BASE_DIR}/generated}"
ARCHIVE_DIR="${ARCHIVE_DIR:-/var/lib/postgresql/wal_archive}"
SYNC_STANDBY_NAME="${SYNC_STANDBY_NAME:-db_standby}"
REPL_USER="${REPL_USER:-replicator}"
APP_SUBNET_CIDR="${APP_SUBNET_CIDR:-172.31.0.0/16}"
STANDBY_CIDR="${STANDBY_CIDR:-172.31.0.0/16}"

mkdir -p "${CONF_DIR}"

sed \
  -e "s#__SYNC_STANDBY_NAME__#${SYNC_STANDBY_NAME}#g" \
  -e "s#__ARCHIVE_DIR__#${ARCHIVE_DIR}#g" \
  "${BASE_DIR}/config/postgresql.primary.conf.template" \
  > "${CONF_DIR}/postgresql.primary.conf"

sed \
  -e "s#__REPL_USER__#${REPL_USER}#g" \
  -e "s#__APP_SUBNET_CIDR__#${APP_SUBNET_CIDR}#g" \
  -e "s#__STANDBY_CIDR__#${STANDBY_CIDR}#g" \
  "${BASE_DIR}/config/pg_hba.primary.conf.template" \
  > "${CONF_DIR}/pg_hba.primary.conf"

echo "Generated:"
echo "  ${CONF_DIR}/postgresql.primary.conf"
echo "  ${CONF_DIR}/pg_hba.primary.conf"
