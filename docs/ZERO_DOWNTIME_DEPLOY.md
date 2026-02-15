# 도커 무중단 배포 가이드

## 현재 방식의 한계

`docker compose up -d --no-deps app` 은 **앱 컨테이너 1개를 새 이미지로 교체**합니다.  
교체 시 기존 컨테이너가 먼저 내려가고 새 컨테이너가 올라오는 동안 **몇 초~수십 초 끊김이 발생**할 수 있습니다.

---

## 방법 1: 다운타임 최소화 (구조 유지)

앱에 **healthcheck** 를 넣고, 배포 스크립트에서 **헬스 통과 후** 성공 처리하면, “완전 무중단”은 아니어도 끊김 구간을 짧게 줄일 수 있습니다.

### 1) docker-compose.prod.yml 에 healthcheck 추가

```yaml
  app:
    # ... 기존 설정 ...
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8080/health || exit 1"]
      interval: 10s
      timeout: 5s
      start_period: 40s
      retries: 3
```

(이미지에 `curl` 이 없으면 `wget -q -O-` 또는 JVM 기반 체크로 대체)

### 2) 배포 시

```bash
git pull origin develop
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d --no-deps app
# 새 컨테이너가 healthcheck 통과할 때까지 대기 후 로그/알림
```

이렇게 하면 “배포 실패” 판단은 정확해지지만, **한 번에 1개만 돌리므로 완전 무중단은 아닙니다.**

---

## 방법 2: 진짜 무중단 (앱 2대 + 한 대씩 순서대로 업데이트)

앱을 **2개 인스턴스**로 띄우고, Nginx가 둘 다로 요청을 보내게 한 뒤, **한 대씩만 순서대로** 업데이트하면 한 대가 재시작하는 동안에도 다른 한 대가 트래픽을 받아서 **무중단**이 됩니다.

### 1) Nginx upstream 에 두 서버 등록

**이미 반영됨.** `nginx/nginx.conf` 의 `upstream foxya_api` 에 `foxya-api-2:8080` 이 포함되어 있습니다.

### 2) 앱 2번째 인스턴스 추가 (docker-compose)

**이미 반영됨.** `docker-compose.prod.yml` 에 **app2** 서비스가 추가되어 있습니다.

### 3) 무중단 배포 순서

한 대만 바꿀 때마다 나머지 한 대가 트래픽을 받으므로, **한 대씩만** 재빌드·재시작합니다.

```bash
cd /var/www/fox_coin   # 실제 배포 경로
git pull origin develop

# 1) app( foxya-api ) 먼저 업데이트
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d --no-deps app
# 필요하면: 새 컨테이너 헬스 확인 후 다음 단계
sleep 15
curl -sf http://localhost:8080/health || true

# 2) app2( foxya-api-2 ) 업데이트
docker compose -f docker-compose.prod.yml build app2
docker compose -f docker-compose.prod.yml up -d --no-deps app2
```

- Nginx 는 `foxya-api` / `foxya-api-2` 둘 다로 요청을 보내므로, 한 컨테이너가 내려가 있어도 다른 한 대가 처리합니다.
- **첫 적용 시**: `docker compose -f docker-compose.prod.yml up -d` 로 app, app2 둘 다 띄우면 됩니다. (이미 Nginx·app2 설정 반영됨)

---

## 요약

| 방식 | 설명 |
|------|------|
| **방법 1** | healthcheck + 기존 1대 구조 유지 → 다운타임 **최소화** |
| **방법 2** | 앱 2대 + Nginx upstream 2개 + 한 대씩 순서 업데이트 → **무중단** |

원하면 방법 2용으로 `docker-compose.prod.yml` 에 app2 블록을 직접 추가해 두고, Nginx 만 위와 같이 수정해서 사용하면 됩니다.
