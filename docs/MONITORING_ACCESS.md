# 모니터링 페이지 접근 가이드

## 📋 개요

모니터링 페이지(Grafana, Prometheus)는 `/6s9ex74204` 경로를 통해 접근할 수 있습니다.

## 🔐 Grafana 로그인

- 기본 계정: `admin`
- 기본 비밀번호: `admin` (또는 `.env`의 `GRAFANA_PASSWORD`)

## 🌐 접속 방법

### 1. Grafana 접속

#### 브라우저에서 접속

1. **직접 URL 접속**
   ```
   http://your-domain/6s9ex74204/grafana/
   ```

#### cURL로 접속

```bash
# 기본 접속
curl http://localhost:8080/6s9ex74204/grafana/

# 특정 경로 접속
curl http://localhost:8080/6s9ex74204/grafana/api/dashboards/home
```

### 2. Prometheus 접속

#### 브라우저에서 접속

```
http://your-domain/6s9ex74204/prometheus/
```

#### cURL로 접속

```bash
# Prometheus 메인 페이지
curl http://localhost:8080/6s9ex74204/prometheus/

# 메트릭 쿼리
curl "http://localhost:8080/6s9ex74204/prometheus/api/v1/query?query=up"
```

### 3. 루트 경로 접속

`/6s9ex74204` 경로로 접속하면 자동으로 Grafana로 리다이렉트됩니다.

```bash
curl http://localhost:8080/6s9ex74204
```

## 📝 사용 예시

### JavaScript (Fetch API)

```javascript
// Grafana 접속
fetch('http://your-domain/6s9ex74204/grafana/')
  .then(response => response.text())
  .then(html => console.log(html));

// Prometheus 쿼리
fetch('http://your-domain/6s9ex74204/prometheus/api/v1/query?query=up')
  .then(response => response.json())
  .then(data => console.log(data));
```

### Python

```python
import requests

# Grafana 접속
response = requests.get('http://your-domain/6s9ex74204/grafana/')
print(response.text)

# Prometheus 쿼리
response = requests.get(
    'http://your-domain/6s9ex74204/prometheus/api/v1/query',
    params={'query': 'up'}
)
print(response.json())
```

### Postman

1. **URL 입력**:
   ```
   GET http://your-domain/6s9ex74204/grafana/
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

### Grafana
- `/6s9ex74204/grafana/` - Grafana 메인 페이지
- `/6s9ex74204/grafana/api/*` - Grafana API
- `/6s9ex74204/grafana/public/*` - 공개 리소스

### Prometheus
- `/6s9ex74204/prometheus/` - Prometheus 메인 페이지
- `/6s9ex74204/prometheus/api/*` - Prometheus API
- `/6s9ex74204/prometheus/graph` - Prometheus Graph UI

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
   http://your-domain/6s9ex74204/grafana/?apiKey=your-secret-key
   ```

2. **브라우저 확장 프로그램 사용**
   - ModHeader (Chrome)
   - Header Editor (Firefox)

3. **Nginx 설정 확인**
   - `/6s9ex74204` 경로가 프록시되고 있는지 확인

## 📚 관련 문서

- [모니터링 설정 가이드](./MONITORING_SETUP.md)
- [Docker 명령어 가이드](./DOCKER_COMMANDS.md)
- [환경 변수 설정](./ENV_CONFIGURATION.md)
