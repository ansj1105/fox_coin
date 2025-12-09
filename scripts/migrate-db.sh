#!/bin/bash

# 데이터베이스 마이그레이션 실행 스크립트

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}데이터베이스 마이그레이션 실행 중...${NC}"

# 마이그레이션 파일 경로
MIGRATION_DIR="${1:-/var/www/coin_system_flyway/src/main/resources/db/migration}"

# DB 연결 정보
DB_USER="foxya"
DB_NAME="coin_system_cloud"

if [ ! -d "$MIGRATION_DIR" ]; then
    echo "Error: 마이그레이션 디렉토리를 찾을 수 없습니다: $MIGRATION_DIR"
    exit 1
fi

# SQL 파일을 순서대로 실행
for sql_file in $(ls -1 ${MIGRATION_DIR}/V*.sql | sort); do
    echo "실행 중: $(basename $sql_file)"
    docker exec -i foxya-postgres psql -U ${DB_USER} -d ${DB_NAME} < "$sql_file"
done

echo -e "${GREEN}마이그레이션 완료!${NC}"
