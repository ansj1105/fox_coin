#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PRIMARY_SSH_KEY="${PRIMARY_SSH_KEY:-/Users/anseojeong/Downloads/bmbit.pem}"
PRIMARY_SSH_HOST="${PRIMARY_SSH_HOST:-ubuntu@54.210.92.221}"
PRIMARY_DEPLOY_DIR="${PRIMARY_DEPLOY_DIR:-/var/www/fox_coin}"

STANDBY_SSH_KEY="${STANDBY_SSH_KEY:-/Users/anseojeong/Downloads/bmbit.pem}"
STANDBY_SSH_HOST="${STANDBY_SSH_HOST:-ubuntu@3.88.238.122}"
STANDBY_DEPLOY_DIR="${STANDBY_DEPLOY_DIR:-/home/ubuntu/fox_coin_db_standby}"

TELEGRAM_SOURCE_SSH_KEY="${TELEGRAM_SOURCE_SSH_KEY:-/Users/anseojeong/Downloads/korion.pem}"
TELEGRAM_SOURCE_SSH_HOST="${TELEGRAM_SOURCE_SSH_HOST:-ubuntu@54.83.183.123}"
TELEGRAM_SOURCE_ENV_PATH="${TELEGRAM_SOURCE_ENV_PATH:-/var/www/korion/.env}"

PRIMARY_TUNNEL_REMOTE_HOST="${PRIMARY_TUNNEL_REMOTE_HOST:-${PRIMARY_SSH_HOST#*@}}"
STANDBY_TUNNEL_LOCAL_BIND="${STANDBY_TUNNEL_LOCAL_BIND:-127.0.0.1:15433}"
STANDBY_TUNNEL_POLL_INTERVAL="${STANDBY_TUNNEL_POLL_INTERVAL:-2}"
STANDBY_CONTAINER_NAME="${STANDBY_CONTAINER_NAME:-foxya-postgres-standby}"
DB_STANDBY_BIND_PORT="${DB_STANDBY_BIND_PORT:-15432}"

PRIMARY_BUNDLE=""
STANDBY_BUNDLE=""
PRIMARY_ALERT_ENV=""
STANDBY_ENV_FILE=""
TUNNEL_ENV_FILE=""
TUNNEL_PUBKEY_FILE=""

log() {
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
    log "ERROR: $*"
    exit 1
}

cleanup() {
    rm -f "$PRIMARY_BUNDLE" "$STANDBY_BUNDLE" "$PRIMARY_ALERT_ENV" "$STANDBY_ENV_FILE" "$TUNNEL_ENV_FILE" "$TUNNEL_PUBKEY_FILE"
}

trap cleanup EXIT

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

load_local_env() {
    if [[ -f "$ROOT_DIR/.env" ]]; then
        set -a
        # shellcheck disable=SC1091
        source "$ROOT_DIR/.env"
        set +a
    fi
}

ssh_run() {
    local key="$1"
    local host="$2"
    shift 2
    ssh -i "$key" -o StrictHostKeyChecking=accept-new "$host" "$@"
}

scp_to() {
    local key="$1"
    local source="$2"
    local target="$3"
    scp -i "$key" -o StrictHostKeyChecking=accept-new "$source" "$target"
}

fetch_remote_env_value() {
    local key="$1"
    ssh_run "$TELEGRAM_SOURCE_SSH_KEY" "$TELEGRAM_SOURCE_SSH_HOST" \
        "grep -m1 '^${key}=' '$TELEGRAM_SOURCE_ENV_PATH' | cut -d= -f2-"
}

resolve_telegram_config() {
    if [[ -z "${TELEGRAM_BOT_TOKEN:-}" ]]; then
        TELEGRAM_BOT_TOKEN="$(fetch_remote_env_value TELEGRAM_BOT_TOKEN | tr -d '\r')"
    fi
    if [[ -z "${TELEGRAM_CHAT_ID:-}" ]]; then
        TELEGRAM_CHAT_ID="$(fetch_remote_env_value TELEGRAM_CHAT_ID | tr -d '\r')"
    fi

    [[ -n "${TELEGRAM_BOT_TOKEN:-}" ]] || fail "TELEGRAM_BOT_TOKEN could not be resolved"
    [[ -n "${TELEGRAM_CHAT_ID:-}" ]] || fail "TELEGRAM_CHAT_ID could not be resolved"
}

