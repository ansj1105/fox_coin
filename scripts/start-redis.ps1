# Windows용 Redis 시작 스크립트
# 사용법: .\scripts\start-redis.ps1

Write-Host "🚀 Starting Redis..." -ForegroundColor Green

# 기존 컨테이너가 있으면 제거
Write-Host "🧹 Cleaning up existing Redis container..." -ForegroundColor Yellow
docker rm -f foxya-coin-redis 2>$null

# Redis 실행
Write-Host "📦 Starting Redis container..." -ForegroundColor Yellow
docker run -d --name foxya-coin-redis -p 6379:6379 redis:7-alpine

# 잠시 대기
Write-Host "⏳ Waiting for Redis to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# 연결 테스트
Write-Host "📊 Testing Redis connection..." -ForegroundColor Yellow
$result = docker exec foxya-coin-redis redis-cli ping 2>$null
if ($result -eq "PONG") {
    Write-Host "✅ Redis is running successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "📝 Connection Info:" -ForegroundColor Cyan
    Write-Host "   Host: localhost" -ForegroundColor White
    Write-Host "   Port: 6379" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "❌ Redis connection failed" -ForegroundColor Red
    Write-Host "   Please check Docker Desktop is running" -ForegroundColor Yellow
    exit 1
}

# 상태 확인
Write-Host "📊 Container Status:" -ForegroundColor Cyan
docker ps --filter "name=foxya-coin-redis" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

Write-Host ""
Write-Host "💡 To stop Redis, run: docker stop foxya-coin-redis" -ForegroundColor Gray
Write-Host "💡 To remove Redis, run: docker rm -f foxya-coin-redis" -ForegroundColor Gray

