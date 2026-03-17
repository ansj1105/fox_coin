# DB Sync Replication Runbook

Fox Coin DB 클러스터링의 최종 선택은 `2노드 Active-Standby + PostgreSQL synchronous replication`입니다.

선택 이유:

1. 출금, 입금, 지갑, 잔액 데이터는 마지막 트랜잭션 유실을 허용하기 어렵습니다.
2. 비동기 복제는 Primary 장애 시 마지막 커밋 유실 가능성이 남습니다.
3. 2노드만 있을 때는 `동기 복제 + 수동 승격`이 가장 단순하고 안전합니다.

## 최종 토폴로지

```text
app/app2/tron-service
        |
        v
      db-proxy
        |
   +----+----+
   |         |
   v         v
Primary   Standby
```

기본 원칙:

1. 앱은 항상 `DB_HOST=db-proxy`
2. `DB_ADMIN_HOST`는 항상 현재 Primary
3. Primary는 `synchronous_commit=on`
4. Primary는 `synchronous_standby_names='FIRST 1 (...)'`
5. Standby에는 DDL/Flyway/백업을 직접 실행하지 않음

## 적용 전 확인

현재 primary가 Docker Compose에서 `127.0.0.1:5432:5432`처럼 localhost에만 바인딩되어 있으면 standby가 붙을 수 없습니다.

필수 선행조건:

1. Primary 호스트의 PostgreSQL 5432가 standby의 private IP에서 접근 가능해야 함
2. Security Group은 `standby private IP -> primary 5432`만 허용
3. app 서버는 `db-proxy`를 통해서만 DB에 붙어야 함

## 롤아웃 순서

### 1. Primary 준비

Primary 서버에서:

```bash
cd /var/www/fox_coin
chmod +x ops/db-ha/scripts/*.sh

APP_SUBNET_CIDR=172.31.0.0/16 \
STANDBY_CIDR=172.31.0.0/16 \
REPL_USER=replicator \
SYNC_STANDBY_NAME=db_standby \
./ops/db-ha/scripts/render-primary-config.sh
```

생성된 파일:

- `ops/db-ha/generated/postgresql.primary.conf`
- `ops/db-ha/generated/pg_hba.primary.conf`

이 설정을 Primary의 실제 `postgresql.conf`, `pg_hba.conf`에 반영하고 PostgreSQL을 재시작합니다.

다음으로 replication user와 slot 준비:

```bash
PGHOST=127.0.0.1 \
PGPORT=5432 \
PGUSER=foxya \
PGDATABASE=postgres \
REPL_USER=replicator \
REPL_PASSWORD='change-this' \
REPLICATION_SLOT=fox_coin_standby_1 \
./ops/db-ha/scripts/configure-primary-replication.sh
```

### 2. Standby 준비

Standby 서버에서 PostgreSQL 15를 설치하고 비어 있는 `PGDATA`를 준비한 뒤:

```bash
PRIMARY_HOST=10.0.10.10 \
PRIMARY_PORT=5432 \
PGDATA=/var/lib/postgresql/15/main \
REPL_USER=replicator \
REPL_PASSWORD='change-this' \
REPLICATION_SLOT=fox_coin_standby_1 \
STANDBY_APPLICATION_NAME=db_standby \
./ops/db-ha/scripts/bootstrap-standby.sh
```

그 후 standby PostgreSQL을 시작합니다.

### 3. 복제 확인

Primary:

```bash
PGHOST=127.0.0.1 PGPORT=5432 PGUSER=foxya PGDATABASE=postgres \
./ops/db-ha/scripts/check-replication.sh
```

정상 기대값:

1. `node_role=primary`
2. `pg_stat_replication.sync_state = sync`

Standby:

```bash
PGHOST=127.0.0.1 PGPORT=5432 PGUSER=foxya PGDATABASE=postgres \
./ops/db-ha/scripts/check-replication.sh
```

정상 기대값:

1. `node_role=standby`
2. `replay_delay`가 매우 작음

### 4. 앱 서버 전환

app 서버 `.env`:

```bash
DB_HOST=db-proxy
DB_PORT=5432
DB_NAME=coin_system_cloud
DB_USER=foxya
DB_PASSWORD=change-this
DB_PRIMARY_HOST=10.0.10.10
DB_PRIMARY_PORT=5432
DB_STANDBY_HOST=10.0.10.11
DB_STANDBY_PORT=5432
DB_ADMIN_MODE=network
DB_ADMIN_HOST=10.0.10.10
DB_ADMIN_PORT=5432
```

적용:

```bash
docker compose -f docker-compose.prod.yml up -d db-proxy app app2 tron-service tron-service2
```