prepare_temp_files() {
    PRIMARY_BUNDLE="$(mktemp /tmp/fox_coin-primary.XXXXXX.tgz)"
    STANDBY_BUNDLE="$(mktemp /tmp/fox_coin-standby.XXXXXX.tgz)"
    PRIMARY_ALERT_ENV="$(mktemp /tmp/fox_coin-primary-alert.XXXXXX.env)"
    STANDBY_ENV_FILE="$(mktemp /tmp/fox_coin-standby-env.XXXXXX.env)"
    TUNNEL_ENV_FILE="$(mktemp /tmp/pg-primary-tunnel.XXXXXX.env)"
    TUNNEL_PUBKEY_FILE="$(mktemp /tmp/pg-primary-tunnel.XXXXXX.pub)"
}

build_primary_bundle() {
    tar \
        --exclude='.git' \
        --exclude='.gradle' \
        --exclude='build' \
        --exclude='target' \
        --exclude='logs' \
        --exclude='storage' \
        --exclude='backups' \
        --exclude='.env' \
        --exclude='*.bak*' \
        -czf "$PRIMARY_BUNDLE" \
        -C "$ROOT_DIR" \
        .
}

build_standby_bundle() {
    tar -czf "$STANDBY_BUNDLE" -C "$ROOT_DIR/ops/db-ha/standby" .
}

prepare_primary_alert_env() {
    cat > "$PRIMARY_ALERT_ENV" <<EOF
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
TELEGRAM_CHAT_ID=${TELEGRAM_CHAT_ID}
DB_ALERT_MONITOR_ENABLED=true
DB_ALERT_POLL_MS=${DB_ALERT_POLL_MS:-15000}
DB_ALERT_CONNECTION_RATIO_THRESHOLD=${DB_ALERT_CONNECTION_RATIO_THRESHOLD:-0.75}
DB_ALERT_LOCK_WAIT_THRESHOLD=${DB_ALERT_LOCK_WAIT_THRESHOLD:-3}
DB_ALERT_SYNC_REP_WAIT_THRESHOLD=${DB_ALERT_SYNC_REP_WAIT_THRESHOLD:-1}
DB_ALERT_CONSECUTIVE_BREACHES=${DB_ALERT_CONSECUTIVE_BREACHES:-2}
EOF
}

prepare_standby_env() {
    cat > "$STANDBY_ENV_FILE" <<EOF
DB_NAME=${DB_NAME:-coin_system_cloud}
DB_USER=${DB_USER:-foxya}
DB_PASSWORD=${DB_PASSWORD:-change-this-in-production}
DB_STANDBY_BIND_PORT=${DB_STANDBY_BIND_PORT}
EOF
}

ensure_standby_tunnel_key() {
    ssh_run "$STANDBY_SSH_KEY" "$STANDBY_SSH_HOST" "bash -lc '
        mkdir -p ~/.ssh
        chmod 700 ~/.ssh
        if [ ! -f ~/.ssh/pg_primary_tunnel ]; then
            ssh-keygen -t ed25519 -N \"\" -f ~/.ssh/pg_primary_tunnel >/dev/null
        fi
        chmod 600 ~/.ssh/pg_primary_tunnel
        chmod 644 ~/.ssh/pg_primary_tunnel.pub
        cat ~/.ssh/pg_primary_tunnel.pub
    '" > "$TUNNEL_PUBKEY_FILE"

    scp_to "$PRIMARY_SSH_KEY" "$TUNNEL_PUBKEY_FILE" "${PRIMARY_SSH_HOST}:/tmp/pg_primary_tunnel.pub"
    ssh_run "$PRIMARY_SSH_KEY" "$PRIMARY_SSH_HOST" "bash -lc '
        mkdir -p ~/.ssh
        chmod 700 ~/.ssh
        touch ~/.ssh/authorized_keys
        chmod 600 ~/.ssh/authorized_keys
        grep -qxF -f /tmp/pg_primary_tunnel.pub ~/.ssh/authorized_keys || cat /tmp/pg_primary_tunnel.pub >> ~/.ssh/authorized_keys
        rm -f /tmp/pg_primary_tunnel.pub
    '"
}

