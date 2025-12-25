# korion.io.kr SSL/TLS 인증서 설정 가이드

## 도메인 목록
- `korion.io.kr` (메인 도메인)
- `www.korion.io.kr` (www 서브도메인)
- `api.korion.io.kr` (API 서브도메인)
- `dev.korion.io.kr` (개발 서브도메인)

## 사전 요구사항
1. 모든 도메인이 서버 IP로 DNS A 레코드가 설정되어 있어야 합니다
2. 80번 포트가 열려 있어야 합니다 (Let's Encrypt 인증)
3. Certbot이 설치되어 있어야 합니다

## SSL 인증서 발급 방법

### 방법 1: Standalone 모드 (Nginx 중지 필요)

```bash
# 프로젝트 디렉토리로 이동
cd /var/www/foxya_coin_service

# Nginx 중지
docker-compose -f docker-compose.prod.yml stop nginx

# Certbot 설치 (아직 설치하지 않은 경우)
sudo apt-get update
sudo apt-get install certbot

# 인증서 발급 (모든 도메인 포함)
sudo certbot certonly --standalone \
  -d korion.io.kr \
  -d www.korion.io.kr \
  -d api.korion.io.kr \
  -d dev.korion.io.kr \
  --email your-email@example.com \
  --agree-tos \
  --non-interactive

# 인증서 복사
sudo mkdir -p /var/www/foxya_coin_service/nginx/ssl
sudo cp /etc/letsencrypt/live/korion.io.kr/fullchain.pem /var/www/foxya_coin_service/nginx/ssl/
sudo cp /etc/letsencrypt/live/korion.io.kr/privkey.pem /var/www/foxya_coin_service/nginx/ssl/
sudo chown -R $USER:$USER /var/www/foxya_coin_service/nginx/ssl/

# Nginx 재시작
docker-compose -f docker-compose.prod.yml start nginx
```

### 방법 2: Webroot 모드 (Nginx 실행 중 가능, 권장)

```bash
# 프로젝트 디렉토리로 이동
cd /var/www/foxya_coin_service

# certbot 디렉토리 생성 (이미 있다면 생략)
mkdir -p certbot/www
mkdir -p certbot/conf

# Certbot 설치 (아직 설치하지 않은 경우)
sudo apt-get update
sudo apt-get install certbot

# 인증서 발급 (모든 도메인 포함)
sudo certbot certonly --webroot \
  -w /var/www/foxya_coin_service/certbot/www \
  -d korion.io.kr \
  -d www.korion.io.kr \
  -d api.korion.io.kr \
  -d dev.korion.io.kr \
  --email your-email@example.com \
  --agree-tos \
  --non-interactive

# 인증서 복사
sudo mkdir -p /var/www/foxya_coin_service/nginx/ssl
sudo cp /etc/letsencrypt/live/korion.io.kr/fullchain.pem /var/www/foxya_coin_service/nginx/ssl/
sudo cp /etc/letsencrypt/live/korion.io.kr/privkey.pem /var/www/foxya_coin_service/nginx/ssl/
sudo chown -R $USER:$USER /var/www/foxya_coin_service/nginx/ssl/
```

### 방법 3: Nginx 플러그인 사용 (가장 간단)

```bash
# Certbot Nginx 플러그인 설치
sudo apt-get update
sudo apt-get install certbot python3-certbot-nginx

# 인증서 발급 및 Nginx 자동 설정
sudo certbot --nginx \
  -d korion.io.kr \
  -d www.korion.io.kr \
  -d api.korion.io.kr \
  -d dev.korion.io.kr \
  --email your-email@example.com \
  --agree-tos \
  --non-interactive

# 인증서 복사 (Docker 볼륨에)
sudo cp /etc/letsencrypt/live/korion.io.kr/fullchain.pem /var/www/foxya_coin_service/nginx/ssl/
sudo cp /etc/letsencrypt/live/korion.io.kr/privkey.pem /var/www/foxya_coin_service/nginx/ssl/
sudo chown -R $USER:$USER /var/www/foxya_coin_service/nginx/ssl/
```

## Nginx 설정 업데이트

`nginx/conf.d/default.conf` 파일에서 SSL 인증서 경로를 활성화:

```nginx
# Let's Encrypt 인증서 사용 시 (주석 해제)
ssl_certificate /etc/nginx/ssl/fullchain.pem;
ssl_certificate_key /etc/nginx/ssl/privkey.pem;

# 자체 서명 인증서 사용 시 (주석 처리)
# ssl_certificate /etc/nginx/ssl/cert.pem;
# ssl_certificate_key /etc/nginx/ssl/key.pem;
```

## Nginx 재시작

```bash
# 설정 테스트
docker exec foxya-nginx nginx -t

# 재시작
docker-compose -f docker-compose.prod.yml restart nginx
```

## 인증서 자동 갱신 설정

Let's Encrypt 인증서는 90일마다 갱신해야 합니다.

### Cron 사용

```bash
# Crontab 편집
sudo crontab -e

# 다음 줄 추가 (매일 새벽 3시에 갱신 시도)
0 3 * * * certbot renew --quiet --deploy-hook "docker-compose -f /var/www/foxya_coin_service/docker-compose.prod.yml restart nginx"
```

### Systemd Timer 사용

```bash
# /etc/systemd/system/certbot-renew.service 생성
sudo nano /etc/systemd/system/certbot-renew.service
```

```ini
[Unit]
Description=Renew Let's Encrypt certificates
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --quiet --deploy-hook "docker-compose -f /var/www/foxya_coin_service/docker-compose.prod.yml restart nginx"
```

```bash
# /etc/systemd/system/certbot-renew.timer 생성
sudo nano /etc/systemd/system/certbot-renew.timer
```

```ini
[Unit]
Description=Run certbot twice daily

[Timer]
OnCalendar=*-*-* 03:00,15:00
RandomizedDelaySec=3600
Persistent=true

[Install]
WantedBy=timers.target
```

```bash
# Timer 활성화
sudo systemctl enable certbot-renew.timer
sudo systemctl start certbot-renew.timer
```

## 확인 방법

### 1. 브라우저에서 확인
- `https://korion.io.kr`
- `https://www.korion.io.kr`
- `https://api.korion.io.kr`
- `https://dev.korion.io.kr`

모든 도메인에서 자물쇠 아이콘이 표시되어야 합니다.

### 2. 명령줄에서 확인

```bash
# SSL 인증서 정보 확인
openssl s_client -connect korion.io.kr:443 -servername korion.io.kr < /dev/null 2>/dev/null | openssl x509 -noout -dates -subject

# TLS 연결 테스트
curl -vI https://korion.io.kr
```

### 3. SSL Labs 테스트

[SSL Labs](https://www.ssllabs.com/ssltest/)에서 테스트:
- https://www.ssllabs.com/ssltest/ 접속
- `korion.io.kr` 입력 후 테스트 실행
- A 등급 이상을 목표로 합니다

## 문제 해결

### 인증서 발급 실패

1. **DNS 확인**
   ```bash
   dig korion.io.kr
   dig www.korion.io.kr
   dig api.korion.io.kr
   dig dev.korion.io.kr
   ```
   모든 도메인이 서버 IP로 올바르게 설정되어 있는지 확인

2. **포트 확인**
   ```bash
   sudo netstat -tlnp | grep :80
   sudo ufw status
   ```
   80번 포트가 열려 있는지 확인

3. **방화벽 확인**
   ```bash
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   ```

### 인증서 파일 권한 오류

```bash
# 인증서 파일 권한 확인 및 수정
chmod 644 /var/www/foxya_coin_service/nginx/ssl/*.pem
chmod 600 /var/www/foxya_coin_service/nginx/ssl/*.key
```

### Nginx가 SSL 인증서를 찾을 수 없음

```bash
# 인증서 파일 존재 확인
ls -la /var/www/foxya_coin_service/nginx/ssl/

# Docker 볼륨 마운트 확인
docker inspect foxya-nginx | grep -A 10 Mounts
```

## 요약

1. **인증서 발급**: `certbot certonly --webroot` 또는 `certbot --nginx` 사용
2. **Nginx 설정**: SSL 인증서 경로 활성화
3. **Nginx 재시작**: `docker-compose -f docker-compose.prod.yml restart nginx`
4. **자동 갱신 설정**: Cron 또는 Systemd Timer 설정

---

**참고**: 이메일 주소(`your-email@example.com`)를 실제 이메일 주소로 변경하세요.

