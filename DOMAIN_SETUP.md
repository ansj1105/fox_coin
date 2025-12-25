# 도메인 설정 가이드

## 구매한 도메인
- `korion.io.kr` (메인 도메인)
- `www.korion.io.kr` (www 서브도메인)
- `api.korion.io.kr` (API 서브도메인)
- `dev.korion.io.kr` (개발 서브도메인)

## 설정 완료된 파일

### 1. Nginx 설정
**파일**: `nginx/conf.d/default.conf`

이미 도메인이 설정되어 있습니다:
- `server_name korion.io.kr www.korion.io.kr api.korion.io.kr dev.korion.io.kr;`
- HTTP에서 HTTPS로 리다이렉트 설정 포함
- 프론트엔드 서빙 및 API 프록시 설정 완료

### 2. 백엔드 설정
**파일**: `src/main/resources/config.json`

프로덕션 환경의 frontend.baseUrl 업데이트:
```json
"frontend": {
  "baseUrl": "https://korion.io.kr"
}
```

### 3. CORS 설정
**파일**: `src/main/java/com/foxya/coin/verticle/ApiVerticle.java`

현재 모든 origin을 허용하는 설정입니다:
```java
.addRelativeOrigin(".*")
```

보안을 강화하려면 특정 도메인만 허용하도록 변경할 수 있습니다:
```java
.addOrigin("https://korion.io.kr")
.addOrigin("https://www.korion.io.kr")
.addOrigin("https://dev.korion.io.kr")
```

## 추가 설정 필요 사항

### 1. DNS 설정
도메인 제공업체에서 다음 DNS 레코드를 추가해야 합니다:

```
Type    Name    Value              TTL
A       @       54.210.92.221      (서버 IP 주소)
A       www     54.210.92.221
A       api     54.210.92.221
A       dev     54.210.92.221
```

또는 CNAME 사용:
```
Type    Name    Value              TTL
A       @       54.210.92.221
CNAME   www     korion.io.kr
CNAME   api     korion.io.kr
CNAME   dev     korion.io.kr
```

### 2. SSL 인증서 설정

#### 방법 1: Let's Encrypt (무료, 권장)
```bash
# Certbot 설치
sudo apt-get update
sudo apt-get install certbot python3-certbot-nginx

# 인증서 발급
sudo certbot --nginx -d korion.io.kr -d www.korion.io.kr -d api.korion.io.kr -d dev.korion.io.kr

# 자동 갱신 설정
sudo certbot renew --dry-run
```

nginx 설정 파일의 SSL 인증서 경로 주석 해제:
```nginx
ssl_certificate /etc/letsencrypt/live/korion.io.kr/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/korion.io.kr/privkey.pem;
```

#### 방법 2: 자체 서명 인증서 (개발/테스트용)
이미 `nginx/ssl/` 디렉토리에 설정되어 있습니다.

### 3. 프론트엔드 환경 변수

프론트엔드 프로젝트(`coin_front`)에서 환경 변수 설정:

**`.env.production`**:
```
# Nginx 프록시를 사용하므로 빈 문자열 (같은 도메인에서 /api 경로로 프록시)
VITE_API_BASE_URL=
```

**`.env.development`**:
```
# 개발 환경: 로컬 서버 또는 dev.korion.io.kr
VITE_API_BASE_URL=http://localhost:8080
# 또는
# VITE_API_BASE_URL=https://dev-api.korion.io.kr
```

## 배포 후 확인사항

1. **도메인 접속 확인**
   ```bash
   curl -I https://korion.io.kr
   curl -I https://www.korion.io.kr
   curl -I https://api.korion.io.kr
   ```

2. **HTTPS 리다이렉트 확인**
   ```bash
   curl -I http://korion.io.kr
   # 301 리다이렉트 응답 확인
   ```

3. **API 프록시 확인**
   ```bash
   curl https://korion.io.kr/api/health
   curl https://api.korion.io.kr/api/health
   ```

4. **CORS 헤더 확인**
   ```bash
   curl -H "Origin: https://korion.io.kr" \
        -H "Access-Control-Request-Method: POST" \
        -X OPTIONS \
        https://api.korion.io.kr/api/v1/auth/login
   ```

## 참고사항

- 모든 도메인은 같은 서버(54.210.92.221)로 라우팅됩니다
- Nginx가 프론트엔드와 API를 모두 서빙합니다
- API는 `/api` 경로로 프록시됩니다
- dev.korion.io.kr은 개발 환경용으로 사용 가능합니다

