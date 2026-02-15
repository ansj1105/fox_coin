# Grafana 대시보드 구성법 (스텝 바이 스텝)

Grafana(https://dev.korion.io.kr/)에서 **API Overview** 대시보드를 처음부터 만드는 순서입니다.

---

## Step 0. 접속·로그인

1. 브라우저에서 **https://dev.korion.io.kr/** 접속
2. **Username**: `admin`  
   **Password**: `.env`의 `GRAFANA_PASSWORD` 또는 기본값 `admin`
3. 로그인 후 첫 화면(Home 또는 대시보드 목록)이 나오면 OK

---

## Step 1. 데이터 소스 확인

1. 왼쪽 사이드바에서 **☰ (햄버거 메뉴)** 클릭
2. **Connections** → **Data sources** 클릭
3. **Prometheus** 항목이 있고, **URL**이 `http://prometheus:9090` 인지 확인
4. **Save & test** 클릭 → "Data source is working" 나오면 OK  
   - 안 나오면: Prometheus 컨테이너가 떠 있는지, 같은 Docker 네트워크인지 확인

---

## Step 2. 새 대시보드 만들기

1. 왼쪽 사이드바 **☰** → **Dashboards** 클릭
2. 오른쪽 위 **New** → **New dashboard** 클릭
3. **Add visualization** 버튼이 있는 빈 대시보드가 열림
4. 상단 **제목(Untitled)** 옆 연필 아이콘(✏️) 클릭 → 이름을 **API Overview** 로 입력 → **Save dashboard** (디스크 아이콘) 클릭
5. **Save dashboard** 창에서 이름 **API Overview**, 폴더는 그대로 두고 **Save** 클릭

---

## Step 3. 패널 1 – RPS (초당 요청 수)

1. **Add** → **Visualization** 클릭 (또는 이미 "Add visualization" 화면이면 그대로 진행)
2. **Choose data source** 에서 **Prometheus** 선택
3. **Query** 탭에서:
   - **Metric**: 비우고 아래 **Code** 모드로 전환(또는 Builder에서 쿼리 입력란에 직접 입력)
   - **PromQL** 입력:
   ```promql
   sum(rate(http_requests_by_method_path_status_total[1m]))
   ```
4. 오른쪽 **Panel options**:
   - **Title**: `RPS (초당 요청 수)` 입력
5. 상단 **Apply** 클릭
6. 오른쪽 위 **← Back to dashboard** (또는 X) 클릭해서 대시보드로 돌아감

---

## Step 4. 패널 2 – 에러율 (%)

1. 대시보드 화면에서 오른쪽 위 **Add** → **Visualization** 클릭
2. **Data source**: **Prometheus**
3. **PromQL** 입력:
   ```promql
   sum(rate(http_requests_by_method_path_status_total{status=~"4..|5.."}[5m])) / sum(rate(http_requests_by_method_path_status_total[5m])) * 100
   ```
4. 오른쪽에서 **Visualization** 을 **Stat** 또는 **Time series** 중 선택
5. **Panel options** → **Title**: `에러율 (%)`
6. **Apply** → **← Back to dashboard**

---

## Step 5. 패널 3 – 평균 응답 시간 (ms)

1. **Add** → **Visualization**
2. **Data source**: **Prometheus**
3. **PromQL** 입력:
   ```promql
   (sum(rate(http_request_duration_seconds_sum[5m])) by () / sum(rate(http_request_duration_seconds_count[5m])) by ()) * 1000
   ```
4. **Visualization**: **Time series**
5. **Panel options** → **Title**: `평균 응답 시간 (ms)`
6. **Apply** → **← Back to dashboard**

---

## Step 6. 패널 4 – 상태 코드별 요청

1. **Add** → **Visualization**
2. **Data source**: **Prometheus**
3. **PromQL** 입력:
   ```promql
   sum(rate(http_requests_by_method_path_status_total[5m])) by (status)
   ```
4. **Visualization**: **Time series**  
   - **Stack** 옵션을 켜면 영역이 쌓여서 보기 좋음 (오른쪽 시리즈 옵션에서)
5. **Panel options** → **Title**: `상태 코드별 요청`
6. **Apply** → **← Back to dashboard**

---

## Step 7. 패널 5 – 엔드포인트별 요청 Top 10

1. **Add** → **Visualization**
2. **Data source**: **Prometheus**
3. **PromQL** 입력:
   ```promql
   topk(10, sum(rate(http_requests_by_method_path_status_total[5m])) by (path))
   ```
4. **Visualization**: **Bar gauge** 또는 **Table**
5. **Panel options** → **Title**: `엔드포인트별 요청 Top 10`
6. **Apply** → **← Back to dashboard**

---

## Step 8. 레이아웃·저장

1. 각 패널 **제목 잡고 드래그**해서 원하는 위치로 이동
2. 패널 **우하단 모서리 드래그**해서 크기 조절
3. 오른쪽 위 **Save dashboard** (디스크 아이콘) 클릭 → **Save** 로 저장
4. 상단 **Time range** 를 **Last 6 hours** 또는 **Last 24 hours** 로 두고 보면 됨

---

## 요약 체크리스트

| Step | 내용 |
|------|------|
| 0 | https://dev.korion.io.kr/ 접속, admin 로그인 |
| 1 | Connections → Data sources → Prometheus `http://prometheus:9090` 확인 |
| 2 | Dashboards → New → New dashboard → 이름 "API Overview" 저장 |
| 3 | 패널 1: RPS → `sum(rate(http_requests_by_method_path_status_total[1m]))` |
| 4 | 패널 2: 에러율(%) → `sum(rate(...{status=~"4..\|5.."}[5m]))/sum(rate(...[5m]))*100` |
| 5 | 패널 3: 평균 응답시간(ms) → duration sum/count * 1000 |
| 6 | 패널 4: 상태코드별 요청 → `sum(rate(...)) by (status)` |
| 7 | 패널 5: 엔드포인트 Top 10 → `topk(10, sum(rate(...)) by (path))` |
| 8 | 드래그로 배치·크기 조절 후 Save dashboard |

---

## 추가로 넣기 좋은 시계열 패널

지금 있는 5개 말고, 아래도 **Time series** 로 넣어두면 유용함.

| 패널 이름 | 용도 | PromQL |
|-----------|------|--------|
| **메서드별 RPS** | GET / POST 등 비율 | `sum(rate(http_requests_by_method_path_status_total[5m])) by (method)` |
| **엔드포인트별 평균 응답 시간** | 어떤 API가 느린지 | `(sum(rate(http_request_duration_seconds_sum[5m])) by (path) / sum(rate(http_request_duration_seconds_count[5m])) by (path)) * 1000` |
| **4xx vs 5xx** | 에러 유형별 추이 | `sum(rate(http_requests_by_method_path_status_total{status=~"4.."}[5m]))` / `sum(rate(...{status=~"5.."}[5m]))` (시리즈 2개) |
| **에러 수 (1시간)** | 에러 절대값 추이 | `sum(increase(http_requests_by_method_path_status_total{status=~"4..|5.."}[1h]))` |
| **분당 요청 수** | 트래픽량 추이 | `sum(increase(http_requests_by_method_path_status_total[1m]))` |

- **메서드별 RPS**: GET/POST 비율이 시간대별로 어떻게 바뀌는지 보기 좋음.
- **엔드포인트별 응답 시간**: 특정 path가 느려지는 구간 잡기 좋음.
- **4xx vs 5xx**: 클라이언트 에러 vs 서버 에러 구분.
- **에러 수(1h)**: 알람용으로 “지금 1시간 동안 에러 몇 건” 보기.
- **분당 요청 수**: RPS와 비슷하지만 “분 단위 증가량”으로 보기.

---

## 메트릭이 안 나올 때

- **No data** 나오면:  
  1. 왼쪽 **Explore** → Data source **Prometheus** 선택  
  2. 쿼리란에 `http_requests_by_method_path_status_total` 입력 후 **Run query**  
  3. 우리 앱은 카운터 이름이 **`http_requests_by_method_path_status_total`** (끝에 `_total`). RPS/에러율 등은 위 PromQL 그대로 사용하면 됨.
- Prometheus 타겟이 **UP** 인지:  
  https://api.korion.io.kr/targets 에서 `foxya-api`(또는 앱 job) 확인
