# 모니터링 페이지 접근 가이드

## 📋 개요

모니터링 페이지(Grafana, Prometheus)는 `/sys9x2k8m4p5` 경로를 통해 접근할 수 있으며, API 키 인증이 필요합니다.

## 🔑 API 키 설정

### 방법 1: 환경 변수 (권장)

```bash
# .env 파일에 추가
MONITORING_API_KEY=your-secret-monitoring-key-here

# 또는 직접 설정
export MONITORING_API_KEY=your-secret-monitoring-key-here
```

### 방법 2: config.json 설정

`src/main/resources/config.json` 파일의 각 환경 설정에 추가:

```json
{
  "local": {
    ...
    "monitoring": {
      "apiKey": "your-secret-monitoring-key-here"
    }
  },
  "prod": {
    ...
    "monitoring": {
      "apiKey": "your-secret-monitoring-key-here"
    }
  }
}
```

## 🔐 API 키 인증 방법

API 키는 다음 3가지 방법으로 전달할 수 있습니다:

### 1. 헤더: `X-API-Key` (권장)

```bash
curl -H "X-API-Key: your-secret-key" \
  http://localhost:8080/sys9x2k8m4p5/grafana/
```

### 2. 헤더: `Authorization: Bearer`

```bash
curl -H "Authorization: Bearer your-secret-key" \
  http://localhost:8080/sys9x2k8m4p5/grafana/
```

### 3. 쿼리 파라미터: `apiKey`

```bash
curl "http://localhost:8080/sys9x2k8m4p5/grafana/?apiKey=your-secret-key"
```

## 🌐 접속 방법

### 1. Grafana 접속

#### 브라우저에서 접속

1. **직접 URL 접속** (쿼리 파라미터 사용)
   ```
   http://your-domain/sys9x2k8m4p5/grafana/?apiKey=your-secret-key
   ```

2. **브라우저 확장 프로그램 사용**
   - ModHeader 같은 확장 프로그램으로 `X-API-Key` 헤더 추가
   - URL: `http://your-domain/sys9x2k8m4p5/grafana/`

#### cURL로 접속

```bash
# 기본 접속
curl -H "X-API-Key: your-secret-key" \
  http://localhost:8080/sys9x2k8m4p5/grafana/

# 특정 경로 접속
curl -H "X-API-Key: your-secret-key" \
  http://localhost:8080/sys9x2k8m4p5/grafana/api/dashboards/home
```

### 2. Prometheus 접속

#### 브라우저에서 접속

```
http://your-domain/sys9x2k8m4p5/prometheus/?apiKey=your-secret-key
```

#### cURL로 접속

```bash
# Prometheus 메인 페이지
curl -H "X-API-Key: your-secret-key" \
  http://localhost:8080/sys9x2k8m4p5/prometheus/

# 메트릭 쿼리
curl -H "X-API-Key: your-secret-key" \
  "http://localhost:8080/sys9x2k8m4p5/prometheus/api/v1/query?query=up"
```

### 3. 루트 경로 접속

`/sys9x2k8m4p5` 경로로 접속하면 자동으로 Grafana로 리다이렉트됩니다.

```bash
curl -H "X-API-Key: your-secret-key" \
  http://localhost:8080/sys9x2k8m4p5
```

## 📝 사용 예시

### JavaScript (Fetch API)

```javascript
// Grafana 접속
fetch('http://your-domain/sys9x2k8m4p5/grafana/', {
  headers: {
    'X-API-Key': 'your-secret-key'
  }
})
  .then(response => response.text())
  .then(html => console.log(html));

// Prometheus 쿼리
fetch('http://your-domain/sys9x2k8m4p5/prometheus/api/v1/query?query=up', {
  headers: {
    'X-API-Key': 'your-secret-key'
  }
})
  .then(response => response.json())
  .then(data => console.log(data));
```

### Python

```python
import requests

# Grafana 접속
headers = {'X-API-Key': 'your-secret-key'}
response = requests.get(
    'http://your-domain/sys9x2k8m4p5/grafana/',
    headers=headers
)
print(response.text)

# Prometheus 쿼리
response = requests.get(
    'http://your-domain/sys9x2k8m4p5/prometheus/api/v1/query',
    params={'query': 'up'},
    headers=headers
)
print(response.json())
```

### Postman

1. **Headers 탭**에 추가:
   - Key: `X-API-Key`
   - Value: `your-secret-key`

2. **URL 입력**:
   ```
   GET http://your-domain/sys9x2k8m4p5/grafana/
   ```

## 🚨 에러 응답

### 401 Unauthorized

API 키가 없거나 잘못된 경우:

```json
{
  "error": "Unauthorized",
  "message": "유효한 API 키가 필요합니다."
}
```

**해결 방법:**
- API 키가 올바른지 확인
- 헤더 또는 쿼리 파라미터가 올바르게 전달되었는지 확인
- 환경 변수 또는 config.json 설정 확인

### 502 Bad Gateway

Grafana/Prometheus 서버에 연결할 수 없는 경우:

```
Grafana 서버에 연결할 수 없습니다.
또는
Prometheus 서버에 연결할 수 없습니다.
```

**해결 방법:**
- Docker Compose에서 Prometheus와 Grafana가 실행 중인지 확인
- 네트워크 연결 확인

## 🔒 보안 권장사항

1. **강력한 API 키 사용**
   - 최소 32자 이상의 랜덤 문자열 사용
   - 예: `openssl rand -hex 32`

2. **환경 변수 사용 (프로덕션)**
   - config.json에 하드코딩하지 말고 환경 변수 사용
   - `.env` 파일은 `.gitignore`에 추가

3. **HTTPS 사용**
   - 프로덕션 환경에서는 반드시 HTTPS 사용
   - API 키가 평문으로 전송되지 않도록 주의

4. **API 키 정기적 변경**
   - 보안을 위해 정기적으로 API 키 변경

## 📊 접근 가능한 경로

### Grafana
- `/sys9x2k8m4p5/grafana/` - Grafana 메인 페이지
- `/sys9x2k8m4p5/grafana/api/*` - Grafana API
- `/sys9x2k8m4p5/grafana/public/*` - 공개 리소스

### Prometheus
- `/sys9x2k8m4p5/prometheus/` - Prometheus 메인 페이지
- `/sys9x2k8m4p5/prometheus/api/*` - Prometheus API
- `/sys9x2k8m4p5/prometheus/graph` - Prometheus Graph UI

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
   http://your-domain/sys9x2k8m4p5/grafana/?apiKey=your-secret-key
   ```

2. **브라우저 확장 프로그램 사용**
   - ModHeader (Chrome)
   - Header Editor (Firefox)

3. **Nginx 설정 확인**
   - `/sys9x2k8m4p5` 경로가 프록시되고 있는지 확인

## 📚 관련 문서

- [모니터링 설정 가이드](./MONITORING_SETUP.md)
- [Docker 명령어 가이드](./DOCKER_COMMANDS.md)
- [환경 변수 설정](./ENV_CONFIGURATION.md)

