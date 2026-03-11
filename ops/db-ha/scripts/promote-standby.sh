#!/usr/bin/env bash

set -euo pipefail

: "${PGDATA:?PGDATA is required}"

pg_ctl -D "${PGDATA}" promote

echo "Standby promoted."
echo "Next:"
echo "  1. point DB_ADMIN_HOST to this node"
echo "  2. switch DB_PRIMARY_HOST / DB_STANDBY_HOST on the app server"
echo "  3. restart db-proxy: docker compose -f docker-compose.prod.yml up -d db-proxy"
