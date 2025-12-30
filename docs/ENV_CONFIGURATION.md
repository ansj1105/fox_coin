# 환경 변수 설정 가이드

## 📋 개요

Foxya Coin Service와 TRON 서비스에서 사용하는 모든 환경 변수를 정리한 문서입니다.

## 🔧 설정 파일 위치

### Java 서비스 (foxya_coin_service)
- **설정 파일**: `src/main/resources/config.json`
- **환경 변수**: `.env` 파일 (Docker Compose에서 사용)

### TRON 서비스 (coin_publish)
- **환경 변수**: `.env` 파일 (Docker Compose의 `env_file`로 자동 로드)

## 📝 환경 변수 목록

### Application
```bash
APP_ENV=prod                    # local, prod
APP_VERSION=1.0.0
```

### Database
```bash
DB_HOST=postgres
DB_PORT=5432
DB_NAME=coin_system_cloud
DB_USER=foxya
DB_PASSWORD=your_password_here
DB_POOL_SIZE=20
```

### JWT
```bash
JWT_SECRET=your-secure-jwt-secret-key-here-change-in-production
JWT_ACCESS_EXPIRE=30           # 분
JWT_REFRESH_EXPIRE=14400       # 분 (10일)
```

### Redis
```bash
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
```

### SMTP (AWS SES)
```bash
SMTP_HOST=email-smtp.us-east-1.amazonaws.com
SMTP_PORT=587
SMTP_USERNAME=your_ses_username
SMTP_PASSWORD=your_ses_password
SMTP_FROM=no-reply@foxya.com
```

### Blockchain - TRON
```bash
TRON_FULL_NODE=https://api.trongrid.io
TRON_SOLIDITY_NODE=https://api.trongrid.io
TRON_EVENT_SERVER=https://api.trongrid.io
TRON_API_KEY=your_trongrid_api_key
TRON_SERVICE_URL=http://tron-service:3000
```

### Blockchain - BTC
```bash
# BTC_NETWORK=mainnet
BTC_NETWORK=testnet
```

### Blockchain - ETH
```bash
# ETH_NETWORK=mainnet
ETH_NETWORK=sepolia
# ETH_NETWORK=goerli
ETH_RPC_URL=https://mainnet.infura.io/v3/your_infura_project_id
ETHERSCAN_API_KEY=your_etherscan_api_key
```

### TRON Service Path
```bash
TRON_SERVICE_PATH=/var/www/coin_publish
```

### Monitoring API Key
```bash
MONITORING_API_KEY=your-secret-monitoring-key-here
```

### JVM Options
```bash
JAVA_OPTS=-Xmx1024m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

## ✅ 설정 확인 체크리스트

### Java 서비스 (config.json)
- [x] Database 설정
- [x] JWT 설정
- [x] Redis 설정
- [x] SMTP 설정
- [x] TRON 설정
- [x] BTC 설정 (추가됨)
- [x] ETH 설정 (추가됨)
- [x] Monitoring API Key 설정 (추가됨)

### TRON 서비스 (.env)
- [ ] Database 설정 (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD)
- [ ] Redis 설정 (REDIS_HOST, REDIS_PORT, REDIS_PASSWORD)
- [ ] TRON 설정 (TRON_FULL_NODE, TRON_API_KEY 등)
- [ ] BTC 설정 (BTC_NETWORK)
- [ ] ETH 설정 (ETH_NETWORK, ETH_RPC_URL, ETHERSCAN_API_KEY)

## 🔐 보안 주의사항

1. **프로덕션 환경**
   - `JWT_SECRET` 반드시 변경
   - `DB_PASSWORD` 강력한 비밀번호 사용
   - API 키들은 환경 변수로 관리

2. **네트워크 선택**
   - 개발/테스트: `testnet`, `sepolia` 사용
   - 프로덕션: `mainnet` 사용

3. **민감 정보**
   - `.env` 파일은 `.gitignore`에 포함
   - Docker Compose의 `env_file` 사용 시 주의

## 📦 Docker Compose 설정

`docker-compose.prod.yml`에서 TRON 서비스는 자동으로 `.env` 파일을 로드합니다:

```yaml
tron-service:
  env_file:
    - .env  # 모든 환경 변수 자동 로드
  environment:
    - DB_HOST=postgres      # Docker 네트워크용 오버라이드
    - REDIS_HOST=redis      # Docker 네트워크용 오버라이드
```

## 🚀 배포 시 설정

1. `.env` 파일 생성:
```bash
cp .env.example .env
nano .env  # 실제 값으로 수정
```

2. Docker Compose로 배포:
```bash
docker-compose -f docker-compose.prod.yml up -d --build
```

3. 환경 변수 확인:
```bash
docker exec foxya-tron-service env | grep -E "BTC|ETH|TRON"
```

