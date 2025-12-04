#!/bin/bash

# ==========================================
# Foxya Coin Service - 배포 스크립트
# ==========================================

set -e  # 에러 발생 시 스크립트 중단

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 변수 설정
PROJECT_NAME="fox_coin"
DEPLOY_DIR="/var/www/${PROJECT_NAME}"
REPO_URL="${REPO_URL:-git@github.com:your-org/foxya-coin-service.git}"
BRANCH="${BRANCH:-main}"
COMPOSE_FILE="docker-compose.prod.yml"

# 사용법 출력
usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  setup       - 초기 서버 설정 (첫 배포 시)"
    echo "  deploy      - 애플리케이션 배포"
    echo "  update      - 코드 업데이트 및 재배포"
    echo "  rollback    - 이전 버전으로 롤백"
    echo "  status      - 서비스 상태 확인"
    echo "  logs        - 로그 확인"
    echo "  stop        - 서비스 중지"
    echo "  restart     - 서비스 재시작"
    echo "  clean       - 미사용 Docker 리소스 정리"
    echo "  backup-db   - 데이터베이스 백업"
    echo ""
    exit 1
}

# 초기 서버 설정
setup() {
    log_info "서버 초기 설정 시작..."
    
    # 필수 패키지 설치
    log_info "필수 패키지 설치 중..."
    sudo yum update -y
    sudo yum install -y git docker
    
    # Docker 서비스 시작
    sudo systemctl start docker
    sudo systemctl enable docker
    sudo usermod -aG docker ec2-user
    
    # Docker Compose 설치
    log_info "Docker Compose 설치 중..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    
    # 프로젝트 디렉토리 생성
    mkdir -p "${DEPLOY_DIR}"
    mkdir -p "${DEPLOY_DIR}/nginx/conf.d"
    mkdir -p "${DEPLOY_DIR}/nginx/ssl"
    mkdir -p "${DEPLOY_DIR}/nginx/logs"
    mkdir -p "${DEPLOY_DIR}/logs"
    mkdir -p "${DEPLOY_DIR}/backups"
    
    log_success "서버 초기 설정 완료!"
    log_warning "새 터미널을 열거나 'newgrp docker' 실행 후 deploy 명령을 실행하세요."
}

# 배포
deploy() {
    log_info "배포 시작..."
    
    cd "${DEPLOY_DIR}"
    
    # .env 파일 확인
    if [ ! -f ".env" ]; then
        log_error ".env 파일이 없습니다. .env.example을 참고하여 생성하세요."
        exit 1
    fi
    
    # 이전 버전 백업 (롤백용)
    if [ -f "${COMPOSE_FILE}" ]; then
        BACKUP_TAG=$(date +%Y%m%d_%H%M%S)
        log_info "현재 이미지 백업 중... (tag: ${BACKUP_TAG})"
        docker tag foxya-coin-api:latest foxya-coin-api:backup_${BACKUP_TAG} 2>/dev/null || true
    fi
    
    # 최신 코드 가져오기
    if [ -d ".git" ]; then
        log_info "최신 코드 가져오는 중..."
        git fetch origin
        git checkout ${BRANCH}
        git pull origin ${BRANCH}
    else
        log_info "저장소 클론 중..."
        git clone -b ${BRANCH} ${REPO_URL} .
    fi
    
    # Docker 이미지 빌드
    log_info "Docker 이미지 빌드 중..."
    docker-compose -f ${COMPOSE_FILE} build --no-cache app
    
    # 서비스 시작/재시작
    log_info "서비스 시작 중..."
    docker-compose -f ${COMPOSE_FILE} up -d
    
    # 헬스체크
    log_info "헬스체크 대기 중... (60초)"
    sleep 10
    
    for i in {1..10}; do
        if curl -sf http://localhost:8080/health > /dev/null; then
            log_success "배포 완료! 서비스가 정상 작동 중입니다."
            docker-compose -f ${COMPOSE_FILE} ps
            exit 0
        fi
        log_info "헬스체크 대기 중... ($i/10)"
        sleep 5
    done
    
    log_error "헬스체크 실패! 로그를 확인하세요."
    docker-compose -f ${COMPOSE_FILE} logs --tail=50 app
    exit 1
}

