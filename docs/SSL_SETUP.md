# SSL/HTTPS 설정 가이드

이 문서는 Foxya Coin Service에 SSL/HTTPS를 적용하는 방법을 설명합니다.

## 📋 목차

1. [자체 서명 인증서 (개발/테스트용)](#1-자체-서명-인증서-개발테스트용)
2. [Let's Encrypt 인증서 (프로덕션용)](#2-lets-encrypt-인증서-프로덕션용)
3. [Nginx 설정 확인](#3-nginx-설정-확인)
4. [SSL 적용 확인](#4-ssl-적용-확인)

---

## 1. 자체 서명 인증서 (개발/테스트용)

### 1.1 인증서 생성

프로젝트 루트에서 다음 명령어를 실행하세요:

```bash
cd /var/www/foxya_coin_service
bash nginx/generate-self-signed-cert.sh
```

또는 수동으로 생성:

```bash
mkdir -p nginx/ssl
cd nginx/ssl

# 인증서 생성 (365일 유효)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout key.pem \
  -out cert.pem \
  -subj "/C=KR/ST=Seoul/L=Seoul/O=Foxya/OU=IT/CN=localhost"

# Let's Encrypt 형식으로 복사
cp cert.pem fullchain.pem
cp key.pem privkey.pem
```

### 1.2 Nginx 설정 확인

`nginx/conf.d/default.conf` 파일에서 다음 설정이 활성화되어 있는지 확인:

```nginx
# 자체 서명 인증서 사용 시
ssl_certificate /etc/nginx/ssl/cert.pem;
ssl_certificate_key /etc/nginx/ssl/key.pem;
```

### 1.3 Docker Compose 재시작

```bash
cd /var/www/foxya_coin_service
docker-compose -f docker-compose.prod.yml restart nginx
```

### 1.4 확인

브라우저에서 `https://localhost` 또는 `https://your-server-ip`로 접속하세요.

⚠️ **주의**: 자체 서명 인증서는 브라우저에서 보안 경고가 표시됩니다. 이는 정상이며, "고급" → "계속 진행"을 클릭하면 접속할 수 있습니다.

---

## 2. Let's Encrypt 인증서 (프로덕션용)

### 2.1 사전 요구사항

- 도메인 이름이 필요합니다 (예: `example.com`)
- 도메인이 서버 IP로 DNS A 레코드가 설정되어 있어야 합니다
- 80번 포트가 열려 있어야 합니다 (Let's Encrypt 인증)

### 2.2 Certbot 설치

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install certbot

# CentOS/RHEL
sudo yum install certbot
```

### 2.3 인증서 발급

#### 방법 1: Standalone 모드 (Nginx 중지 필요)

```bash
# Nginx 중지
docker-compose -f docker-compose.prod.yml stop nginx

# 인증서 발급 (도메인을 실제 도메인으로 변경)
sudo certbot certonly --standalone \
  -d example.com \
  -d www.example.com \
  --email your-email@example.com \
  --agree-tos \
  --non-interactive

# 인증서 복사
sudo mkdir -p /var/www/foxya_coin_service/nginx/ssl
sudo cp /etc/letsencrypt/live/example.com/fullchain.pem /var/www/foxya_coin_service/nginx/ssl/
sudo cp /etc/letsencrypt/live/example.com/privkey.pem /var/www/foxya_coin_service/nginx/ssl/
sudo chown -R $USER:$USER /var/www/foxya_coin_service/nginx/ssl/
```

#### 방법 2: Webroot 모드 (Nginx 실행 중 가능)

```bash
# certbot 디렉토리 생성
mkdir -p /var/www/foxya_coin_service/certbot/www
mkdir -p /var/www/foxya_coin_service/certbot/conf

# 인증서 발급
sudo certbot certonly --webroot \
  -w /var/www/foxya_coin_service/certbot/www \
  -d example.com \
  -d www.example.com \
  --email your-email@example.com \
  --agree-tos \
  --non-interactive

# 인증서 복사
sudo cp /etc/letsencrypt/live/example.com/fullchain.pem /var/www/foxya_coin_service/nginx/ssl/
sudo cp /etc/letsencrypt/live/example.com/privkey.pem /var/www/foxya_coin_service/nginx/ssl/
sudo chown -R $USER:$USER /var/www/foxya_coin_service/nginx/ssl/
```

### 2.4 Nginx 설정 수정

`nginx/conf.d/default.conf` 파일에서 다음 설정을 활성화:

```nginx
# Let's Encrypt 인증서 사용 시
ssl_certificate /etc/nginx/ssl/fullchain.pem;
ssl_certificate_key /etc/nginx/ssl/privkey.pem;
```

그리고 자체 서명 인증서 설정은 주석 처리:

```nginx
# 자체 서명 인증서 사용 시 (주석 처리)
# ssl_certificate /etc/nginx/ssl/cert.pem;
# ssl_certificate_key /etc/nginx/ssl/key.pem;
```

### 2.5 서버 이름 설정 (선택사항)

`nginx/conf.d/default.conf`에서 `server_name`을 실제 도메인으로 변경:

```nginx
server {
    listen 443 ssl http2;
    server_name example.com www.example.com;  # 실제 도메인으로 변경
    ...
}
```

### 2.6 Docker Compose 재시작

```bash
cd /var/www/foxya_coin_service
docker-compose -f docker-compose.prod.yml restart nginx
```

### 2.7 인증서 자동 갱신 설정

Let's Encrypt 인증서는 90일마다 갱신해야 합니다. Cron 작업을 설정하세요:

```bash
# Crontab 편집
sudo crontab -e

# 다음 줄 추가 (매일 새벽 3시에 갱신 시도)
0 3 * * * certbot renew --quiet --deploy-hook "docker-compose -f /var/www/foxya_coin_service/docker-compose.prod.yml restart nginx"
```

또는 systemd timer 사용:

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

---

## 3. Nginx 설정 확인

### 3.1 SSL 설정 확인

`nginx/conf.d/default.conf` 파일에서 다음 설정이 포함되어 있는지 확인:

- ✅ SSL 인증서 경로 설정
- ✅ TLS 1.2, 1.3 프로토콜 사용
- ✅ 강력한 암호화 알고리즘 사용
- ✅ HSTS 헤더 설정
- ✅ HTTP → HTTPS 리다이렉트

### 3.2 설정 테스트

```bash
# Nginx 설정 문법 확인
docker exec foxya-nginx nginx -t

# 예상 출력:
# nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
# nginx: configuration file /etc/nginx/nginx.conf test is successful
```

---

## 4. SSL 적용 확인

### 4.1 브라우저에서 확인

1. `https://your-domain.com` 또는 `https://your-server-ip`로 접속
2. 브라우저 주소창의 자물쇠 아이콘 확인
3. 자물쇠 아이콘 클릭 → "연결이 안전합니다" 확인

### 4.2 SSL Labs 테스트 (프로덕션)

Let's Encrypt 인증서를 사용하는 경우, [SSL Labs](https://www.ssllabs.com/ssltest/)에서 테스트:

1. https://www.ssllabs.com/ssltest/ 접속
2. 도메인 입력 후 테스트 실행
3. A 등급 이상을 목표로 합니다

### 4.3 명령줄에서 확인

```bash
# SSL 인증서 정보 확인
openssl s_client -connect your-domain.com:443 -servername your-domain.com < /dev/null 2>/dev/null | openssl x509 -noout -dates

# TLS 연결 테스트
curl -vI https://your-domain.com
```

### 4.4 HTTP → HTTPS 리다이렉트 확인

```bash
# HTTP 요청 시 HTTPS로 리다이렉트되는지 확인
curl -I http://your-domain.com

# 예상 응답:
# HTTP/1.1 301 Moved Permanently
# Location: https://your-domain.com/...
```

---

## 5. 문제 해결

### 5.1 인증서 파일 권한 오류

```bash
# 인증서 파일 권한 확인 및 수정
chmod 644 nginx/ssl/*.pem
chmod 600 nginx/ssl/*.key
```

### 5.2 Nginx가 SSL 인증서를 찾을 수 없음

```bash
# 인증서 파일 존재 확인
ls -la nginx/ssl/

# Docker 볼륨 마운트 확인
docker inspect foxya-nginx | grep -A 10 Mounts
```

### 5.3 SSL 연결 실패

```bash
# Nginx 로그 확인
docker logs foxya-nginx

# 방화벽 확인 (443 포트 열려있는지)
sudo ufw status
sudo firewall-cmd --list-ports  # CentOS/RHEL
```

### 5.4 Let's Encrypt 갱신 실패

```bash
# 수동 갱신 시도
sudo certbot renew --dry-run

# 실제 갱신
sudo certbot renew

# 인증서 만료일 확인
sudo certbot certificates
```

---

## 6. 보안 권장사항

### 6.1 SSL 설정 강화

`nginx/conf.d/default.conf`에 이미 다음 보안 설정이 포함되어 있습니다:

- ✅ TLS 1.2, 1.3만 허용
- ✅ 강력한 암호화 알고리즘 사용
- ✅ HSTS 헤더 설정
- ✅ SSL 세션 캐싱
- ✅ OCSP Stapling (선택사항)

### 6.2 추가 보안 헤더

필요시 다음 헤더를 추가할 수 있습니다:

```nginx
# Content Security Policy
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';" always;

# Referrer Policy
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
```

---

## 7. 참고 자료

- [Let's Encrypt 공식 문서](https://letsencrypt.org/docs/)
- [Certbot 사용 가이드](https://certbot.eff.org/)
- [Nginx SSL 설정 가이드](https://nginx.org/en/docs/http/configuring_https_servers.html)
- [SSL Labs 테스트 도구](https://www.ssllabs.com/ssltest/)

---

## 8. 요약

### 개발/테스트 환경
1. `bash nginx/generate-self-signed-cert.sh` 실행
2. `docker-compose -f docker-compose.prod.yml restart nginx`

### 프로덕션 환경
1. Certbot으로 Let's Encrypt 인증서 발급
2. 인증서를 `nginx/ssl/` 디렉토리에 복사
3. `nginx/conf.d/default.conf`에서 Let's Encrypt 인증서 경로 활성화
4. 자동 갱신 Cron/Timer 설정
5. `docker-compose -f docker-compose.prod.yml restart nginx`

---

**문의사항이나 문제가 있으면 이슈를 등록해주세요.**

