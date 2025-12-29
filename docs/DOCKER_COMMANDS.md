# 🐳 Docker 명령어 가이드

## 📋 Docker Compose 기본 명령어

### 1. 모든 서비스 중지 및 제거
```bash
cd /var/www/foxya_coin_service
docker-compose -f docker-compose.prod.yml down
```
z
### 2. 모든 서비스 중지 (컨테이너는 유지)
```bash
docker-compose -f docker-compose.prod.yml stop
```

### 3. 모든 서비스 시작 (이미지 재빌드 없이)
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### 4. 모든 서비스 재시작 (이미지 재빌드 포함)
```bash
# 방법 1: 전체 재빌드 및 시작
docker-compose -f docker-compose.prod.yml up -d --build

# 방법 2: 특정 서비스만 재빌드
docker-compose -f docker-compose.prod.yml up -d --build app
docker-compose -f docker-compose.prod.yml up -d --build tron-service
```

### 5. 완전히 내렸다가 다시 올리기 (권장)
```bash
# 1. 모든 서비스 중지 및 제거
docker-compose -f docker-compose.prod.yml down

# 2. 이미지 재빌드 및 시작
docker-compose -f docker-compose.prod.yml up -d --build

# 3. 로그 확인
docker-compose -f docker-compose.prod.yml logs -f
```

## 🔄 서비스별 재시작

### 특정 서비스만 재시작
```bash
# API 서버만 재시작
docker-compose -f docker-compose.prod.yml restart app

# TRON 서비스만 재시작
docker-compose -f docker-compose.prod.yml restart tron-service

# Nginx만 재시작
docker-compose -f docker-compose.prod.yml restart nginx
```

### 특정 서비스 재빌드 및 재시작
```bash
# API 서버 재빌드 및 재시작
docker-compose -f docker-compose.prod.yml up -d --build app

# TRON 서비스 재빌드 및 재시작
docker-compose -f docker-compose.prod.yml up -d --build tron-service
```

## 📊 상태 확인

### 컨테이너 상태 확인
```bash
docker-compose -f docker-compose.prod.yml ps
```

### 서비스 로그 확인
```bash
# 전체 로그
docker-compose -f docker-compose.prod.yml logs -f

# 특정 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f app
docker-compose -f docker-compose.prod.yml logs -f tron-service
docker-compose -f docker-compose.prod.yml logs -f nginx

# 최근 100줄만
docker-compose -f docker-compose.prod.yml logs --tail=100 app
```

## 🌐 Swagger 접근

### Swagger UI
- **URL**: `http://your-server-ip/api-docs`
- **또는**: `http://localhost/api-docs` (서버에서 직접 접근 시)

### OpenAPI Spec
- **URL**: `http://your-server-ip/openapi.yaml`
- **또는**: `http://localhost/openapi.yaml`

### Health Check
- **URL**: `http://your-server-ip/health`
- **또는**: `http://localhost/health`

## 🚀 빠른 재시작 스크립트

### 전체 재시작 (한 번에)
```bash
#!/bin/bash
cd /var/www/foxya_coin_service
echo "🛑 서비스 중지 중..."
docker-compose -f docker-compose.prod.yml down

echo "🔨 이미지 재빌드 중..."
docker-compose -f docker-compose.prod.yml build

echo "🚀 서비스 시작 중..."
docker-compose -f docker-compose.prod.yml up -d

echo "⏳ 헬스체크 대기 중..."
sleep 10

echo "✅ 서비스 상태 확인:"
docker-compose -f docker-compose.prod.yml ps

echo ""
echo "📖 Swagger 접속: http://your-server-ip/api-docs"
```

## 🔍 문제 해결

### 포트 충돌 확인
```bash
# 8080 포트 사용 확인
sudo lsof -i :8080

# 3000 포트 사용 확인
sudo lsof -i :3000

# 80 포트 사용 확인
sudo lsof -i :80
```

### 네트워크 확인
```bash
# Docker 네트워크 확인
docker network ls

# 네트워크 상세 정보
docker network inspect foxya-network
```

### 컨테이너 내부 접속
```bash
# API 서버 컨테이너 접속
docker exec -it foxya-api sh

# TRON 서비스 컨테이너 접속
docker exec -it foxya-tron-service sh

# PostgreSQL 접속
docker exec -it foxya-postgres psql -U foxya -d coin_system_cloud
```

### 볼륨 확인
```bash
# 볼륨 목록
docker volume ls

# 볼륨 상세 정보
docker volume inspect foxya_coin_service_postgres-data
```

## 🧹 Docker 빌드 캐시 오류 해결

### 빌드 캐시 손상 오류
```
target tron-service: failed to solve: failed to prepare extraction snapshot: parent snapshot does not exist
```

**해결 방법:**
```bash
# 1. 빌드 캐시 정리 (권장)
docker builder prune -a -f

# 2. 캐시 없이 재빌드
docker-compose -f docker-compose.prod.yml build --no-cache tron-service
docker-compose -f docker-compose.prod.yml up -d

# 3. 전체 시스템 정리 (주의: 모든 이미지 삭제)
docker system prune -a -f
```

### 리소스 사용량 확인
```bash
# Docker 리소스 사용량
docker system df

# 빌드 캐시만 정리 (이미지 유지)
docker builder prune -a -f
```

## 📝 배포 후 확인 사항

1. **컨테이너 상태**
   ```bash
   docker-compose -f docker-compose.prod.yml ps
   ```
   모든 서비스가 `Up` 상태인지 확인

2. **헬스체크**
   ```bash
   curl http://localhost/health
   ```

3. **Swagger 접속**
   ```bash
   curl http://localhost/api-docs
   ```

4. **API 테스트**
   ```bash
   curl http://localhost/api/v1/currencies
   ```