# 업데이트
update() {
    log_info "업데이트 시작..."
    
    cd "${DEPLOY_DIR}"
    
    # 최신 코드 가져오기
    git fetch origin
    git checkout ${BRANCH}
    git pull origin ${BRANCH}
    
    # 이전 버전 백업
    BACKUP_TAG=$(date +%Y%m%d_%H%M%S)
    docker tag foxya-coin-api:latest foxya-coin-api:backup_${BACKUP_TAG} 2>/dev/null || true
    
    # 앱만 재빌드 및 재시작 (Zero-downtime)
    log_info "앱 재빌드 중..."
    docker-compose -f ${COMPOSE_FILE} build app
    
    log_info "앱 재시작 중..."
    docker-compose -f ${COMPOSE_FILE} up -d --no-deps app
    
    # 헬스체크
    sleep 10
    if curl -sf http://localhost:8080/health > /dev/null; then
        log_success "업데이트 완료!"
    else
        log_error "업데이트 실패! 롤백을 고려하세요."
        exit 1
    fi
}

# 롤백
rollback() {
    log_info "롤백 시작..."
    
    cd "${DEPLOY_DIR}"
    
    # 백업 이미지 목록 확인
    BACKUPS=$(docker images foxya-coin-api --format "{{.Tag}}" | grep "backup_" | head -5)
    
    if [ -z "$BACKUPS" ]; then
        log_error "백업 이미지가 없습니다."
        exit 1
    fi
    
    echo "사용 가능한 백업:"
    echo "$BACKUPS"
    echo ""
    read -p "롤백할 버전 태그를 입력하세요: " ROLLBACK_TAG
    
    if [ -z "$ROLLBACK_TAG" ]; then
        log_error "태그를 입력하세요."
        exit 1
    fi
    
    # 롤백 실행
    docker tag foxya-coin-api:${ROLLBACK_TAG} foxya-coin-api:latest
    docker-compose -f ${COMPOSE_FILE} up -d --no-deps app
    
    log_success "롤백 완료!"
}

# 상태 확인
status() {
    log_info "서비스 상태:"
    cd "${DEPLOY_DIR}"
    docker-compose -f ${COMPOSE_FILE} ps
    
    echo ""
    log_info "헬스체크:"
    curl -s http://localhost:8080/health | jq . 2>/dev/null || curl -s http://localhost:8080/health
    
    echo ""
    log_info "리소스 사용량:"
    docker stats --no-stream $(docker-compose -f ${COMPOSE_FILE} ps -q) 2>/dev/null || true
}

# 로그 확인
logs() {
    cd "${DEPLOY_DIR}"
    SERVICE=${2:-app}
    docker-compose -f ${COMPOSE_FILE} logs -f --tail=100 ${SERVICE}
}

# 서비스 중지
stop() {
    log_info "서비스 중지 중..."
    cd "${DEPLOY_DIR}"
    docker-compose -f ${COMPOSE_FILE} down
    log_success "서비스 중지 완료!"
}

# 서비스 재시작
restart() {
    log_info "서비스 재시작 중..."
    cd "${DEPLOY_DIR}"
    docker-compose -f ${COMPOSE_FILE} restart
    log_success "서비스 재시작 완료!"
}

# Docker 리소스 정리
clean() {
    log_info "미사용 Docker 리소스 정리 중..."
    
    # 중지된 컨테이너 삭제
    docker container prune -f
    
    # 미사용 이미지 삭제 (최근 백업 제외)
    docker image prune -f
    
    # 미사용 볼륨 삭제 (주의!)
    read -p "미사용 볼륨도 삭제하시겠습니까? (y/N): " CONFIRM
    if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
        docker volume prune -f
    fi
    
    # 미사용 네트워크 삭제
    docker network prune -f
    
    log_success "정리 완료!"
    docker system df
}

# 데이터베이스 백업
backup_db() {
    log_info "데이터베이스 백업 시작..."
    
    cd "${DEPLOY_DIR}"
    BACKUP_FILE="backups/foxya_coin_$(date +%Y%m%d_%H%M%S).sql.gz"
    
    # .env에서 DB 정보 로드
    source .env
    
    docker-compose -f ${COMPOSE_FILE} exec -T postgres \
        pg_dump -U ${DB_USER} ${DB_NAME} | gzip > ${BACKUP_FILE}
    
    log_success "백업 완료: ${BACKUP_FILE}"
    
    # 7일 이상 된 백업 삭제
    find backups/ -name "*.sql.gz" -mtime +7 -delete
    log_info "7일 이상 된 백업 파일 삭제 완료"
}

# 메인 로직
case "${1}" in
    setup)
        setup
        ;;
    deploy)
        deploy
        ;;
    update)
        update
        ;;
    rollback)
        rollback
        ;;
    status)
        status
        ;;
    logs)
        logs "$@"
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    clean)
        clean
        ;;
    backup-db)
        backup_db
        ;;
    *)
        usage
        ;;
esac

