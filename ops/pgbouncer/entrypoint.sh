#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-db-proxy}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-coin_system_cloud}"
DB_USER="${DB_USER:-foxya}"
DB_PASSWORD="${DB_PASSWORD:-change-this-in-production}"
PGBOUNCER_PORT="${PGBOUNCER_PORT:-6432}"
PGBOUNCER_POOL_MODE="${PGBOUNCER_POOL_MODE:-transaction}"
PGBOUNCER_MAX_CLIENT_CONN="${PGBOUNCER_MAX_CLIENT_CONN:-400}"
PGBOUNCER_DEFAULT_POOL_SIZE="${PGBOUNCER_DEFAULT_POOL_SIZE:-25}"
PGBOUNCER_MIN_POOL_SIZE="${PGBOUNCER_MIN_POOL_SIZE:-5}"
PGBOUNCER_RESERVE_POOL_SIZE="${PGBOUNCER_RESERVE_POOL_SIZE:-5}"
PGBOUNCER_RESERVE_POOL_TIMEOUT="${PGBOUNCER_RESERVE_POOL_TIMEOUT:-5}"
PGBOUNCER_MAX_DB_CONNECTIONS="${PGBOUNCER_MAX_DB_CONNECTIONS:-40}"
PGBOUNCER_QUERY_WAIT_TIMEOUT="${PGBOUNCER_QUERY_WAIT_TIMEOUT:-120}"
PGBOUNCER_SERVER_CONNECT_TIMEOUT="${PGBOUNCER_SERVER_CONNECT_TIMEOUT:-5}"
PGBOUNCER_IDLE_TRANSACTION_TIMEOUT="${PGBOUNCER_IDLE_TRANSACTION_TIMEOUT:-60}"

mkdir -p /etc/pgbouncer /var/log/pgbouncer /var/run/pgbouncer

cat > /etc/pgbouncer/pgbouncer.ini <<EOF
[databases]
${DB_NAME} = host=${DB_HOST} port=${DB_PORT} dbname=${DB_NAME} user=${DB_USER} password=${DB_PASSWORD}

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = ${PGBOUNCER_PORT}
unix_socket_dir = /var/run/pgbouncer
auth_type = plain
auth_file = /etc/pgbouncer/userlist.txt
admin_users = ${DB_USER}
stats_users = ${DB_USER}
pool_mode = ${PGBOUNCER_POOL_MODE}
max_client_conn = ${PGBOUNCER_MAX_CLIENT_CONN}
default_pool_size = ${PGBOUNCER_DEFAULT_POOL_SIZE}
min_pool_size = ${PGBOUNCER_MIN_POOL_SIZE}
reserve_pool_size = ${PGBOUNCER_RESERVE_POOL_SIZE}
reserve_pool_timeout = ${PGBOUNCER_RESERVE_POOL_TIMEOUT}
max_db_connections = ${PGBOUNCER_MAX_DB_CONNECTIONS}
query_wait_timeout = ${PGBOUNCER_QUERY_WAIT_TIMEOUT}
server_connect_timeout = ${PGBOUNCER_SERVER_CONNECT_TIMEOUT}
idle_transaction_timeout = ${PGBOUNCER_IDLE_TRANSACTION_TIMEOUT}
ignore_startup_parameters = extra_float_digits,options
server_reset_query = DISCARD ALL
log_connections = 1
log_disconnections = 1
EOF

cat > /etc/pgbouncer/userlist.txt <<EOF
"${DB_USER}" "${DB_PASSWORD}"
EOF

chown -R pgbouncer:pgbouncer /etc/pgbouncer /var/log/pgbouncer /var/run/pgbouncer

exec su-exec pgbouncer pgbouncer /etc/pgbouncer/pgbouncer.ini
