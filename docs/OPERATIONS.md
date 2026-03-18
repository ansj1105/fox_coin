# 운영·모니터링·장애 대응·성능 검증

Foxya Coin Service(KORION)의 일상 운영, 모니터링, 장애 대응, 성능 검증 절차를 정리한 문서입니다.

---

## 목차

1. [일상 운영](#1-일상-운영)
2. [모니터링](#2-모니터링)
3. [장애 대응](#3-장애-대응)
4. [성능 검증](#4-성능-검증)
5. [Prometheus / Grafana 연동 및 안 열릴 때](#5-prometheus--grafana-연동-및-안-열릴-때)

---

## 1. 일상 운영

### 1.1 일일 점검 (체크리스트)

| 항목 | 명령어 / 방법 | 정상 기준 |
|------|----------------|-----------|
| 컨테이너 상태 | `docker compose -f docker-compose.prod.yml ps` | app, nginx, db-proxy, redis 모두 `Up` |
| 헬스체크 | `curl -s -o /dev/null -w "%{http_code}" https://your-domain/health` | `200` |
| API 응답 | `curl -s https://your-domain/api/v1/health` (또는 공개 엔드포인트) | JSON 정상 반환 |
| 디스크 여유 | `df -h` | `/` 20% 이상 여유 권장 |
| 메모리 | `free -h` 또는 `docker stats --no-stream` | OOM 없이 여유 유지 |

### 1.2 주간/정기 작업

- **DB 백업**: `./scripts/deploy.sh backup-db` (또는 cron으로 주 1회 이상)
- **로그 로테이션**: Docker json-file 드라이버 설정(`max-size`, `max-file`) 확인
- **미사용 리소스 정리**: `docker system prune -f` (이미지/컨테이너만, 볼륨은 신중히)
- **인증서 만료**: Let's Encrypt 사용 시 만료 30일 전 갱신 (cron 권장)

### 1.3 배포 절차

```bash
# 1. 코드 반영
cd /var/www/fox_coin  # 실제 배포 경로
git pull origin develop

# 2. 앱만 재빌드·재시작 (DB 마이그레이션 필요 시 별도 실행)
docker compose -f docker-compose.prod.yml up -d --build app

# 3. 헬스체크
curl -s -o /dev/null -w "%{http_code}" http://localhost/health
```

- 전체 재시작이 필요할 때: `docker compose -f docker-compose.prod.yml up -d`
- 롤백: 이전 이미지 태그로 `docker compose`에서 `image` 지정 후 `up -d`

### 1.4 로그 확인

| 목적 | 명령어 |
|------|--------|
| 앱 실시간 | `docker compose -f docker-compose.prod.yml logs -f app` |
| 앱 최근 N줄 | `docker logs foxya-api --tail 200` |
| 에러만 | `docker logs foxya-api 2>&1 \| grep -i error` |
| Nginx 접근/에러 | `docker exec foxya-nginx tail -f /var/log/nginx/foxya_access.log` / `foxya_error.log` |
| DB 프록시 | `docker logs foxya-db-proxy --tail 100` |

자세한 명령어는 [LOG_CHECK.md](./LOG_CHECK.md) 참고.

---

## 2. 모니터링

### 2.1 현재 구성

- **메트릭 수집**: 앱의 `/metrics` (Prometheus 포맷) → Prometheus가 15초마다 스크랩
- **저장**: Prometheus (기본 30일 보존)
- **시각화**: Grafana (Prometheus를 데이터 소스로 사용)
- **접근 경로**: Grafana **`https://dev.korion.io.kr/`**, Prometheus **`https://api.korion.io.kr/`**
  - 호환 리다이렉트: `https://api.korion.io.kr/prometheus/*` → `https://api.korion.io.kr/*`

Prometheus/Grafana가 **아직 붙어 있지 않거나** 안 열리는 경우는 [5장](#5-prometheus--grafana-연동-및-안-열릴-때) 참고. **서버 기준 구성법**은 [MONITORING_SERVER_SETUP.md](./MONITORING_SERVER_SETUP.md) 참고.

### 2.2 메트릭 엔드포인트 (앱)

- **경로**: `GET /metrics`
- **인증**: 없음 (필요 시 Nginx에서 IP 제한 권장)
- **내용 예**: `http_requests_total`, `http_request_duration_seconds`, `http_errors_total` 등 (Micrometer Prometheus)

```bash
# 앱 컨테이너 기준
curl -s http://localhost:8080/metrics

# Nginx 경유 (도메인 사용 시)
curl -s https://your-domain/metrics
```

### 2.3 모니터링 시 확인할 지표

| 지표 | 의미 | 참고 |
|------|------|------|
| HTTP 요청 수 | 트래픽 양 | `http_requests_total` 또는 rate |
| 응답 시간 | 지연 구간 파악 | `http_request_duration_seconds` (평균/백분위) |
| 에러율 | 4xx/5xx 비율 | `http_errors_total` / `http_requests_total` |
| DB/Redis | 연결 실패, 지연 | 로그 및 헬스체크 |
| 컨테이너 리소스 | CPU/메모리 | `docker stats` |

설정·쿼리 예시는 [MONITORING_SETUP.md](./MONITORING_SETUP.md) 참고.

---

## 3. 장애 대응

### 3.1 장애 단계별 대응

| 단계 | 내용 |
|------|------|
| **감지** | 헬스체크 실패, 에러율 급증, 로그 에러, 사용자 문의 |
| **확인** | `docker ps`, `curl /health`, `docker logs`, Nginx/앱 에러 로그 |
| **격리** | 필요 시 트래픽 차단( Nginx maintenance 페이지 등) |
| **복구** | 재시작, 롤백, 설정/DB 복구 |
| **정리** | 원인 기록, 재발 방지(설정/코드/운영 절차) |

### 3.2 유형별 대응

#### 앱 무응답 / 502

```bash
# 컨테이너 상태
docker compose -f docker-compose.prod.yml ps
docker logs foxya-api --tail 100

# 앱만 재시작
docker compose -f docker-compose.prod.yml restart app

# 여전히 실패 시 재빌드
docker compose -f docker-compose.prod.yml up -d --build app
```

#### DB 연결 실패

```bash
docker logs foxya-db-proxy --tail 50
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:15-alpine \
  psql -h "$DB_ADMIN_HOST" -p "$DB_ADMIN_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT pg_is_in_recovery(), now()"
# 풀/연결 수 확인 후 app 재시작
```

#### Redis 장애

- 토큰 블랙리스트·재시도 큐 등 Redis 의존 기능만 영향. 앱은 기동 가능.
- `docker logs foxya-redis`, 필요 시 `restart redis`

#### 디스크 부족

```bash
df -h
docker system df
docker system prune -f   # 미사용 이미지/컨테이너 (볼륨 제외)
# 로그/백업 정리 후 필요 시 볼륨 정리
```

#### 메모리 부족 (OOM)

- `docker stats`로 컨테이너별 사용량 확인
- `.env`의 `JAVA_OPTS` (예: `-Xmx1024m`) 조정 후 app 재시작
- Prometheus/Grafana 메모리 제한은 `docker-compose.prod.yml`의 `deploy.resources.limits.memory` 참고

### 3.3 롤백 절차

1. 이전에 동작하던 커밋/이미지 확인
2. `git checkout <commit>` 또는 이미지 태그 변경
3. `docker compose -f docker-compose.prod.yml up -d --build app` (또는 해당 서비스만)
4. `/health` 및 핵심 API로 동작 확인
5. DB 마이그레이션 롤백이 필요하면 별도 스크립트/백업 복원 수행

### 3.4 에스컬레이션

- **기록**: 발생 시각, 증상, 수행한 명령/조치, 로그 발췌
- **공유**: 팀 채널/이슈 트래커에 요약 등록
- **재발 방지**: 설정 변경, 코드 수정, 알람/체크리스트 보강

---

## 4. 성능 검증

### 4.1 목표 (참고용)

| 항목 | 참고 기준 | 비고 |
|------|-----------|------|
| 헬스체크 | 200 OK, 1초 이내 | 정상 부하 시 |
| 일반 API | p95 1초 이내 | 인증/DB 호출 구간 포함 |
| 동시 사용자 | 설계치 기준으로 점진적 증가 | 부하 테스트로 확인 |

실제 목표치는 트래픽과 인프라에 맞게 조정하세요.

### 4.2 부하 테스트 (예시)

- **도구**: k6, Apache Bench(ab), hey 등
- **대상**: `GET /health`, `GET /api/v1/...` (인증 필요 시 헤더 포함)

```bash
# ab 예시 (동시 10, 총 1000 요청)
ab -n 1000 -c 10 https://your-domain/health

# 응답 시간·RPS 확인 후, /metrics 또는 Grafana에서 지표 확인
```

### 4.3 성능 검증 시 확인 사항

1. **앱 메트릭**: `/metrics`의 `http_request_duration_seconds`, `http_errors_total`
2. **리소스**: `docker stats`로 app/DB/Redis CPU·메모리
3. **DB**: 느린 쿼리 로그, connection pool 설정
4. **Nginx**: `limit_req`/`limit_conn` 초과 여부, 502/504 발생 여부

부하 테스트 전후로 위 항목을 기록해 두면 용량 산정과 튜닝에 도움이 됩니다.

---

## 5. Prometheus / Grafana 연동 및 안 열릴 때

현재 Prometheus·Grafana는 **선택 구성**입니다.  
아직 띄우지 않았거나, “열어봤는데 안 열린다”면 아래 순서로 확인하면 됩니다.  
우리 서비스는 **Nginx → Grafana/Prometheus** 또는 **Nginx → API → Prometheus** 구조로, 도메인·subpath 설정이 맞아야 합니다.

### 5.1 우리 서비스에서의 연동 구조

- **Grafana (권장 접속)**: **`https://dev.korion.io.kr/`** — Nginx `server_name dev.korion.io.kr` → `proxy_pass http://grafana:3000` (루트 제공, subpath 없음)
- **Grafana (예전 경로)**: `https://korion.io.kr/6s9ex74204/grafana/*` → 302 리다이렉트 → `https://dev.korion.io.kr/`
- **Prometheus**: **`https://api.korion.io.kr/`** — Nginx `server_name api.korion.io.kr` → `proxy_pass http://prometheus:9090` (전용 도메인, 리다이렉트 루프 방지)
- **Prometheus 호환 경로**: `https://api.korion.io.kr/prometheus/*` → `https://api.korion.io.kr/*` 로 302 리다이렉트
- **Prometheus query 호환 경로**: `https://api.korion.io.kr/prometheus/query` → `https://api.korion.io.kr/graph` 로 302 리다이렉트
- **메트릭**: Nginx `location /metrics` → `proxy_pass http://foxya_api/metrics`
- **Grafana 환경 변수 (dev.korion.io.kr 사용 시)**: `GF_SERVER_SERVE_FROM_SUB_PATH=false`, `GF_SERVER_ROOT_URL=https://dev.korion.io.kr/`, `GF_SERVER_DOMAIN=dev.korion.io.kr`

**Prometheus**: `api.korion.io.kr` 사용 (기존 DNS·인증서 그대로 사용).

즉, “우리 서비스에 안 맞다”기보다는 **컨테이너 기동·네트워크·도메인/ROOT_URL 설정** 중 하나가 맞지 않을 가능성이 큽니다.

### 5.2 Grafana / Prometheus가 안 열릴 때 체크리스트

#### 1) 컨테이너 기동 여부

```bash
docker compose -f docker-compose.prod.yml ps
# prometheus, grafana 항목이 있는지 확인

# 없으면 올리기
docker compose -f docker-compose.prod.yml up -d prometheus grafana
```

- `docker-compose.prod.yml`에 `prometheus`, `grafana` 서비스가 없으면 추가해야 합니다. (기존 프로젝트에는 정의되어 있음)

#### 2) 네트워크·내부 연결

```bash
# Nginx에서 Grafana로 연결 가능한지 (같은 compose 네트워크)
docker exec foxya-nginx wget -q -O- --timeout=2 http://grafana:3000/api/health || echo "FAIL"

# 앱에서 Prometheus로 (Prometheus 경로 접근 시)
docker exec foxya-api wget -q -O- --timeout=2 http://prometheus:9090/-/healthy || echo "FAIL"
```

- 실패 시: `docker network inspect fox_coin_foxya-network` 등으로 서비스가 같은 네트워크에 있는지 확인.

#### 3) 도메인·ROOT_URL (Grafana가 안 열리는 경우에 자주 원인)

- **권장 접속**: Grafana는 **`https://dev.korion.io.kr/`** 에서 사용합니다 (subpath 없음, 리다이렉트 이슈 없음).
- `docker-compose.prod.yml`의 Grafana 환경 변수 (dev.korion.io.kr 사용 시):
  - `GF_SERVER_SERVE_FROM_SUB_PATH=false`
  - `GF_SERVER_ROOT_URL=https://dev.korion.io.kr/`
  - `GF_SERVER_DOMAIN=dev.korion.io.kr`  
  (위와 다르게 설정하면 리다이렉트 무한 루프나 빈 화면이 발생할 수 있음)
- 도메인/ROOT_URL을 바꿨다면 Grafana 재시작:
  ```bash
  docker compose -f docker-compose.prod.yml up -d grafana
  ```

**ERR_TOO_MANY_REDIRECTS (korion.io.kr 리다이렉트 횟수 너무 많음):**  
클라이언트가 `/6s9ex74204/grafana/`로 요청하면 Nginx가 Grafana에 `GET /`로 전달하고, Grafana가 루트(`/`)를 `GF_SERVER_ROOT_URL`(같은 URL)로 301 리다이렉트해서 **무한 루프**가 납니다.  
이미 **Nginx에서 루트 요청만 Grafana `/login`으로 넘기도록** 수정해 두었습니다. `nginx/conf.d/default.conf`에 다음이 있어야 합니다:

- `rewrite ^/6s9ex74204/grafana/?$ /login break;`  ← 루트일 때만 `/login`으로 전달
- `rewrite ^/6s9ex74204/grafana/(.*)$ /$1 break;`   ← 나머지는 prefix 제거 후 전달

→ `/6s9ex74204/grafana/` 접속 시 Grafana는 `/login`을 받아 로그인 페이지(또는 로그인 후 대시보드)를 내려주고, 같은 URL로 301을 보내지 않아 루프가 사라집니다.

**ERR_TOO_MANY_REDIRECTS on `/6s9ex74204/grafana/login` 또는 `.../grafana/login/`:**  
Grafana는 **`/login/`(끝 슬래시 있음) 요청을 `/login`(끝 슬래시 없음)으로 301** 보냅니다. Nginx에서 그 Location을 `.../grafana/login/`으로 바꿔주면 클라이언트가 다시 `/login/` 요청 → Grafana가 또 `/login`으로 301 → **루프**가 납니다.  
그래서 **항상 `/login`(끝 슬래시 없음)** 으로 통일했습니다.  
- **`/6s9ex74204/grafana/login/`**(슬래시 있음) 요청 → Nginx가 **301** 한 번만 **`/6s9ex74204/grafana/login`** 으로 리다이렉트  
- Grafana가 **Location: .../grafana/login/** 으로 보내면 Nginx **proxy_redirect**로 **.../grafana/login** 으로 덮어쓰기  
- **`.../grafana/login`** 요청 시 Grafana에 **GET /login** 전달 → 200 응답  
Grafana 접속 확인: `curl -sI -o /dev/null -w "%{http_code}\n" 'https://dev.korion.io.kr/login'` → **200**이면 정상.

**다른 도메인/IP로 접속 시 리다이렉트·빈 화면:**  
Grafana가 보내는 `Location`을 Nginx에서 요청 호스트로 덮어쓰려면 `proxy_redirect https://korion.io.kr/ $scheme://$host/;` (및 http 동일)가 있어야 합니다.  
`GF_SERVER_ROOT_URL`에 다른 도메인을 쓰면, 그 도메인에 대한 `proxy_redirect` 한 줄을 같은 형식으로 추가하면 됩니다.

#### 4) Nginx 설정

- `nginx/conf.d/default.conf`에 다음이 있는지 확인:
  - `location /6s9ex74204/grafana/` → `proxy_pass http://grafana:3000;`
  - dev 블록: `location /prometheus/` → `proxy_pass http://prometheus:9090/`
- 수정 후:
  ```bash
  docker exec foxya-nginx nginx -t && docker compose -f docker-compose.prod.yml restart nginx
  ```

#### 5) 직접 포트로 접근 (동작 여부만 확인)

- Grafana: 서버에서 `curl -s -o /dev/null -w "%{http_code}" https://dev.korion.io.kr/` 또는 내부 `http://grafana:3000/api/health`
- Prometheus: `curl -s -o /dev/null -w "%{http_code}" http://localhost:9090/-/healthy`
- 200이면 컨테이너는 정상. 문제는 Nginx/프록시/ROOT_URL일 가능성이 큼.

#### 6) Grafana 접속 확인 (curl)

```bash
# Grafana (dev.korion.io.kr): 200이면 정상
curl -sI -o /dev/null -w "%{http_code}\n" 'https://dev.korion.io.kr/login'
# 예전 경로 → dev 리다이렉트 확인
curl -sI 'https://korion.io.kr/6s9ex74204/grafana/' 2>/dev/null | grep -i location
```

- 301/302가 같은 URL로 반복되면 Nginx `proxy_redirect` 또는 Grafana `GF_SERVER_ROOT_URL` 설정 재확인.

#### 7) 로그로 원인 좁히기

```bash
docker logs foxya-grafana --tail 50
docker logs foxya-nginx --tail 50
docker exec foxya-nginx tail -20 /var/log/nginx/foxya_error.log
```

- 502: Nginx → Grafana/Prometheus 또는 Nginx → API 연결 실패 (컨테이너 중지, 네트워크, 호스트명 오타)
- 404: location 경로 불일치 또는 subpath 미지원

### 5.3 Prometheus 수집 데이터가 없을 때 (타겟 DOWN / No data)

1. **타겟 상태 확인**  
   브라우저에서 **https://api.korion.io.kr/targets** 접속 → **foxya-api** 가 **UP** 인지 확인.  
   **DOWN** 이면 아래 순서로 점검.

2. **앱 컨테이너·네트워크**
   ```bash
   docker compose -f docker-compose.prod.yml ps app prometheus
   # app, prometheus 둘 다 Up 이어야 함

   # Prometheus 컨테이너에서 앱 /metrics 호출 (같은 네트워크여야 함)
   docker exec foxya-prometheus wget -q -O- --timeout=5 http://app:8080/metrics | head -20
   ```
   - 여기서 메트릭 텍스트가 나오면 앱은 정상. Prometheus만 재시작해 보기: `docker compose -f docker-compose.prod.yml up -d prometheus`
   - 연결 실패(타임아웃 등)면: `docker network inspect $(docker compose -f docker-compose.prod.yml ps -q prometheus | xargs docker inspect -f '{{range .NetworkSettings.Networks}}{{.NetworkID}}{{end}}')` 등으로 app·prometheus가 같은 네트워크에 있는지 확인.

3. **설정 파일 확인**
   - `prometheus/prometheus.yml` 에 **targets: ['app:8080']** 인지 확인 (compose 서비스명이 `app` 이어야 함).
   - 수정 후 반영: `docker compose -f docker-compose.prod.yml up -d prometheus`

4. **호스트에서 앱 /metrics**
   ```bash
   curl -s http://localhost:8080/metrics | head -20
   ```
   - `http_requests_total` 등이 보이면 앱은 노출 중. Prometheus가 **app:8080** 으로만 접근하므로, 같은 Docker 네트워크에서 **app** 이름으로 접근 가능해야 함.

5. **첫 스크랩 대기**  
   스크랩 주기가 15초이므로, 앱·Prometheus 기동 후 **15초~1분** 지난 뒤 다시 **Targets** / Grafana 쿼리 확인.

### 5.4 요약

- Prometheus·Grafana는 **이 서비스와 연동 가능**한 구성으로 되어 있습니다.
- “안 열린다”면  
  **컨테이너 기동 → Nginx location → 실제 접속 URL과 GF_SERVER_ROOT_URL 일치** 순으로 확인하면 됩니다.
- 상세 설정·쿼리 예시는 [MONITORING_SETUP.md](./MONITORING_SETUP.md), 접속 방법은 [MONITORING_ACCESS.md](./MONITORING_ACCESS.md)를 참고하세요.

---

## 관련 문서

- [DEPLOYMENT.md](./DEPLOYMENT.md) - 배포 및 서버 설정
- [MONITORING_SERVER_SETUP.md](./MONITORING_SERVER_SETUP.md) - **서버 기반 모니터링 페이지 구성법**
- [MONITORING_SETUP.md](./MONITORING_SETUP.md) - Prometheus/Grafana 설정
- [MONITORING_ACCESS.md](./MONITORING_ACCESS.md) - 모니터링 URL·접근 방법
- [LOG_CHECK.md](./LOG_CHECK.md) - 로그 확인 명령어
- [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) - 일반 트러블슈팅
- [DOCKER_COMMANDS.md](./DOCKER_COMMANDS.md) - Docker 명령어 정리
