#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-foxya-postgres-standby}"
LOCAL_BIND="${LOCAL_BIND:-127.0.0.1:15433}"
REMOTE_HOST="${REMOTE_HOST:-172.31.71.66}"
REMOTE_TARGET="${REMOTE_TARGET:-172.23.0.7:5432}"
SSH_USER="${SSH_USER:-ubuntu}"
SSH_KEY="${SSH_KEY:-/home/ubuntu/.ssh/pg_primary_tunnel}"
KNOWN_HOSTS="${KNOWN_HOSTS:-/home/ubuntu/.ssh/known_hosts}"
DOCKER_BIN="${DOCKER_BIN:-/usr/bin/docker}"
NSENTER_BIN="${NSENTER_BIN:-/usr/bin/nsenter}"
SSH_BIN="${SSH_BIN:-/usr/bin/ssh}"
POLL_INTERVAL="${POLL_INTERVAL:-2}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

current_pid() {
  "$DOCKER_BIN" inspect -f '{{if .State.Running}}{{.State.Pid}}{{end}}' "$CONTAINER_NAME" 2>/dev/null || true
}

wait_for_pid() {
  local pid=""
  while true; do
    pid="$(current_pid)"
    if [[ -n "$pid" && "$pid" != "0" ]]; then
      printf '%s\n' "$pid"
      return 0
    fi
    log "Waiting for container $CONTAINER_NAME to be running"
    sleep "$POLL_INTERVAL"
  done
}

start_tunnel() {
  local pid="$1"
  log "Starting tunnel in namespace of $CONTAINER_NAME (pid=$pid)"
  "$NSENTER_BIN" --target "$pid" --net \
    "$SSH_BIN" \
      -o ServerAliveInterval=30 \
      -o ServerAliveCountMax=3 \
      -o ExitOnForwardFailure=yes \
      -o StrictHostKeyChecking=yes \
      -o UserKnownHostsFile="$KNOWN_HOSTS" \
      -N \
      -L "${LOCAL_BIND}:${REMOTE_TARGET}" \
      -i "$SSH_KEY" \
      "${SSH_USER}@${REMOTE_HOST}"
}

while true; do
  pid="$(wait_for_pid)"
  start_tunnel "$pid" &
  tunnel_pid=$!

  while kill -0 "$tunnel_pid" 2>/dev/null; do
    latest_pid="$(current_pid)"
    if [[ -z "$latest_pid" || "$latest_pid" == "0" || "$latest_pid" != "$pid" ]]; then
      log "Container pid changed from $pid to ${latest_pid:-stopped}; restarting tunnel"
      kill "$tunnel_pid" 2>/dev/null || true
      wait "$tunnel_pid" || true
      break
    fi
    sleep "$POLL_INTERVAL"
  done

  wait "$tunnel_pid" || true
  sleep 1
done
