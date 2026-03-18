# Monitoring Server Setup

Foxya monitoring is exposed with two public entrypoints:

- Grafana: `https://dev.korion.io.kr/`
- Prometheus: `https://api.korion.io.kr/`

Legacy compatibility redirects are kept for older bookmarks:

- `https://korion.io.kr/6s9ex74204/grafana/*` -> `https://dev.korion.io.kr/`
- `https://api.korion.io.kr/prometheus/*` -> `https://api.korion.io.kr/*`
- `https://api.korion.io.kr/prometheus/query` -> `https://api.korion.io.kr/graph`

## Nginx Routing

- `server_name dev.korion.io.kr`
  - `location /` -> `http://grafana:3000`
- `server_name api.korion.io.kr`
  - `location /` -> `http://prometheus:9090`
  - `location /prometheus*` -> 302 redirect to the equivalent root path

## Quick Checks

```bash
curl -I https://dev.korion.io.kr/
curl -I https://api.korion.io.kr/
curl -I https://api.korion.io.kr/prometheus/query
```

Expected:

- Grafana root returns `200` or a login redirect on `dev.korion.io.kr`
- Prometheus root returns `200` on `api.korion.io.kr`
- `/prometheus/query` returns `302` to `https://api.korion.io.kr/graph`
- canonical Prometheus API queries should use `/api/v1/query`, not `/query`

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
