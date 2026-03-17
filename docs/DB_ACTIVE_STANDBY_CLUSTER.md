# DB Active-Standby Cluster

현재 `docker-compose.prod.yml`의 단일 `postgres` 컨테이너는 앱 2대 구성과 달리 DB가 단일 장애점(SPOF)입니다.  
DB 서버 자체가 죽는 상황을 막으려면 PostgreSQL을 최소 2개 서버로 분리하고, 앱은 항상 고정 엔드포인트(`db-proxy`)만 보도록 바꿔야 합니다.

이 프로젝트의 기본 선택은 `동기 복제(synchronous replication)`입니다. 실제 적용 절차는 [DB_SYNC_REPLICATION_RUNBOOK.md](./DB_SYNC_REPLICATION_RUNBOOK.md)를 따르세요.

## 목표 구조

```text
app/app2/tron-service
        |
        v
   db-proxy(HAProxy/Pgpool)
        |
   +----+----+
   |         |
   v         v
Primary   Standby
  |          |
  +----+-----+
       |
    replication
```

현재 운영에서 더 권장하는 배치는 아래입니다.

```text
instanceA (main/app)
  - foxya-api
  - foxya-api-2
  - foxya-tron-service
  - db-proxy
  - postgres-standby

instanceB (db)
  - postgres-primary
  - pg-primary-tunnel
```

핵심은 "스토리지는 각 인스턴스가 따로 가진다" 입니다.

1. instanceA, instanceB가 같은 EBS를 같이 쓰는 구조는 권장하지 않습니다.
2. PostgreSQL은 각 인스턴스의 로컬 디스크/EBS에 따로 저장하고 WAL replication으로만 맞춥니다.
3. 앱은 항상 `db-proxy`만 보고, 실제 primary/standby는 뒤에서만 바뀌게 둡니다.
4. 지금 운영은 이 구조의 축소판이며, app 서버 local postgres가 standby 역할을 합니다.

권장 서버 배치:

1. `db-primary` EC2: PostgreSQL Primary
2. `db-standby` EC2: PostgreSQL Standby
3. `db-witness` 또는 관리 노드: Patroni/repmgr witness 또는 etcd quorum
4. `app` EC2: 현재 fox_coin API, TRON service, `db-proxy`

같은 EC2 안에 Primary/Standby 컨테이너 2개를 올리는 것은 "프로세스 장애"만 줄일 뿐 "서버 장애"는 막지 못합니다.

## 이 레포에서 반영된 내용

1. 앱(Java)은 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` 환경변수로 DB 연결을 덮어쓸 수 있습니다.
2. 운영 compose에 `db-proxy` 서비스가 추가되어 앱이 직접 `postgres`를 보지 않고 `db-proxy`를 보도록 바뀌었습니다.
3. `tron-service`도 동일하게 `DB_HOST=db-proxy`를 사용합니다.
4. 백업/마이그레이션 스크립트는 `DB_ADMIN_*` 값을 사용해 Primary 직결 경로로 실행할 수 있습니다.

## 필수 운영 원칙

1. 앱 접속용: `DB_HOST=db-proxy`
2. 백업/DDL/Flyway용: `DB_ADMIN_HOST=<현재 Primary>`
3. Standby에는 절대 마이그레이션하지 않습니다.
4. Primary 승격은 Patroni/repmgr/RDS Multi-AZ 같은 외부 HA 매니저가 맡아야 합니다.
5. write path용 `db-proxy`는 `DB_PROXY_STANDBY_FALLBACK_ENABLED=false`를 기본으로 두고, standby는 승격 이후에만 새 primary로 붙입니다.

## 권장 환경 변수

```bash
DB_HOST=db-proxy
DB_PORT=5432
DB_NAME=coin_system_cloud
DB_USER=foxya
DB_PASSWORD=change-this
DB_POOL_SIZE=15

DB_PRIMARY_HOST=10.0.10.10
DB_PRIMARY_PORT=5432
DB_STANDBY_HOST=10.0.10.11
DB_STANDBY_PORT=5432

