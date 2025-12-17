# TRON 서비스 Docker 설정 가이드

## 📋 개요

외부 TRON.js 프로젝트를 Docker Compose에 통합하는 방법입니다.

## 🔧 설정 방법

### 1. TRON 서비스 프로젝트에 Dockerfile 추가

TRON 서비스 프로젝트 루트에 `Dockerfile`을 생성하세요:

```dockerfile
FROM node:18-alpine

WORKDIR /app

# 패키지 파일 복사
COPY package*.json ./

# 의존성 설치
RUN npm ci --only=production

# 소스 코드 복사
COPY . .

# 로그 디렉토리 생성
RUN mkdir -p /app/logs

# 포트 노출
EXPOSE 3000

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:3000/health || exit 1

# 애플리케이션 실행
CMD ["npm", "start"]
```

### 2. TRON 서비스 프로젝트 구조 예시

```
tron-service/
├── src/
│   ├── index.js
│   ├── routes/
│   │   └── wallet.js
│   └── services/
│       └── TronService.js
├── package.json
├── Dockerfile          # ← 추가 필요
└── .dockerignore       # ← 추가 권장
```

### 3. .dockerignore 파일 (선택사항)

TRON 서비스 프로젝트 루트에 `.dockerignore` 추가:

```
node_modules
npm-debug.log
.env
.git
.gitignore
README.md
logs
*.log
```

### 4. docker-compose.prod.yml 설정

`docker-compose.prod.yml`에서 TRON 서비스 경로를 지정:

**방법 1: 환경변수로 경로 지정 (권장)**
```bash
# .env 파일 또는 환경변수
export TRON_SERVICE_PATH=/path/to/tron-service
docker-compose -f docker-compose.prod.yml up -d
```

**방법 2: docker-compose.prod.yml 직접 수정**
```yaml
tron-service:
  build:
    context: /path/to/tron-service  # 실제 경로로 변경
    dockerfile: Dockerfile
```

### 5. TRON 서비스 API 엔드포인트

TRON 서비스는 다음 엔드포인트를 제공해야 합니다:

**POST /api/wallet/create**
```json
// Request
{
  "currencyCode": "KRO"
}

// Response
{
  "address": "TXYZabc123def456..."
}
```

**GET /health** (선택사항, 헬스체크용)
```json
{
  "status": "ok"
}
```

## 🚀 배포 방법

### 1. 환경변수 설정
```bash
export TRON_SERVICE_PATH=/path/to/tron-service
export TRON_API_KEY=your-tron-api-key  # 필요시
```

### 2. Docker Compose로 실행
```bash
cd /var/www/foxya_coin_service
docker-compose -f docker-compose.prod.yml up -d --build
```

### 3. 로그 확인
```bash
# TRON 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f tron-service

# 전체 로그
docker-compose -f docker-compose.prod.yml logs -f
```

## 🔍 트러블슈팅

### TRON 서비스가 시작되지 않는 경우
1. Dockerfile이 올바른지 확인
2. package.json에 `start` 스크립트가 있는지 확인
3. 포트 3000이 충돌하지 않는지 확인

### 네트워크 연결 문제
- `foxya-network`에 모든 서비스가 연결되어 있는지 확인
- 컨테이너 이름으로 통신: `http://tron-service:3000`

### 빌드 실패
- TRON_SERVICE_PATH 환경변수가 올바른지 확인
- Dockerfile이 TRON 서비스 프로젝트 루트에 있는지 확인

