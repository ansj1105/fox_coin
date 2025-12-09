# 프론트엔드 설정 가이드

## 개요

Nginx는 80 포트를 사용하여 프론트엔드와 백엔드를 모두 서빙합니다.

- **프로덕션**: Nginx가 프론트엔드 정적 파일을 서빙하고, API 요청을 백엔드로 프록시
- **개발**: 프론트엔드는 별도 포트에서 실행하고, API만 Nginx를 통해 백엔드로 전달

## 80 포트를 사용하는 이유

1. **HTTP 기본 포트**: 브라우저에서 포트 번호 없이 접근 가능 (`http://example.com` vs `http://example.com:3000`)
2. **표준 관행**: 대부분의 웹 서버가 80(HTTP)과 443(HTTPS) 포트를 사용
3. **SSL/HTTPS 호환**: 443 포트와 함께 사용하기 쉬움

## 프로덕션 환경 설정

### 1. 프론트엔드 빌드

```bash
# 프론트엔드 프로젝트에서 빌드
cd frontend
npm run build
# 또는
yarn build
```

### 2. Docker Compose 설정

`docker-compose.prod.yml`에서 프론트엔드 빌드 파일을 마운트:

```yaml
nginx:
  volumes:
    - ./frontend/dist:/usr/share/nginx/html/frontend:ro
```

### 3. Nginx 설정

현재 `nginx/conf.d/default.conf`는 다음과 같이 구성되어 있습니다:

- `/api/*` → 백엔드 API로 프록시
- `/` → 프론트엔드 정적 파일 서빙

## 개발 환경 설정

### 옵션 1: 프론트엔드를 별도 포트에서 실행 (권장)

프론트엔드는 개발 서버(예: 3000, 5173 포트)에서 실행하고, API 요청만 Nginx를 통해 백엔드로 전달:

```javascript
// 프론트엔드 API 설정
const API_BASE_URL = 'http://localhost/api/v1';
```

**장점:**
- Hot Reload 지원
- 개발 도구 사용 가능
- 프론트엔드와 백엔드 독립 실행

### 옵션 2: Nginx가 프론트엔드 개발 서버를 프록시

`nginx/conf.d/frontend-dev.conf.example` 파일을 참고하여 프론트엔드 개발 서버를 프록시:

```nginx
location / {
    proxy_pass http://host.docker.internal:3000;
    # WebSocket 지원
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

## 포트 구성

| 서비스 | 포트 | 용도 |
|--------|------|------|
| Nginx | 80, 443 | 프론트엔드 + API 프록시 |
| 백엔드 API | 8080 | API 서버 (내부) |
| 프론트엔드 (개발) | 3000, 5173 등 | 개발 서버 (선택) |
| PostgreSQL | 5432 | 데이터베이스 (내부) |
| Redis | 6379 | 캐시/이벤트 (내부) |

## 요청 흐름

### 프로덕션
```
사용자 → Nginx:80 → 프론트엔드 정적 파일
사용자 → Nginx:80/api/* → 백엔드:8080
```

### 개발 환경
```
사용자 → 프론트엔드:3000 (개발 서버)
프론트엔드 → Nginx:80/api/* → 백엔드:8080
```

## CORS 설정

프론트엔드가 별도 포트에서 실행되는 경우, 백엔드에서 CORS를 허용해야 합니다.

현재 `ApiVerticle.java`에서 CORS가 설정되어 있습니다:

```java
router.route().handler(CorsHandler.create()
    .addRelativeOrigin(".*")
    // ...
);
```

## SSL/HTTPS 설정

프로덕션 환경에서는 SSL 인증서를 설정하여 HTTPS를 사용할 수 있습니다:

1. SSL 인증서를 `nginx/ssl/` 디렉토리에 배치
2. `nginx/conf.d/default.conf`에서 SSL 설정 주석 해제
3. HTTP → HTTPS 리다이렉트 활성화

## 문제 해결

### 프론트엔드가 80 포트를 사용할 수 없다고 나오는 경우

이는 정상입니다. 프론트엔드는:
- **프로덕션**: Nginx가 80 포트에서 프론트엔드를 서빙
- **개발**: 별도 포트(3000 등)에서 실행하고, API만 Nginx를 통해 백엔드로 전달

### 포트 충돌이 발생하는 경우

다른 서비스가 80 포트를 사용 중인지 확인:

```bash
sudo lsof -i :80
sudo netstat -tlnp | grep :80
```

### 프론트엔드에서 API 호출이 실패하는 경우

1. CORS 설정 확인
2. API 경로 확인 (`/api/v1/...`)
3. Nginx 프록시 설정 확인

