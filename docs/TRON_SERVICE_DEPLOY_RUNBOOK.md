# TRON Service Deploy Runbook

## 왜 docker-compose.yml 만으로 끝내지 않는가

`docker-compose.prod.yml` 은 런타임 선언 파일입니다.
여기에는 아래 내용이 들어갑니다.

- 어떤 컨테이너를 띄울지
- 어떤 이미지와 build context를 쓸지
- 어떤 env, volume, network 를 붙일지
- healthcheck 와 restart 정책

하지만 실제 운영 배포는 그 앞뒤 절차가 더 필요합니다.

- `/var/www/coin_publish` 최신 코드 동기화
- `fox_coin/.env` 에서 `LEDGER_EXPECTED_ISSUER`, `LEDGER_SHARED_HMAC_SECRET` 확인
- 필요한 서비스만 선택 재빌드
- 컨테이너 재기동 후 health 확인
- 실패 시 로그 확인과 롤백 판단

즉:

- `docker-compose.prod.yml`: 어떻게 실행할지
- `scripts/deploy-tron-service.sh`: 어떤 순서로 배포할지

실무에서는 이 둘을 분리하는 편이 더 안전합니다.

## 원샷 배포 진입점

```bash
cd /var/www/fox_coin
./scripts/deploy-tron-service.sh
```

또는 기존 배포 스크립트 진입점:

```bash
cd /var/www/fox_coin
./scripts/deploy.sh update-tron
```

두 명령은 같은 동작을 수행해야 합니다.

## 수행 순서

`deploy-tron-service.sh` 는 아래 순서로 동작합니다.

1. `/var/www/fox_coin/.env` 존재 확인
2. `/var/www/coin_publish` 소스 존재 확인
3. `LEDGER_EXPECTED_ISSUER`, `LEDGER_SHARED_HMAC_SECRET` 검증
4. `/var/www/coin_publish` 를 대상 브랜치 기준으로 `fetch + checkout + pull --rebase`
5. `foxya-tron-service:latest` 를 backup tag 로 보관 시도
6. `docker compose -f docker-compose.prod.yml up -d --build tron-service`
7. 필요 시 `tron-service2` 까지 같이 재배포
8. 각 컨테이너에서 `http://localhost:3000/health` 확인
9. `docker compose ps` 로 상태 출력

## 필수 환경 변수

`/var/www/fox_coin/.env` 에 아래 값을 넣어야 합니다.

```env
LEDGER_EXPECTED_ISSUER=korion
LEDGER_SHARED_HMAC_SECRET=<coin_manage 와 동일한 값>
TRON_SERVICE_PATH=/var/www/coin_publish
TRON_SERVICE_BRANCH=develop
```

`LEDGER_SHARED_HMAC_SECRET` 는 Git 에 넣지 말고 서버 `.env` 에만 둡니다.

## compose 가 책임지는 범위

`docker-compose.prod.yml` 은 아래만 책임집니다.

- `tron-service`, `tron-service2` 서비스 정의
- build context 를 `${TRON_SERVICE_PATH:-/var/www/coin_publish}` 로 지정
- `.env` 값을 컨테이너 runtime env 로 주입
- healthcheck 와 restart 정책 유지

즉 compose 만 실행하면 이미 준비된 소스를 컨테이너로 올릴 수는 있지만,
소스 pull, 운영 env 보정, 배포 순서 제어는 별도 스크립트가 맡아야 합니다.

## 롤백 기준

다음 중 하나면 롤백 검토 대상입니다.

- `tron-service` healthcheck 실패
- `tron-service2` healthcheck 실패
- `withdrawal worker` 로그에 `unexpected ledger contract issuer`
- `invalid ledger contract signature`
- `withdrawal status not executable`

## 운영 체크 명령

```bash
docker compose -f docker-compose.prod.yml ps tron-service tron-service2
docker compose -f docker-compose.prod.yml logs --tail=100 tron-service
docker compose -f docker-compose.prod.yml logs --tail=100 tron-service2
```
