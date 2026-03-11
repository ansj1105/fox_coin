# PostgreSQL HA Assets

이 폴더는 Fox Coin의 `Primary + Standby` PostgreSQL 동기 복제를 위한 운영 템플릿과 스크립트를 담습니다.

구성 원칙:

1. 복제 전략은 `synchronous replication`
2. 2노드만 있을 때 자동 승격은 기본 비활성
3. 장애 시 `standby promote -> app db-proxy 전환` 순서로 처리
4. 앱은 항상 `db-proxy`만 바라봄
5. 백업과 마이그레이션은 항상 현재 Primary로 직결

포함 항목:

- `config/`: primary 설정 템플릿
- `scripts/render-primary-config.sh`: primary `postgresql.conf`/`pg_hba.conf` 생성
- `scripts/configure-primary-replication.sh`: replication user/slot 준비
- `scripts/bootstrap-standby.sh`: standby 초기화
- `scripts/check-replication.sh`: primary/standby 상태 점검
- `scripts/promote-standby.sh`: 장애 시 standby 승격

주의:

현재 prod처럼 PostgreSQL이 `127.0.0.1:5432`에만 바인딩되어 있으면 standby가 붙을 수 없습니다.
반드시 VPC private IP 기준으로 primary의 5432를 standby에서 접근 가능하게 열고, Security Group은 standby IP만 허용하세요.

임시 우회:

직접 `5432/tcp`를 열 수 없는 경우, standby 호스트에서 `SSH tunnel -> primary postgres container` 경로로 복제를 유지할 수 있습니다.
이때는 `standby/maintain-standby-tunnel.sh` 같은 감시 스크립트로 `foxya-postgres-standby` 컨테이너 PID 변경을 감지하고 tunnel을 자동 재부착해야 합니다.
컨테이너 재시작 후 tunnel을 수동으로 다시 붙이는 방식은 운영상 안전하지 않습니다.

원샷 배포:

- `scripts/deploy-cluster-all.sh`는 로컬 머신에서 primary/app 서버와 standby 서버를 같이 갱신합니다.
- primary 쪽은 현재 작업 트리를 tarball로 덮어쓰고 `docker compose build app && up -d --no-deps db-proxy app app2`를 수행합니다.
- standby 쪽은 `standby/docker-compose.standby.yml`과 tunnel watchdog/systemd를 같이 배포합니다.
- 이 스크립트는 기존 standby 데이터 볼륨과 replication bootstrap이 이미 준비된 상태를 전제로 합니다.
