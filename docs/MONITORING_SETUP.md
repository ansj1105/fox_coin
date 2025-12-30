# 모니터링 설정 가이드

## 개요

Foxya Coin Service는 **Prometheus**와 **Grafana**를 사용하여 API 요청 수, 응답 시간, 에러율 등을 모니터링합니다.

## 아키텍처

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   API App   │────▶│  Prometheus  │────▶│   Grafana   │
│  /metrics   │     │  (수집/저장)  │     │  (시각화)   │
└─────────────┘     └──────────────┘     └─────────────┘
```

## 구성 요소

### 1. MetricsCollector
- **위치**: `src/main/java/com/foxya/coin/common/metrics/MetricsCollector.java`
- **기능**: HTTP 요청 메트릭 수집
  - 요청 수 (method, path, status code별)
  - 응답 시간 (method, path별)
  - 에러 수 (4xx, 5xx)

### 2. Prometheus
- **포트**: `9090` (로컬호스트만 접근 가능)
- **설정 파일**: `prometheus/prometheus.yml`
- **기능**: 메트릭 수집 및 저장

### 3. Grafana
- **포트**: `3001` (로컬호스트만 접근 가능)
- **기본 계정**: `admin` / `admin` (환경 변수로 변경 가능)
- **기능**: 메트릭 시각화

## 시작하기

### 1. 서비스 시작

```bash
# 모든 서비스 시작 (Prometheus, Grafana 포함)
docker-compose -f docker-compose.prod.yml up -d

# 특정 서비스만 시작
docker-compose -f docker-compose.prod.yml up -d prometheus grafana
```

### 2. 메트릭 확인

#### API 메트릭 엔드포인트
```bash
# 직접 접근 (컨테이너 내부)
curl http://localhost:8080/metrics

# Nginx를 통한 접근
curl http://your-domain/metrics
```

#### Prometheus UI
- **URL**: http://localhost:9090
- **예시 쿼리**:
  ```promql
  # 전체 요청 수
  http_requests_total
  
  # 응답 시간 (평균)
  rate(http_request_duration_seconds_sum[5m]) / rate(http_request_duration_seconds_count[5m])
  
  # 에러율
  rate(http_errors_total[5m]) / rate(http_requests_total[5m])
  
  # 특정 엔드포인트 요청 수
  http_requests_by_method_path_status{path="/api/v1/missions"}
  ```

#### Grafana UI
- **URL**: http://localhost:3001
- **로그인**: `admin` / `admin` (또는 `.env`의 `GRAFANA_PASSWORD`)
- **데이터 소스**: 자동으로 Prometheus가 설정됨

### 3. 대시보드 생성

Grafana에서 다음 메트릭을 모니터링할 수 있습니다:

#### 주요 메트릭
1. **HTTP 요청 수**
   - `http_requests_total` - 전체 요청 수
   - `http_requests_by_method_path_status` - method, path, status별 요청 수

2. **응답 시간**
   - `http_request_duration_seconds` - 요청 처리 시간

3. **에러율**
   - `http_errors_total` - 전체 에러 수
   - `rate(http_errors_total[5m]) / rate(http_requests_total[5m])` - 에러율

#### 예시 대시보드 패널

**1. 요청 수 (Requests per Second)**
```promql
sum(rate(http_requests_total[1m])) by (method)
```

**2. 평균 응답 시간 (Average Response Time)**
```promql
rate(http_request_duration_seconds_sum[5m]) / rate(http_request_duration_seconds_count[5m])
```

**3. 에러율 (Error Rate)**
```promql
sum(rate(http_errors_total[5m])) / sum(rate(http_requests_total[5m])) * 100
```

**4. 엔드포인트별 요청 수 (Top Endpoints)**
```promql
topk(10, sum(rate(http_requests_by_method_path_status[5m])) by (path))
```

**5. 상태 코드별 분포**
```promql
sum(rate(http_requests_by_method_path_status[5m])) by (status)
```

## 환경 변수

### Grafana 비밀번호 변경
`.env` 파일에 추가:
```bash
GRAFANA_PASSWORD=your_secure_password
```

## 데이터 보존

- **Prometheus**: 30일 (설정 파일에서 변경 가능)
- **Grafana**: 영구 저장 (볼륨에 저장)

## 문제 해결

### 메트릭이 보이지 않을 때

1. **API 서버 확인**
   ```bash
   curl http://localhost:8080/metrics
   ```

2. **Prometheus 타겟 확인**
   - http://localhost:9090/targets 접속
   - `foxya-api` 타겟이 `UP` 상태인지 확인

3. **로그 확인**
   ```bash
   docker logs foxya-api | grep -i metric
   docker logs foxya-prometheus
   docker logs foxya-grafana
   ```

### Prometheus가 메트릭을 수집하지 못할 때

1. **네트워크 확인**
   ```bash
   docker exec foxya-prometheus wget -O- http://app:8080/metrics
   ```

2. **설정 파일 확인**
   ```bash
   cat prometheus/prometheus.yml
   ```

## 보안 고려사항

- Prometheus와 Grafana는 기본적으로 로컬호스트(`127.0.0.1`)에서만 접근 가능
- 프로덕션 환경에서는 Nginx를 통해 인증 추가 권장
- `/metrics` 엔드포인트는 현재 인증 없이 접근 가능 (필요시 IP 제한 추가)

## 참고 자료

- [Prometheus 공식 문서](https://prometheus.io/docs/)
- [Grafana 공식 문서](https://grafana.com/docs/)
- [Micrometer 공식 문서](https://micrometer.io/docs)

