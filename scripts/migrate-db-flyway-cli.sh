#!/bin/bash

# Flyway CLI를 사용한 migration 실행 스크립트 (별도 Flyway 컨테이너 사용)

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}Flyway CLI를 사용한 데이터베이스 마이그레이션 실행 중...${NC}"

if [ -f ".env" ]; then
    set -a
    . ./.env
    set +a
fi

DB_HOST="${DB_ADMIN_HOST:-${DB_PRIMARY_HOST:-${DB_HOST:-postgres}}}"
DB_PORT="${DB_ADMIN_PORT:-${DB_PRIMARY_PORT:-${DB_PORT:-5432}}}"
DB_NAME="${DB_NAME:-coin_system_cloud}"
DB_USER="${DB_USER:-foxya}"
DB_PASSWORD="${DB_PASSWORD:-}"

# 마이그레이션 파일 경로 (호스트)
MIGRATION_DIR="${1:-${MIGRATION_DIR:-./src/main/resources/db/migration}}"

if [ ! -d "$MIGRATION_DIR" ]; then
    echo -e "${RED}Error: 마이그레이션 디렉토리를 찾을 수 없습니다: $MIGRATION_DIR${NC}"
    exit 1
fi

# Flyway Docker 이미지 사용
echo -e "${YELLOW}Flyway 컨테이너에서 migration 실행...${NC}"

docker run --rm \
    -v "$(cd "${MIGRATION_DIR}" && pwd):/flyway/sql" \
    flyway/flyway:10.4.1 \
    -url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} \
    -user=${DB_USER} \
    -password=${DB_PASSWORD} \
    -locations=filesystem:/flyway/sql \
    migrate

if [ $? -eq 0 ]; then
    echo -e "${GREEN}마이그레이션 완료!${NC}"
else
    echo -e "${RED}마이그레이션 실패!${NC}"
    exit 1
fi
