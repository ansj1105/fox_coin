# 모니터링 페이지 접근 가이드

## 📋 개요

모니터링 접근 주소는 다음과 같습니다.

- **Grafana**: **`https://dev.korion.io.kr/`** 에서 접근 (루트 경로 사용, 리다이렉트 이슈 없음)
- **Prometheus**: **`https://api.korion.io.kr/`** (예전 `.../6s9ex74204/prometheus/` 또는 dev 서브경로 접속 시 이 주소로 리다이렉트)
- 예전 Grafana 주소 `.../6s9ex74204/grafana/` 로 접속하면 `https://dev.korion.io.kr/` 로 자동 리다이렉트됩니다.

## 🔐 Grafana 로그인

- 기본 계정: `admin`
- 기본 비밀번호: `admin` (또는 `.env`의 `GRAFANA_PASSWORD`)

## 🌐 접속 방법

### 1. Grafana 접속

#### 브라우저에서 접속

1. **직접 URL 접속 (권장)**
   ```
   https://dev.korion.io.kr/
   ```

2. **예전 경로** (자동 리다이렉트)
   - `https://korion.io.kr/6s9ex74204/grafana/` → `https://dev.korion.io.kr/` 로 이동

#### cURL로 접속

```bash
# 기본 접속 (프로덕션)
curl -sI https://dev.korion.io.kr/

# 로컬/다른 포트 사용 시
curl http://localhost:8080/6s9ex74204/grafana/
curl http://localhost:3001/
```

### 2. Prometheus 접속

#### 브라우저에서 접속

```
https://api.korion.io.kr/
```

- 예전 경로 `https://korion.io.kr/6s9ex74204/prometheus/` → `https://api.korion.io.kr/` 로 리다이렉트

#### cURL로 접속

```bash
# Prometheus 메인 페이지 (프로덕션)
curl -sI https://api.korion.io.kr/

# 메트릭 쿼리
curl "https://api.korion.io.kr/api/v1/query?query=up"
```

### 3. Grafana 루트 접속

Grafana는 **`https://dev.korion.io.kr/`** 에서 루트(`/`)로 제공됩니다.  
메인 도메인 `/6s9ex74204` 또는 `/6s9ex74204/grafana/` 로 접속하면 `https://dev.korion.io.kr/` 로 리다이렉트됩니다.

## 📝 사용 예시

### JavaScript (Fetch API)

```javascript
// Grafana 접속 (프로덕션: dev.korion.io.kr)
fetch('https://dev.korion.io.kr/')
  .then(response => response.text())
  .then(html => console.log(html));

// Prometheus 쿼리
fetch('https://api.korion.io.kr/api/v1/query?query=up')
  .then(response => response.json())
  .then(data => console.log(data));
```

### Python

```python
import requests

# Grafana 접속 (프로덕션: dev.korion.io.kr)
response = requests.get('https://dev.korion.io.kr/')
print(response.text)

# Prometheus 쿼리
response = requests.get(
    'https://api.korion.io.kr/api/v1/query',
    params={'query': 'up'}
)
print(response.json())
```

### Postman

1. **URL 입력**:
   ```
   GET https://dev.korion.io.kr/
   ```

## 🚨 에러 응답

### 502 Bad Gateway / 페이지가 안 열릴 때

Grafana/Prometheus 서버에 연결할 수 없거나, 브라우저에서 빈 화면/리다이렉트 무한 루프가 나올 때:

- **우리 서비스에 안 맞는 게 아니라** 대부분 **컨테이너 미기동·Nginx 경로·Grafana ROOT_URL 불일치** 때문입니다.
- **체크리스트·해결 순서**는 **[OPERATIONS.md § 5. Prometheus / Grafana 연동 및 안 열릴 때](./OPERATIONS.md#5-prometheus--grafana-연동-및-안-열릴-때)**를 참고하세요.

**간단 확인:**
- Docker Compose에서 Prometheus와 Grafana가 실행 중인지: `docker compose -f docker-compose.prod.yml ps`
- 접속 URL과 Grafana 환경 변수 `GF_SERVER_ROOT_URL`이 **완전히 동일**한지 (도메인·경로·슬래시 포함)
- 네트워크: Nginx ↔ Grafana/Prometheus 컨테이너 간 연결 확인

## 🔒 보안 권장사항

1. **Grafana 비밀번호 변경**
   - 기본 비밀번호(`admin`)는 반드시 변경
   - `.env`의 `GRAFANA_PASSWORD` 사용 권장

2. **HTTPS 사용**
   - 프로덕션 환경에서는 반드시 HTTPS 사용
   - API 키가 평문으로 전송되지 않도록 주의

4. **API 키 정기적 변경**
   - 보안을 위해 정기적으로 API 키 변경

## 📊 접근 가능한 경로

### Grafana (dev.korion.io.kr)
- `https://dev.korion.io.kr/` - Grafana 메인 페이지 (권장)
- `https://dev.korion.io.kr/api/*` - Grafana API
- `https://dev.korion.io.kr/public/*` - 공개 리소스
- `https://korion.io.kr/6s9ex74204/grafana/*` - 예전 경로 → `dev.korion.io.kr` 로 리다이렉트

### Prometheus (api.korion.io.kr)
- `https://api.korion.io.kr/` - Prometheus 메인 페이지
- `https://api.korion.io.kr/api/*` - Prometheus API
- `https://api.korion.io.kr/graph` - Prometheus Graph UI
- `https://api.korion.io.kr/targets` - 타겟 상태

## 🔍 문제 해결

### API 키가 작동하지 않을 때

1. **서버 재시작 확인**
   ```bash
   docker-compose -f docker-compose.prod.yml restart app
   ```

2. **환경 변수 확인**
   ```bash
   docker exec foxya-api env | grep MONITORING_API_KEY
   ```

3. **로그 확인**
   ```bash
   docker logs foxya-api | grep -i monitoring
   ```

### 브라우저에서 접속이 안 될 때

1. **쿼리 파라미터 사용**
   ```
   https://dev.korion.io.kr/?apiKey=your-secret-key
   ```

2. **브라우저 확장 프로그램 사용**
   - ModHeader (Chrome)
   - Header Editor (Firefox)

3. **Nginx 설정 확인**
   - `dev.korion.io.kr` → Grafana(3000), `api.korion.io.kr` → Prometheus(9090) 확인

## 📚 관련 문서

- [**서버 기반 모니터링 페이지 구성법**](./MONITORING_SERVER_SETUP.md) — Nginx·Docker·접속 주소 정리
- [모니터링 설정 가이드](./MONITORING_SETUP.md)
- [Docker 명령어 가이드](./DOCKER_COMMANDS.md)
- [환경 변수 설정](./ENV_CONFIGURATION.md)