현재 운영 기준으로 바로 대입 가능한 후보값은 아래입니다.

```bash
DB_HOST=db-proxy
DB_PORT=5432
DB_NAME=coin_system_cloud
DB_USER=foxya
DB_PRIMARY_HOST=postgres
DB_PRIMARY_PORT=5432
DB_STANDBY_HOST=172.31.31.109
DB_STANDBY_PORT=15432
DB_ADMIN_MODE=network
DB_ADMIN_HOST=127.0.0.1
DB_ADMIN_PORT=5432
REPL_USER=replicator
REPLICATION_SLOT=fox_coin_standby_1
SYNC_STANDBY_NAME=db_standby
```

여기서 아직 사람이 넣어야 하는 값은 `REPL_PASSWORD` 하나입니다.

현재 운영 standby는 별도 EC2의 Docker standby 컨테이너를 쓰므로:

1. standby 호스트 외부 공개 포트는 `15432`
2. standby 컨테이너 내부 PostgreSQL 포트는 `5432`
3. app 서버의 `db-proxy`는 `172.31.31.109:15432`를 standby 대상으로 둡니다.

## 현재 동기복제 상태

현재 운영 primary(`52.200.97.155`)에서 확인된 값은 아래와 같습니다.

```sql
show synchronous_commit;        -- on
show synchronous_standby_names; -- FIRST 1 (db_standby)
select application_name, sync_state
from pg_stat_replication;       -- db_standby | sync
```

뜻:

1. Primary는 standby가 같이 저장될 때까지 기다립니다.
2. 동기 대상으로 `db_standby`를 사용합니다.
3. `52.204.57.80` standby가 실제 sync 세션으로 붙어 있습니다.

즉 현재는 문서상 구조가 아니라 실제 운영에서도 "실시간으로 같이 받아 적는 상태"입니다.

## 장애조치

Primary 장애 시 standby에서:

```bash
PGDATA=/var/lib/postgresql/15/main ./ops/db-ha/scripts/promote-standby.sh
```

그 다음 app 서버에서:

1. `.env`의 `DB_PRIMARY_HOST`를 새 Primary로 변경
2. `.env`의 `DB_STANDBY_HOST`를 이전 Primary 복구 예정 주소로 변경
3. `.env`의 `DB_ADMIN_HOST`를 새 Primary로 변경
4. `docker compose -f docker-compose.prod.yml up -d db-proxy`

## 운영 메모

1. 2노드만으로 자동 failover를 켜면 split-brain 위험이 있습니다.
2. 자동 failover가 꼭 필요하면 witness를 추가하고 Patroni/repmgr로 확장하세요.
3. 가능하면 장기적으로는 AWS RDS Multi-AZ가 더 안전합니다.

## Failback 절차

failover 이후 원래 primary를 다시 standby로 붙일 때는 아래 순서를 따릅니다.

### 상황

- 새 primary: 현재 승격된 standby
- 새 standby: 예전 primary를 basebackup으로 다시 붙인 노드

### 순서

1. 새 primary에서 replication user / slot 준비

```bash
PGHOST=<new-primary-host> \
PGPORT=<new-primary-port> \
PGUSER=foxya \
PGDATABASE=postgres \
REPL_USER=replicator \
REPL_PASSWORD='<replication-password>' \
REPLICATION_SLOT=fox_coin_standby_1 \
./ops/db-ha/scripts/configure-primary-replication.sh
```

2. 새 primary `pg_hba.conf`에 예전 primary host를 replication 허용
3. 예전 primary postgres 데이터 볼륨을 비우고 basebackup으로 재구성
4. 예전 primary를 standby로 기동
5. 새 primary에서 `pg_stat_replication.sync_state = sync` 확인
6. app 서버 `.env`를 아래처럼 유지

```bash
DB_PRIMARY_HOST=<new-primary-host>
DB_PRIMARY_PORT=<new-primary-port>
DB_STANDBY_HOST=<rebuilt-local-standby-host-or-service>
DB_STANDBY_PORT=<rebuilt-local-standby-port>
DB_ADMIN_HOST=<new-primary-host>
DB_ADMIN_PORT=<new-primary-port>
```

### 현재 실제 운영 기준 예시

```bash
DB_PRIMARY_HOST=postgres
DB_PRIMARY_PORT=5432
DB_STANDBY_HOST=172.31.31.109
DB_STANDBY_PORT=15432
DB_ADMIN_HOST=127.0.0.1
DB_ADMIN_PORT=5432
```

핵심은:

1. failover는 "예비를 본체로 올리는 것"
2. failback은 "옛 본체를 새 예비로 다시 붙이는 것"
3. 둘 다 끝나야 다시 진짜 이중화가 완성됩니다.
