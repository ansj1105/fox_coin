# 코인 API 컨테이너 2개 구성 및 배포법

## 1. 컨테이너 2개 구조 (foxya-api처럼)

코인 API는 **동일 이미지를 쓰는 컨테이너 2개**로 띄우고, Nginx가 두 개 모두로 트래픽을 나눠 보냅니다.

| 구분 | 컨테이너 이름 | 서비스명 | 포트 노출 | 비고 |
|------|----------------|----------|-----------|------|
| API 1 | `foxya-api` | `app` | 8080 (호스트) | Nginx upstream 대상 |
| API 2 | `foxya-api-2` | `app2` | 없음 (내부만) | Nginx upstream 대상 |

- **이미지**: 둘 다 `foxya-coin-api:${APP_VERSION:-latest}` (같은 빌드 결과 사용)
- **Nginx**: `nginx/nginx.conf`의 `upstream foxya_api`에 `foxya-api:8080`, `foxya-api-2:8080` 등록 → 라운드로빈
- **효과**: 한 대만 재시작해도 다른 한 대가 트래픽 처리 → **무중단 배포** 가능

---

## 2. 배포 방법

### 사전 준비

- 서버에 Docker, Docker Compose 설치
- 프로젝트 경로: `/var/www/fox_coin` (또는 `DEPLOY_DIR`에 맞게)
- `.env` 파일 준비 (`.env.example` 참고)
- `coin-shared` 네트워크 생성:  
  `docker network create coin-shared`

### 첫 배포 (전체 서비스 기동)

```bash
cd /var/www/fox_coin

# 1) 환경 설정
cp .env.example .env
# .env 수정 (DB, JWT, ENCRYPTION_KEY, GOOGLE_*, TRON_SERVICE_PATH 등)

# 2) 디렉토리 생성
mkdir -p logs nginx/ssl nginx/conf.d nginx/logs backups
# Nginx 설정 복사/수정 (nginx/nginx.conf, nginx/conf.d/)

# 3) 배포 스크립트로 한 번에 배포
./scripts/deploy.sh deploy
```

또는 직접:

```bash
cd /var/www/fox_coin
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d
```

- `app`만 빌드하면 `app`/`app2` 둘 다 같은 이미지(`foxya-coin-api:latest`)를 사용합니다.
- `up -d` 시 postgres, redis, app, app2, tron-service, nginx 등 전체 기동.

### 업데이트 배포 (무중단, 한 대씩 재시작)

코드/이미지 갱신 후 **app → app2 순서**로 한 대씩만 재시작하면 됩니다.

```bash
cd /var/www/fox_coin
git pull origin develop   # 또는 사용 중인 브랜치

# 1) 이미지 한 번 빌드 (app, app2 공용)
docker compose -f docker-compose.prod.yml build app

# 2) app( foxya-api ) 먼저 교체
docker compose -f docker-compose.prod.yml up -d --no-deps app
sleep 15
curl -sf http://localhost:8080/health && echo " app OK" || echo " app fail"

# 3) app2( foxya-api-2 ) 교체
docker compose -f docker-compose.prod.yml up -d --no-deps app2
sleep 10
curl -sf http://localhost/health && echo " nginx->api OK" || echo " check nginx"
```

배포 스크립트 사용 시:

```bash
./scripts/deploy.sh update
```

(스크립트가 위와 같은 순서로 app → app2 무중단 업데이트를 수행하도록 되어 있음)

### 롤백

이전에 백업해 둔 이미지 태그로 되돌립니다.

```bash
cd /var/www/fox_coin
./scripts/deploy.sh rollback
# 프롬프트에 나온 backup_YYYYMMDD_HHMMSS 중 하나 입력
```

또는 직접:

```bash
docker tag foxya-coin-api:backup_20250101120000 foxya-coin-api:latest
docker compose -f docker-compose.prod.yml up -d --no-deps app
docker compose -f docker-compose.prod.yml up -d --no-deps app2
```

---

## 3. 관리 명령어 요약

| 목적 | 명령어 |
|------|--------|
| 상태 확인 | `docker compose -f docker-compose.prod.yml ps` |
| API 1 로그 | `docker compose -f docker-compose.prod.yml logs -f app` |
| API 2 로그 | `docker compose -f docker-compose.prod.yml logs -f app2` |
| 전체 로그 | `docker compose -f docker-compose.prod.yml logs -f` |
| API만 재시작 (1대) | `docker compose -f docker-compose.prod.yml restart app` |
| API 둘 다 재시작 | `docker compose -f docker-compose.prod.yml restart app app2` |
| 전체 중지 | `docker compose -f docker-compose.prod.yml down` |
| 전체 기동 | `docker compose -f docker-compose.prod.yml up -d` |
| DB 백업 | `./scripts/deploy.sh backup-db` |

---

## 4. 지금 배포할 때 체크리스트

1. **서버**: Docker/Docker Compose 설치, `coin-shared` 네트워크 존재
2. **경로**: `/var/www/fox_coin` (또는 사용하는 `DEPLOY_DIR`)
3. **.env**: DB, JWT, ENCRYPTION_KEY, GOOGLE_*, TRON_SERVICE_PATH(coin_publish 경로) 등 설정
4. **Nginx**: `nginx/nginx.conf`, `nginx/conf.d/` 설정 및 SSL(필요 시) 준비
5. **첫 배포**: `./scripts/deploy.sh deploy`
6. **이후 배포**: `git pull` 후 `./scripts/deploy.sh update` 또는 위의 무중단 순서(app → app2)대로 실행

자세한 서버 초기 설정·SSL·트러블슈팅은 [DEPLOYMENT.md](./DEPLOYMENT.md)를 참고하면 됩니다.