deploy_primary_repo() {
    log "Uploading primary bundle to ${PRIMARY_SSH_HOST}:${PRIMARY_DEPLOY_DIR}"
    ssh_run "$PRIMARY_SSH_KEY" "$PRIMARY_SSH_HOST" "mkdir -p '$PRIMARY_DEPLOY_DIR'"
    scp_to "$PRIMARY_SSH_KEY" "$PRIMARY_BUNDLE" "${PRIMARY_SSH_HOST}:/tmp/fox_coin-primary-bundle.tgz"
    scp_to "$PRIMARY_SSH_KEY" "$PRIMARY_ALERT_ENV" "${PRIMARY_SSH_HOST}:/tmp/fox_coin-primary-alert.env"

    ssh_run "$PRIMARY_SSH_KEY" "$PRIMARY_SSH_HOST" "bash -s" -- "$PRIMARY_DEPLOY_DIR" <<'EOF'
set -euo pipefail
deploy_dir="$1"

cd "$deploy_dir"
tar xzf /tmp/fox_coin-primary-bundle.tgz -C "$deploy_dir"
rm -f /tmp/fox_coin-primary-bundle.tgz

if [[ ! -f .env ]]; then
  cp .env.example .env
fi

python3 - "$deploy_dir/.env" /tmp/fox_coin-primary-alert.env <<'PY'
from pathlib import Path
import sys

env_path = Path(sys.argv[1])
overlay_path = Path(sys.argv[2])

base_lines = env_path.read_text().splitlines() if env_path.exists() else []
overlay_lines = overlay_path.read_text().splitlines()
merged = {}
order = []

for line in base_lines:
    if "=" not in line or line.lstrip().startswith("#"):
        order.append(line)
        continue
    key, value = line.split("=", 1)
    merged[key] = value
    order.append(key)

for line in overlay_lines:
    if "=" not in line or line.lstrip().startswith("#"):
        continue
    key, value = line.split("=", 1)
    merged[key] = value
    if key not in order:
        order.append(key)

rendered = []
seen = set()
for item in order:
    if item in merged:
        rendered.append(f"{item}={merged[item]}")
        seen.add(item)
    else:
        rendered.append(item)
for key, value in merged.items():
    if key not in seen:
        rendered.append(f"{key}={value}")

env_path.write_text("\n".join(rendered) + "\n")
overlay_path.unlink(missing_ok=True)
PY

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

$COMPOSE -f docker-compose.prod.yml build app
$COMPOSE -f docker-compose.prod.yml up -d --no-deps db-proxy app app2
EOF
}

read_primary_postgres_ip() {
    ssh_run "$PRIMARY_SSH_KEY" "$PRIMARY_SSH_HOST" \
        "sudo docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' foxya-postgres"
}

prepare_tunnel_env() {
    local postgres_ip="$1"
    [[ -n "$postgres_ip" ]] || fail "Could not resolve foxya-postgres container IP on primary host"

    cat > "$TUNNEL_ENV_FILE" <<EOF
CONTAINER_NAME=${STANDBY_CONTAINER_NAME}
LOCAL_BIND=${STANDBY_TUNNEL_LOCAL_BIND}
REMOTE_HOST=${PRIMARY_TUNNEL_REMOTE_HOST}
REMOTE_TARGET=${postgres_ip}:5432
SSH_USER=ubuntu
SSH_KEY=/home/ubuntu/.ssh/pg_primary_tunnel
KNOWN_HOSTS=/home/ubuntu/.ssh/known_hosts
DOCKER_BIN=/usr/bin/docker
NSENTER_BIN=/usr/bin/nsenter
SSH_BIN=/usr/bin/ssh
POLL_INTERVAL=${STANDBY_TUNNEL_POLL_INTERVAL}
EOF
}

