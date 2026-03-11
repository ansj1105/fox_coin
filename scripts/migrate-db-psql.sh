#!/bin/bash

# PostgreSQL psql을 사용한 migration 실행 스크립트 (간단한 방법)

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}PostgreSQL psql을 사용한 데이터베이스 마이그레이션 실행 중...${NC}"

if [ -f ".env" ]; then
    set -a
    . ./.env
    set +a
fi

DB_ADMIN_MODE="${DB_ADMIN_MODE:-container}"
DB_CONTAINER="${DB_ADMIN_SERVICE:-postgres}"
DB_HOST="${DB_ADMIN_HOST:-${DB_PRIMARY_HOST:-${DB_HOST:-postgres}}}"
DB_PORT="${DB_ADMIN_PORT:-${DB_PRIMARY_PORT:-${DB_PORT:-5432}}}"
DB_USER="${DB_USER:-foxya}"
DB_NAME="${DB_NAME:-coin_system_cloud}"
DB_PASSWORD="${DB_PASSWORD:-}"

# 마이그레이션 파일 경로
MIGRATION_DIR="${1:-./src/main/resources/db/migration}"

if [ ! -d "$MIGRATION_DIR" ]; then
    echo -e "${RED}Error: 마이그레이션 디렉토리를 찾을 수 없습니다: $MIGRATION_DIR${NC}"
    exit 1
fi

# SQL 파일을 순서대로 실행 (V로 시작하는 파일만)
echo -e "${YELLOW}마이그레이션 파일 실행 중...${NC}"

for sql_file in $(ls -1 ${MIGRATION_DIR}/V*.sql | sort -V); do
    echo -e "${YELLOW}실행 중: $(basename $sql_file)${NC}"
    if [ "${DB_ADMIN_MODE}" = "container" ]; then
        docker-compose -f docker-compose.prod.yml exec -T "${DB_CONTAINER}" \
            psql -U "${DB_USER}" -d "${DB_NAME}" < "$sql_file"
    else
        docker run --rm -i \
            -e PGPASSWORD="${DB_PASSWORD}" \
            postgres:15-alpine \
            psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" < "$sql_file"
    fi
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}마이그레이션 실패: $(basename $sql_file)${NC}"
        exit 1
    fi
done

echo -e "${GREEN}마이그레이션 완료!${NC}"
