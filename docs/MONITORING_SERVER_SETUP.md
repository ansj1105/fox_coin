# Monitoring Server Setup

Foxya monitoring is exposed with two public entrypoints:

- Grafana: `https://dev.korion.io.kr/`
- Prometheus: `https://api.korion.io.kr/prometheus/`

Legacy compatibility redirects are kept for older bookmarks:

- `https://korion.io.kr/6s9ex74204/grafana/*` -> `https://dev.korion.io.kr/`
- `https://korion.io.kr/6s9ex74204/prometheus/*` -> `https://api.korion.io.kr/prometheus/`

Host nginx is the source of truth for public monitoring exposure on `52.200.97.155`.
`https://api.korion.io.kr/` root remains the Foxya API surface, not Prometheus.

## Nginx Routing

- `server_name dev.korion.io.kr`
  - `location /` -> `http://grafana:3000`
- host nginx `server_name api.korion.io.kr`
  - `location /` -> `http://127.0.0.1:8080` (Foxya API)
  - `location /prometheus/` -> `http://127.0.0.1:9090`
  - `location = /prometheus` -> `302 /prometheus/`

## Quick Checks

```bash
curl -I https://dev.korion.io.kr/
curl -I https://api.korion.io.kr/
curl -I https://api.korion.io.kr/prometheus/
curl -I https://api.korion.io.kr/prometheus/graph
```

Expected:

- Grafana root returns `200` or a login redirect on `dev.korion.io.kr`
- Foxya API root on `api.korion.io.kr` may return app responses such as `401`
- Prometheus is reached via `api.korion.io.kr/prometheus/`
- `/prometheus/graph` may still redirect without preserving the prefix depending on Prometheus redirect behavior
- canonical Prometheus API queries should use `/prometheus/api/v1/query`

## Docker Checks

```bash
docker compose -f docker-compose.prod.yml ps grafana prometheus nginx
docker exec foxya-nginx wget -q -O- --timeout=2 http://grafana:3000/api/health
docker exec foxya-nginx wget -q -O- --timeout=2 http://prometheus:9090/-/healthy
```

If public access fails, check in this order:

1. Containers are running
2. Nginx can reach `grafana:3000` and `prometheus:9090`
3. Public hostnames still match the intended routing
4. Legacy redirect paths point to the correct canonical route