DB_ADMIN_MODE=network
DB_ADMIN_HOST=10.0.10.10
DB_ADMIN_PORT=5432
```

현재 운영 서버에서 확인된 후보값은 아래와 같습니다.

```bash
DB_PRIMARY_HOST=172.31.71.66
DB_PRIMARY_PORT=5432
DB_STANDBY_HOST=172.31.89.103
DB_STANDBY_PORT=15432
DB_ADMIN_MODE=network
DB_ADMIN_HOST=172.31.71.66
DB_ADMIN_PORT=5432
REPL_USER=replicator
REPLICATION_SLOT=fox_coin_standby_1
SYNC_STANDBY_NAME=db_standby
```

주의:

1. 예전 `.env`에 `DB_STANDBY_PORT=15432`가 들어가 있었다면 그 값은 app 서버의 `db-proxy` 바인딩 포트와 섞인 것입니다.
2. 현재 운영 standby는 `foxya-postgres-standby`를 호스트 `15432 -> 컨테이너 5432`로 공개합니다.
3. 그래서 app 서버 `db-proxy`가 standby를 볼 때는 `DB_STANDBY_PORT=15432`가 맞습니다.
4. standby 컨테이너 내부 PostgreSQL 자체 포트는 여전히 `5432`입니다.
5. `REPL_PASSWORD`만 아직 별도 시크릿으로 정해서 넣어야 합니다.

단일 컨테이너 임시운영에서는 아래처럼 둘 수 있습니다.

```bash
DB_PRIMARY_HOST=postgres
DB_STANDBY_HOST=postgres
DB_ADMIN_MODE=container
DB_ADMIN_SERVICE=postgres
```

## 장애조치 절차

자동 승격(Patroni/repmgr/RDS Multi-AZ)이 있는 경우:

1. Primary 장애 발생
2. HA 매니저가 Standby를 새 Primary로 승격
3. `db-proxy`가 새 Primary에 붙도록 `DB_PRIMARY_HOST`, `DB_STANDBY_HOST`를 맞춘 뒤 재기동
4. 앱은 `DB_HOST=db-proxy` 그대로 유지

다른 서버의 서비스가 `db-proxy`에 직접 붙어야 하면 `DB_PROXY_BIND_ADDRESS=0.0.0.0`로 바꾸고, Security Group은 필요한 소스 IP만 허용합니다.

수동 승격만 있는 경우:

1. Standby에서 `promote` 수행
2. `.env`의 `DB_PRIMARY_HOST`, `DB_STANDBY_HOST`, `DB_ADMIN_HOST` 갱신
3. `docker compose -f docker-compose.prod.yml up -d db-proxy`
4. `./scripts/deploy.sh backup-db` 또는 `psql -c "SELECT pg_is_in_recovery()"`로 Primary 여부 확인

## 백업 / 마이그레이션 경로

운영 중에는 아래 원칙을 고정합니다.

1. 백업은 항상 `DB_ADMIN_HOST=<현재 Primary>` 에서만 실행
2. Flyway/DDL도 항상 `DB_ADMIN_HOST=<현재 Primary>` 에서만 실행
3. Standby에는 절대 백업 restore 테스트 외 쓰기 작업을 하지 않음
4. 앱 연결은 계속 `DB_HOST=db-proxy`

현재 failover 이후 기준값 예시는 아래입니다.

```bash
DB_HOST=db-proxy
DB_PORT=5432
DB_PRIMARY_HOST=172.31.89.103
DB_PRIMARY_PORT=15432
DB_STANDBY_HOST=postgres
DB_STANDBY_PORT=5432
DB_ADMIN_HOST=172.31.89.103
DB_ADMIN_PORT=15432
```

즉 아주 쉽게 말하면:

- 앱은 `db-proxy`만 본다
- 백업/마이그레이션 도구는 "지금 실제 본체 DB"만 본다
- standby는 읽기/복제 대기만 한다

## 점검 명령

```bash
# 앱이 보는 프록시 상태
docker logs foxya-db-proxy --tail 100

# 현재 Primary 확인
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:15-alpine \
  psql -h "$DB_ADMIN_HOST" -p "$DB_ADMIN_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -c "SELECT pg_is_in_recovery() AS standby, now() AS checked_at"

# 백업
./scripts/deploy.sh backup-db
```

## 추천

가능하면 자체 Active-Standby보다 AWS RDS Multi-AZ 또는 Aurora PostgreSQL로 전환하는 편이 운영 리스크가 훨씬 낮습니다.  
직접 운영해야 한다면 최소한 `Primary + Standby + Witness + db-proxy` 4개 역할은 분리하세요.
