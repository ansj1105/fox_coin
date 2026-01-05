# 📋 로그 확인 가이드

## 🚀 빠른 명령어

### 모든 서비스 로그 확인
```bash
# 실시간 로그 (모든 서비스)
docker-compose -f docker-compose.prod.yml logs -f

# 최근 50줄만 확인
docker-compose -f docker-compose.prod.yml logs --tail 50
```

---

## 📦 서비스별 로그 확인

### 1. 백엔드 API (app)
```bash
# 실시간 로그
docker logs -f foxya-api

# 최근 100줄
docker logs foxya-api --tail 100

# 특정 키워드 필터링
docker logs foxya-api | grep -i error
docker logs foxya-api | grep -i "REQUEST"
docker logs foxya-api | grep -i grafana

# 특정 시간 이후 로그
docker logs foxya-api --since 10m  # 최근 10분
docker logs foxya-api --since 1h   # 최근 1시간
docker logs foxya-api --since 2024-01-01T00:00:00  # 특정 시간 이후
```

### 2. Nginx (Reverse Proxy)
```bash
# 실시간 로그
docker logs -f foxya-nginx

# 최근 50줄
docker logs foxya-nginx --tail 50

# Nginx 액세스 로그 (파일 직접 확인)
docker exec foxya-nginx tail -f /var/log/nginx/foxya_access.log
docker exec foxya-nginx tail -50 /var/log/nginx/foxya_access.log

# Nginx 에러 로그 (파일 직접 확인)
docker exec foxya-nginx tail -f /var/log/nginx/foxya_error.log
docker exec foxya-nginx tail -50 /var/log/nginx/foxya_error.log

# 에러만 필터링
docker logs foxya-nginx | grep -i error
docker exec foxya-nginx tail -100 /var/log/nginx/foxya_error.log | grep -i error
```

### 3. Grafana (모니터링)
```bash
# 실시간 로그
docker logs -f foxya-grafana

# 최근 50줄
docker logs foxya-grafana --tail 50

# HTTP Server 관련 로그
docker logs foxya-grafana | grep "HTTP Server Listen"
docker logs foxya-grafana | grep -i "subpath\|root_url"

# 에러만 확인
docker logs foxya-grafana | grep -i error

# 특정 시간 이후
docker logs foxya-grafana --since 10m
```

### 4. Prometheus (메트릭 수집)
```bash
# 실시간 로그
docker logs -f foxya-prometheus

# 최근 50줄
docker logs foxya-prometheus --tail 50

# 에러만 확인
docker logs foxya-prometheus | grep -i error
```

### 5. PostgreSQL (데이터베이스)
```bash
# 실시간 로그
docker logs -f foxya-postgres

# 최근 50줄
docker logs foxya-postgres --tail 50

# 에러만 확인
docker logs foxya-postgres | grep -i error
```

### 6. Redis (캐시)
```bash
# 실시간 로그
docker logs -f foxya-redis

# 최근 50줄
docker logs foxya-redis --tail 50
```

---

## 🔍 특정 문제 진단용 로그

### Grafana 접속 문제
```bash
# 1. 백엔드가 Grafana 요청을 받는지 확인
docker logs foxya-api --tail 50 | grep -i grafana

# 2. Nginx가 백엔드로 프록시하는지 확인
docker exec foxya-nginx tail -50 /var/log/nginx/foxya_error.log | grep grafana

# 3. Grafana 자체 로그 확인
docker logs foxya-grafana --tail 50 | grep -E "(HTTP Server|subpath|root_url|error)"
```

### API 요청 문제
```bash
# 백엔드 API 요청 로그
docker logs foxya-api --tail 100 | grep "REQUEST"

# Nginx 액세스 로그에서 특정 경로 확인
docker exec foxya-nginx tail -100 /var/log/nginx/foxya_access.log | grep "/api/"

# 에러 응답 확인
docker exec foxya-nginx tail -100 /var/log/nginx/foxya_access.log | grep " 50[0-9] "
```

### 데이터베이스 연결 문제
```bash
# 백엔드 DB 연결 에러
docker logs foxya-api | grep -i "database\|postgres\|connection"

# PostgreSQL 로그
docker logs foxya-postgres --tail 50
```

