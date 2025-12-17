# Docker 빌드 캐시 오류 해결 가이드

## ❌ 오류 메시지
```
target tron-service: failed to solve: failed to prepare extraction snapshot: parent snapshot does not exist: not found
```

## 🔍 원인
Docker 빌드 캐시가 손상되었거나 불일치 상태입니다.

## ✅ 해결 방법

### 방법 1: 빌드 캐시만 정리 (권장)
```bash
# 빌드 캐시만 정리 (이미지와 컨테이너는 유지)
docker builder prune -a -f

# 또는 특정 시간 이전 캐시만 정리
docker builder prune --filter "until=24h" -f
```

### 방법 2: --no-cache로 빌드
```bash
# 캐시 없이 강제 재빌드
docker-compose -f docker-compose.prod.yml build --no-cache tron-service
docker-compose -f docker-compose.prod.yml up -d
```

### 방법 3: 전체 시스템 정리 (주의: 모든 이미지 삭제)
```bash
# 사용하지 않는 모든 리소스 정리
docker system prune -a -f

# 또는 단계별로 정리
docker container prune -f      # 중지된 컨테이너
docker image prune -a -f       # 사용하지 않는 이미지
docker volume prune -f         # 사용하지 않는 볼륨
docker builder prune -a -f     # 빌드 캐시
```

### 방법 4: 특정 이미지/캐시만 정리
```bash
# TRON 서비스 관련 이미지 삭제
docker images | grep tron
docker rmi <IMAGE_ID>

# 빌드 캐시 정리
docker builder prune -a -f
```

## 🚀 빠른 해결 스크립트

```bash
#!/bin/bash

echo "🧹 Docker 빌드 캐시 정리 중..."
docker builder prune -a -f

echo "🔨 TRON 서비스 재빌드 중..."
docker-compose -f docker-compose.prod.yml build --no-cache tron-service

echo "🚀 서비스 시작..."
docker-compose -f docker-compose.prod.yml up -d

echo "✅ 완료!"
```

## 📊 현재 사용량 확인

```bash
# Docker 리소스 사용량 확인
docker system df

# 상세 정보
docker system df -v
```

## ⚠️ 주의사항

1. **빌드 캐시 정리**: 다음 빌드가 느려질 수 있지만, 문제를 해결합니다.
2. **전체 시스템 정리**: 모든 이미지와 컨테이너가 삭제되므로 주의하세요.
3. **볼륨 정리**: 데이터베이스 볼륨까지 삭제될 수 있으므로 백업을 확인하세요.

## 🔄 권장 순서

1. 먼저 빌드 캐시만 정리: `docker builder prune -a -f`
2. 문제가 계속되면 `--no-cache`로 재빌드
3. 그래도 안 되면 특정 이미지 삭제 후 재빌드