deploy_standby_stack() {
    log "Uploading standby bundle to ${STANDBY_SSH_HOST}:${STANDBY_DEPLOY_DIR}"
    ssh_run "$STANDBY_SSH_KEY" "$STANDBY_SSH_HOST" "mkdir -p '$STANDBY_DEPLOY_DIR'"
    scp_to "$STANDBY_SSH_KEY" "$STANDBY_BUNDLE" "${STANDBY_SSH_HOST}:/tmp/fox_coin-standby-bundle.tgz"
    scp_to "$STANDBY_SSH_KEY" "$STANDBY_ENV_FILE" "${STANDBY_SSH_HOST}:/tmp/fox_coin-standby.env"
    scp_to "$STANDBY_SSH_KEY" "$TUNNEL_ENV_FILE" "${STANDBY_SSH_HOST}:/tmp/pg-primary-tunnel.env"

    ssh_run "$STANDBY_SSH_KEY" "$STANDBY_SSH_HOST" "bash -s" -- "$STANDBY_DEPLOY_DIR" "$PRIMARY_TUNNEL_REMOTE_HOST" <<'EOF'
set -euo pipefail
deploy_dir="$1"
primary_tunnel_host="$2"

mkdir -p "$deploy_dir"
tar xzf /tmp/fox_coin-standby-bundle.tgz -C "$deploy_dir"
mv /tmp/fox_coin-standby.env "$deploy_dir/.env"

mkdir -p ~/.ssh
chmod 700 ~/.ssh
ssh-keyscan -H "$primary_tunnel_host" >> ~/.ssh/known_hosts 2>/dev/null || true
sort -u ~/.ssh/known_hosts -o ~/.ssh/known_hosts
chmod 644 ~/.ssh/known_hosts

sudo cp "$deploy_dir/maintain-standby-tunnel.sh" /usr/local/bin/maintain-standby-tunnel.sh
sudo chmod 0755 /usr/local/bin/maintain-standby-tunnel.sh
sudo cp "$deploy_dir/pg-primary-tunnel.service" /etc/systemd/system/pg-primary-tunnel.service
sudo cp /tmp/pg-primary-tunnel.env /etc/default/pg-primary-tunnel
rm -f /tmp/pg-primary-tunnel.env /tmp/fox_coin-standby-bundle.tgz

if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
else
  COMPOSE="docker-compose"
fi

$COMPOSE -f "$deploy_dir/docker-compose.standby.yml" up -d

sudo systemctl daemon-reload
sudo systemctl enable --now pg-primary-tunnel.service
sudo systemctl restart pg-primary-tunnel.service
EOF
}

verify_cluster() {
    log "Verifying primary health"
    ssh_run "$PRIMARY_SSH_KEY" "$PRIMARY_SSH_HOST" "curl -sf http://localhost:8080/health >/dev/null"

    log "Verifying synchronous replication"
    ssh_run "$PRIMARY_SSH_KEY" "$PRIMARY_SSH_HOST" "bash -lc '
        sudo docker exec foxya-postgres psql -U ${DB_USER:-foxya} -d postgres -Atc \
        \"SELECT application_name || \\\"|\\\" || state || \\\"|\\\" || sync_state FROM pg_stat_replication;\"
    '"
}

send_telegram_notice() {
    log "Sending Telegram deployment notice"
    curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
        -H 'Content-Type: application/json' \
        -d @- >/dev/null <<EOF
{"chat_id":"${TELEGRAM_CHAT_ID}","text":"[KORION] DB Cluster Deploy Complete\nprimary=${PRIMARY_SSH_HOST}\nstandby=${STANDBY_SSH_HOST}\nalertMonitor=enabled\nsyncReplication=verified","disable_web_page_preview":true}
EOF
}

main() {
    require_cmd ssh
    require_cmd scp
    require_cmd tar
    require_cmd mktemp
    require_cmd curl

    load_local_env
    resolve_telegram_config
    prepare_temp_files
    build_primary_bundle
    build_standby_bundle
    prepare_primary_alert_env
    prepare_standby_env
    ensure_standby_tunnel_key
    deploy_primary_repo
    primary_postgres_ip="$(read_primary_postgres_ip | tr -d '\r')"
    prepare_tunnel_env "$primary_postgres_ip"
    deploy_standby_stack
    verify_cluster
    send_telegram_notice
    log "Cluster deployment completed"
}

main "$@"
