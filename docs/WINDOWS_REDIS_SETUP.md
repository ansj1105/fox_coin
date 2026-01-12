# Windows에서 Redis 실행 가이드

## 🚀 방법 1: Docker 사용 (권장)

### 1. Docker Desktop 설치 확인
- Docker Desktop이 설치되어 있어야 합니다.
- 설치되어 있지 않다면: https://www.docker.com/products/docker-desktop/

### 2. Redis만 실행하기

#### PowerShell 또는 CMD에서 실행:
```powershell
# 프로젝트 루트 디렉토리로 이동
cd C:\Users\msi\IdeaProjects\fox_coin

# Redis만 실행
docker-compose up -d redis
```

#### 또는 Docker 명령어로 직접 실행:
```powershell
docker run -d --name foxya-coin-redis -p 6379:6379 redis:7-alpine
```

### 3. Redis 실행 확인
```powershell
# 컨테이너 상태 확인
docker ps

# Redis 연결 테스트
docker exec -it foxya-coin-redis redis-cli ping
# 응답: PONG 이 나오면 정상
```

### 4. Redis 중지
```powershell
# Redis 중지
docker stop foxya-coin-redis

# Redis 중지 및 삭제
docker rm -f foxya-coin-redis
```

---

## 🚀 방법 2: Docker Compose로 전체 서비스 실행

### 모든 서비스 실행 (PostgreSQL + Redis + App)
```powershell
cd C:\Users\msi\IdeaProjects\fox_coin
docker-compose up -d
```

### 특정 서비스만 실행
```powershell
# PostgreSQL과 Redis만 실행
docker-compose up -d postgres redis
```

### 서비스 상태 확인
```powershell
docker-compose ps
```

### 서비스 로그 확인
```powershell
# Redis 로그 확인
docker-compose logs -f redis

# 전체 로그 확인
docker-compose logs -f
```

---

## 🔧 방법 3: WSL2 사용 (고급)

### 1. WSL2 설치 확인
```powershell
wsl --list --verbose
```

### 2. WSL2에서 Redis 설치
```bash
# WSL2 Ubuntu 터미널에서
sudo apt update
sudo apt install redis-server -y

# Redis 시작
sudo service redis-server start

# Redis 상태 확인
sudo service redis-server status
```

### 3. Windows에서 접속
- WSL2의 IP 주소를 사용하거나
- `localhost:6379`로 접속 가능

---

## 🔧 방법 4: Windows용 Redis 포팅 버전 (비공식)

### Memurai 사용 (Redis 호환)
1. https://www.memurai.com/ 에서 다운로드
2. 설치 후 서비스 시작
3. 기본 포트: 6379

---

## ✅ Redis 연결 확인

### 방법 1: Docker exec 사용
```powershell
docker exec -it foxya-coin-redis redis-cli
# Redis CLI에 접속됨
# ping 입력하면 PONG 응답
```

### 방법 2: telnet 사용
```powershell
# Windows에서 telnet 활성화 (관리자 권한 필요)
dism /online /Enable-Feature /FeatureName:TelnetClient

# Redis 연결 테스트
telnet localhost 6379
```

### 방법 3: PowerShell로 테스트
```powershell
# Test-NetConnection 사용
Test-NetConnection -ComputerName localhost -Port 6379
```

---

## 🐛 문제 해결

### 포트 6379가 이미 사용 중인 경우
```powershell
# 포트 사용 확인
netstat -ano | findstr :6379

# 다른 포트로 실행
docker run -d --name foxya-coin-redis -p 6380:6379 redis:7-alpine
```

그리고 `config.json`에서 포트를 6380으로 변경:
```json
{
  "redis": {
    "host": "localhost",
    "port": 6380
  }
}
```

### Docker가 실행되지 않는 경우
1. Docker Desktop이 실행 중인지 확인
2. Windows에서 WSL2 기능이 활성화되어 있는지 확인
3. Docker Desktop 재시작

### Redis 연결 오류가 계속되는 경우
```powershell
# Redis 컨테이너 재시작
docker restart foxya-coin-redis

# Redis 로그 확인
docker logs foxya-coin-redis

# 컨테이너 삭제 후 재생성
docker rm -f foxya-coin-redis
docker run -d --name foxya-coin-redis -p 6379:6379 redis:7-alpine
```

---

## 📝 빠른 시작 스크립트

### start-redis.ps1 (PowerShell 스크립트)
```powershell
# start-redis.ps1
Write-Host "🚀 Starting Redis..." -ForegroundColor Green

# 기존 컨테이너가 있으면 제거
docker rm -f foxya-coin-redis 2>$null

# Redis 실행
docker run -d --name foxya-coin-redis -p 6379:6379 redis:7-alpine

# 잠시 대기
Start-Sleep -Seconds 3

# 연결 테스트
Write-Host "📊 Testing Redis connection..." -ForegroundColor Yellow
$result = docker exec foxya-coin-redis redis-cli ping
if ($result -eq "PONG") {
    Write-Host "✅ Redis is running successfully!" -ForegroundColor Green
} else {
    Write-Host "❌ Redis connection failed" -ForegroundColor Red
}

# 상태 확인
docker ps --filter "name=foxya-coin-redis"
```

### 사용 방법:
```powershell
.\start-redis.ps1
```

---

## 💡 개발 환경 설정

### 로컬 개발용 (Docker Compose)
```powershell
# 프로젝트 루트에서
docker-compose up -d postgres redis
```

### config.json 설정 확인
```json
{
  "local": {
    "redis": {
      "mode": "standalone",
      "host": "localhost",
      "port": 6379,
      "password": "",
      "maxPoolSize": 8,
      "maxPoolWaiting": 32
    }
  }
}
```

---

## 📚 추가 리소스

- Docker Desktop: https://www.docker.com/products/docker-desktop/
- Redis 공식 문서: https://redis.io/docs/
- Docker Compose 문서: https://docs.docker.com/compose/