---

## 📊 로그 파일 위치

### Docker 컨테이너 내부 로그 파일
```bash
# Nginx 로그 파일
/var/log/nginx/foxya_access.log  # 액세스 로그
/var/log/nginx/foxya_error.log    # 에러 로그

# 확인 방법
docker exec foxya-nginx ls -la /var/log/nginx/
docker exec foxya-nginx cat /var/log/nginx/foxya_error.log
```

### 호스트 마운트된 로그 (설정된 경우)
```bash
# nginx/logs 디렉토리 확인
ls -la /var/www/foxya_coin_service/nginx/logs/

# 직접 확인
tail -f /var/www/foxya_coin_service/nginx/logs/foxya_access.log
tail -f /var/www/foxya_coin_service/nginx/logs/foxya_error.log
```

---

## 🛠️ 유용한 로그 필터링 명령어

### 시간대별 로그
```bash
# 최근 10분간의 로그
docker logs foxya-api --since 10m

# 최근 1시간간의 로그
docker logs foxya-api --since 1h

# 오늘의 로그만
docker logs foxya-api --since $(date +%Y-%m-%d)
```

### 여러 서비스 동시 확인
```bash
# 여러 컨테이너 로그 동시 확인
docker logs -f foxya-api foxya-nginx foxya-grafana

# 특정 키워드로 필터링
docker-compose -f docker-compose.prod.yml logs | grep -i error
```

### 로그 저장
```bash
# 로그를 파일로 저장
docker logs foxya-api --tail 1000 > api_logs.txt
docker logs foxya-nginx --tail 1000 > nginx_logs.txt

# 특정 시간대 로그 저장
docker logs foxya-api --since 1h > api_logs_1h.txt
```

---

## 🚨 문제 해결 시나리오

### 시나리오 1: Grafana가 로드되지 않음
```bash
# 1단계: 백엔드 로그 확인
docker logs foxya-api --tail 50 | grep -i grafana

# 2단계: Nginx 에러 로그 확인
docker exec foxya-nginx tail -50 /var/log/nginx/foxya_error.log

# 3단계: Grafana 로그 확인
docker logs foxya-grafana --tail 50 | grep -E "(HTTP Server|error)"
```

### 시나리오 2: API 요청이 실패함
```bash
# 1단계: 백엔드 로그 확인
docker logs foxya-api --tail 100 | grep "REQUEST"

# 2단계: Nginx 액세스 로그 확인
docker exec foxya-nginx tail -100 /var/log/nginx/foxya_access.log | grep "/api/"

# 3단계: 에러 응답 확인
docker exec foxya-nginx tail -100 /var/log/nginx/foxya_access.log | grep " 50[0-9] "
```

### 시나리오 3: 데이터베이스 연결 실패
```bash
# 1단계: PostgreSQL 로그 확인
docker logs foxya-postgres --tail 50

# 2단계: 백엔드 DB 연결 에러 확인
docker logs foxya-api | grep -i "database\|postgres\|connection"

# 3단계: 네트워크 확인
docker network inspect foxya-network | grep -A 5 postgres
```

---

## 💡 팁

1. **로그가 너무 많을 때**: `--tail` 옵션으로 최근 로그만 확인
2. **실시간 모니터링**: `-f` 옵션으로 실시간 로그 확인 (Ctrl+C로 종료)
3. **특정 키워드 찾기**: `grep` 명령어로 필터링
4. **여러 서비스 동시 확인**: `docker-compose logs` 사용
5. **로그 파일 크기 관리**: Docker의 로그 로테이션 설정 확인

---

## 📝 로그 확인 체크리스트

문제 발생 시 다음 순서로 확인:

```bash
# 1. 모든 컨테이너가 실행 중인지 확인
docker ps

# 2. 백엔드 로그 확인
docker logs foxya-api --tail 50

# 3. Nginx 로그 확인
docker logs foxya-nginx --tail 50
docker exec foxya-nginx tail -50 /var/log/nginx/foxya_error.log

# 4. 관련 서비스 로그 확인 (Grafana, Prometheus 등)
docker logs foxya-grafana --tail 50

# 5. 네트워크 연결 확인
docker network inspect foxya-network
```

