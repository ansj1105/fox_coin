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
