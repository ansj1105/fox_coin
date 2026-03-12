#!/bin/bash
set -euo pipefail

RED='[0;31m'
GREEN='[0;32m'
YELLOW='[1;33m'
BLUE='[0;34m'
NC='[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

DEPLOY_DIR="${DEPLOY_DIR:-/var/www/fox_coin}"
TRON_SERVICE_PATH="${TRON_SERVICE_PATH:-/var/www/coin_publish}"
TRON_SERVICE_BRANCH="${TRON_SERVICE_BRANCH:-develop}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
TRON_SERVICES="${TRON_SERVICES:-tron-service}"
DEFAULT_ISSUER="korion"

require_file() {
    local path="$1"
    local label="$2"
    if [ ! -f "$path" ]; then
        log_error "$label not found: $path"
        exit 1
    fi
}

can_run_docker_without_sudo() {
    docker info >/dev/null 2>&1
}

compose_cmd() {
    if can_run_docker_without_sudo; then
        docker compose -f "$COMPOSE_FILE" "$@"
    else
        sudo docker compose -f "$COMPOSE_FILE" "$@"
    fi
}

git_sync() {
    local git_prefix=(git -C "$TRON_SERVICE_PATH")
    if [ ! -w "$TRON_SERVICE_PATH/.git" ]; then
        git_prefix=(sudo git -C "$TRON_SERVICE_PATH")
    fi
    "${git_prefix[@]}" fetch origin
    "${git_prefix[@]}" checkout "$TRON_SERVICE_BRANCH"
    "${git_prefix[@]}" pull --rebase origin "$TRON_SERVICE_BRANCH"
}

sync_env_key() {
    local key="$1"
    local value="${!key:-}"
    if [ -z "$value" ]; then
        return 0
    fi

    local env_file="$DEPLOY_DIR/.env"
    python3 - "$env_file" "$key" "$value" <<'PY2'
from pathlib import Path
import sys
path = Path(sys.argv[1])
key = sys.argv[2]
value = sys.argv[3]
text = path.read_text()
lines = text.splitlines()
out = []
seen = False
for line in lines:
    if '=' in line and not line.lstrip().startswith('#'):
        current = line.split('=', 1)[0]
        if current == key:
            out.append(f"{key}={value}")
            seen = True
            continue
    out.append(line)
if not seen:
    out.append(f"{key}={value}")
path.write_text('\n'.join(out) + '\n')
PY2
}

read_env_value() {
    local key="$1"
    local env_file="$DEPLOY_DIR/.env"
    python3 - "$env_file" "$key" <<'PY2'
from pathlib import Path
import sys
path = Path(sys.argv[1])
key = sys.argv[2]
for line in path.read_text().splitlines():
    stripped = line.strip()
    if not stripped or stripped.startswith('#') or '=' not in stripped:
        continue
    current, value = stripped.split('=', 1)
    if current == key:
        print(value)
        break
PY2
}

ensure_runtime_env() {
    local env_file="$DEPLOY_DIR/.env"
    sync_env_key LEDGER_EXPECTED_ISSUER
    sync_env_key LEDGER_SHARED_HMAC_SECRET

    local issuer
    issuer="$(read_env_value LEDGER_EXPECTED_ISSUER)"
    local secret
    secret="$(read_env_value LEDGER_SHARED_HMAC_SECRET)"

    if [ -z "$issuer" ]; then
        log_warning "LEDGER_EXPECTED_ISSUER is missing in .env. Using default issuer: $DEFAULT_ISSUER"
        LEDGER_EXPECTED_ISSUER="$DEFAULT_ISSUER"
        sync_env_key LEDGER_EXPECTED_ISSUER
        issuer="$DEFAULT_ISSUER"
    fi

    if [ -z "$secret" ]; then
        log_error "LEDGER_SHARED_HMAC_SECRET is missing. Export it in the shell or add it to $env_file before deploy."
        exit 1
    fi

    log_info "Ledger contract issuer: $issuer"
    log_info "Ledger shared secret configured in .env"
}

health_check() {
    local service="$1"
    compose_cmd exec -T "$service" node -e 'require("http").get("http://localhost:3000/health", (r) => process.exit(r.statusCode === 200 ? 0 : 1)).on("error", () => process.exit(1))'
}

main() {
    require_file "$DEPLOY_DIR/.env" 'env file'
    require_file "$DEPLOY_DIR/$COMPOSE_FILE" 'compose file'
    require_file "$TRON_SERVICE_PATH/package.json" 'coin_publish package.json'

    cd "$DEPLOY_DIR"
    ensure_runtime_env

    log_info "Syncing coin_publish source from $TRON_SERVICE_BRANCH"
    git_sync

    local backup_tag
    backup_tag="$(date +%Y%m%d_%H%M%S)"
    if compose_cmd images >/dev/null 2>&1; then
        docker image inspect foxya-tron-service:latest >/dev/null 2>&1 && docker tag foxya-tron-service:latest "foxya-tron-service:backup_${backup_tag}" || true
    fi

    log_info "Rebuilding TRON services: $TRON_SERVICES"
    compose_cmd up -d --build $TRON_SERVICES

    for service in $TRON_SERVICES; do
        log_info "Waiting for $service health check"
        sleep 15
        if health_check "$service" >/dev/null 2>&1; then
            log_success "$service is healthy"
        else
            log_warning "$service health check failed. Check logs with: docker compose -f $COMPOSE_FILE logs --tail=100 $service"
        fi
    done

    compose_cmd ps $TRON_SERVICES
    log_success "TRON service deploy finished"
}

main "$@"
