# 🚀 Foxya Coin Service - 배포 및 운영 가이드

## 📋 목차
- [사전 준비](#사전-준비)
- [서버 초기 설정](#서버-초기-설정)
- [배포](#배포)
- [운영 관리](#운영-관리)
- [SSL 인증서 설정](#ssl-인증서-설정)
- [트러블슈팅](#트러블슈팅)

---

## 사전 준비

### AWS 리소스
| 리소스 | 권장 사양 |
|--------|-----------|
| EC2 인스턴스 | Ubuntu 22.04 / t3.medium 이상 (최소 2GB RAM) |
| 보안 그룹 | 22(SSH), 80(HTTP), 443(HTTPS) |
| Elastic IP | 고정 IP 필요 시 (선택) |

### 보안 그룹 설정
```
인바운드 규칙:
- SSH (22): 내 IP만
- HTTP (80): 0.0.0.0/0
- HTTPS (443): 0.0.0.0/0
```

---

## 서버 초기 설정

### 1. SSH 접속
```bash
ssh -i your-key.pem ubuntu@your-ec2-public-ip
```

### 2. Docker 설치 (Ubuntu)
```bash
# 필수 패키지 설치
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Docker GPG 키 추가
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Docker 저장소 추가
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Docker 설치
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Docker 서비스 시작
sudo systemctl start docker
sudo systemctl enable docker

# 현재 유저를 docker 그룹에 추가
sudo usermod -aG docker $USER
newgrp docker

# 설치 확인
docker --version
docker compose version
```

### 3. 프로젝트 클론
```bash
# 프로젝트 디렉토리 생성
sudo mkdir -p /var/www/fox_coin
sudo chown -R $USER:$USER /var/www/fox_coin
cd /var/www/fox_coin

# Git 클론
git clone https://github.com/your-org/foxya-coin-service.git .

# 또는 SSH 방식
git clone git@github.com:your-org/foxya-coin-service.git .
```

### 4. 환경 변수 설정
```bash
# .env 파일 생성
cp .env.example .env
nano .env
```

**.env 파일 내용:**
```bash
# Application
APP_ENV=prod
APP_VERSION=1.0.0

# Database
DB_NAME=coin_system_cloud
DB_USER=foxya
DB_PASSWORD=foxya1124!@
DB_POOL_SIZE=20

# JWT (프로덕션에서 반드시 변경!)
JWT_SECRET=your-secure-jwt-secret-key-here
JWT_ACCESS_EXPIRE=30
JWT_REFRESH_EXPIRE=14400

# Redis
REDIS_PASSWORD=

# Blockchain
TRON_API_KEY=your_trongrid_api_key

# JVM
JAVA_OPTS=-Xmx1024m -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### 5. 필요 디렉토리 생성
```bash
mkdir -p logs nginx/ssl backups
chmod +x scripts/*.sh
```

---

## 배포

### 첫 배포
```bash
cd /var/www/fox_coin
./scripts/deploy.sh deploy
```

### 업데이트 배포
```bash
cd /var/www/fox_coin
git pull origin develop
docker compose -f docker-compose.prod.yml up -d --build app
```

### 배포 확인
```bash
# 컨테이너 상태
docker compose -f docker-compose.prod.yml ps

# 헬스체크
curl http://localhost/health

# 외부 접속 확인
curl http://your-ec2-public-ip/health
```

---

## 운영 관리

### 📊 상태 확인
```bash
# 컨테이너 상태
docker compose -f docker-compose.prod.yml ps

# 리소스 사용량 (CPU, 메모리)
docker stats

# 헬스체크
curl http://localhost/health
```

### 📝 로그 확인
```bash
# 전체 로그 (실시간)
docker compose -f docker-compose.prod.yml logs -f

# 앱 로그만
docker compose -f docker-compose.prod.yml logs -f app

# 최근 100줄
docker compose -f docker-compose.prod.yml logs --tail=100 app

# Nginx 로그
docker compose -f docker-compose.prod.yml logs -f nginx

# PostgreSQL 로그
docker compose -f docker-compose.prod.yml logs -f postgres
```

### 🔄 서비스 관리
```bash
# 전체 재시작
docker compose -f docker-compose.prod.yml restart

# 앱만 재시작
docker compose -f docker-compose.prod.yml restart app

# 서비스 중지
docker compose -f docker-compose.prod.yml down

# 서비스 시작
docker compose -f docker-compose.prod.yml up -d

# 앱만 재빌드
docker compose -f docker-compose.prod.yml up -d --build app
```

### 💾 데이터베이스 관리
```bash
# PostgreSQL 접속
docker compose -f docker-compose.prod.yml exec postgres psql -U foxya -d coin_system_cloud

# DB 백업
./scripts/deploy.sh backup-db

# 수동 백업
docker compose -f docker-compose.prod.yml exec postgres pg_dump -U foxya coin_system_cloud > backup_$(date +%Y%m%d).sql

# DB 복원
cat backup_20241204.sql | docker compose -f docker-compose.prod.yml exec -T postgres psql -U foxya -d coin_system_cloud
```

### 🔴 Redis 관리
```bash
# Redis CLI 접속
docker compose -f docker-compose.prod.yml exec redis redis-cli

# Redis 상태 확인
docker compose -f docker-compose.prod.yml exec redis redis-cli info

# 캐시 전체 삭제
docker compose -f docker-compose.prod.yml exec redis redis-cli FLUSHALL
```

### 🧹 리소스 정리
```bash
# 미사용 Docker 리소스 정리
docker system prune -f

# 오래된 이미지 삭제
docker image prune -a -f

# 디스크 사용량 확인
df -h
docker system df
```

### 📋 명령어 요약
| 작업 | 명령어 |
|------|--------|
| 상태 확인 | `docker compose -f docker-compose.prod.yml ps` |
| 로그 보기 | `docker compose -f docker-compose.prod.yml logs -f app` |
| 재시작 | `docker compose -f docker-compose.prod.yml restart app` |
| 업데이트 | `git pull && docker compose -f docker-compose.prod.yml up -d --build app` |
| DB 백업 | `./scripts/deploy.sh backup-db` |
| 리소스 확인 | `docker stats` |
| 정리 | `docker system prune -f` |

---

## SSL 인증서 설정

### Let's Encrypt 무료 인증서 발급

#### 1. Certbot 설치
```bash
sudo apt-get update
sudo apt-get install -y certbot
```

#### 2. 인증서 발급
```bash
# Nginx 임시 중지 (80포트 필요)
docker compose -f docker-compose.prod.yml stop nginx

# 인증서 발급 (도메인을 실제 도메인으로 변경)
sudo certbot certonly --standalone -d your-domain.com -d www.your-domain.com
```

#### 3. 인증서 복사
```bash
sudo cp /etc/letsencrypt/live/your-domain.com/fullchain.pem /var/www/fox_coin/nginx/ssl/
sudo cp /etc/letsencrypt/live/your-domain.com/privkey.pem /var/www/fox_coin/nginx/ssl/
sudo chown -R $USER:$USER /var/www/fox_coin/nginx/ssl/
```

#### 4. Nginx 설정 수정
`nginx/conf.d/default.conf` 파일 수정:

```nginx
# HTTP → HTTPS 리다이렉트
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS 서버
server {
    listen 443 ssl http2;
    server_name your-domain.com www.your-domain.com;

    # SSL 인증서
    ssl_certificate /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;
    
    # SSL 설정
    ssl_session_timeout 1d;
    ssl_session_cache shared:SSL:50m;
    ssl_session_tickets off;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;

    # HSTS
    add_header Strict-Transport-Security "max-age=63072000" always;

    # 로그
    access_log /var/log/nginx/foxya_access.log main;
    error_log /var/log/nginx/foxya_error.log warn;

    # API 프록시
    location / {
        limit_req zone=api_limit burst=20 nodelay;
        limit_conn conn_limit 10;

        proxy_pass http://foxya_api;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Health Check
    location /health {
        access_log off;
        proxy_pass http://foxya_api/health;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }
}
```

#### 5. Nginx 재시작
```bash
docker compose -f docker-compose.prod.yml up -d nginx
```

#### 6. 인증서 자동 갱신 설정
```bash
sudo crontab -e

# 아래 줄 추가 (매월 1일 새벽 3시에 갱신)
0 3 1 * * certbot renew --pre-hook "docker compose -f /var/www/fox_coin/docker-compose.prod.yml stop nginx" --post-hook "cp /etc/letsencrypt/live/your-domain.com/*.pem /var/www/fox_coin/nginx/ssl/ && docker compose -f /var/www/fox_coin/docker-compose.prod.yml start nginx"
```

---

## 트러블슈팅

### 컨테이너가 시작되지 않음
```bash
# 로그 확인
docker compose -f docker-compose.prod.yml logs app

# 컨테이너 상태 확인
docker ps -a
```

### DB 연결 실패
```bash
# PostgreSQL 컨테이너 상태 확인
docker compose -f docker-compose.prod.yml logs postgres

# DB 직접 접속 테스트
docker compose -f docker-compose.prod.yml exec postgres psql -U foxya -d coin_system_cloud
```

### 포트 충돌
```bash
# 사용 중인 포트 확인
sudo netstat -tlnp | grep :80
sudo netstat -tlnp | grep :8080

# 기존 프로세스 종료
sudo kill -9 <PID>
```

### 메모리 부족
```bash
# 메모리 사용량 확인
free -h
docker stats

# JVM 메모리 조정 (.env 파일)
JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC
```

### Redis unhealthy
```bash
# Redis 로그 확인
docker logs foxya-redis

# 볼륨 삭제 후 재시작
docker compose -f docker-compose.prod.yml down -v
docker volume rm fox_coin_redis-data
docker compose -f docker-compose.prod.yml up -d
```

### Git 충돌
```bash
# 로컬 변경사항 버리고 최신 코드로
git fetch origin
git reset --hard origin/develop
```

---

## 📁 디렉토리 구조

```
/var/www/fox_coin/
├── .env                      # 환경변수 (git에 포함 안됨)
├── docker-compose.prod.yml   # 프로덕션 Docker Compose
├── Dockerfile.prod           # 프로덕션 Dockerfile
├── nginx/
│   ├── nginx.conf            # Nginx 메인 설정
│   ├── conf.d/
│   │   └── default.conf      # 서버 블록 설정
│   ├── ssl/                  # SSL 인증서
│   │   ├── fullchain.pem
│   │   └── privkey.pem
│   └── logs/                 # Nginx 로그
├── logs/                     # 앱 로그
├── backups/                  # DB 백업
└── scripts/
    ├── deploy.sh             # 배포 스크립트
    └── init-db.sql           # DB 초기화
```

---

## 🔗 유용한 링크

- **Swagger UI**: `http://your-domain.com/api-docs`
- **Health Check**: `http://your-domain.com/health`
- **OpenAPI Spec**: `http://your-domain.com/openapi.yaml`

---

*Last Updated: 2024-12-04*

