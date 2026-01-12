# Windows용 Redis 중지 스크립트
# 사용법: .\scripts\stop-redis.ps1

Write-Host "🛑 Stopping Redis..." -ForegroundColor Yellow

# Redis 중지
docker stop foxya-coin-redis 2>$null

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Redis stopped successfully" -ForegroundColor Green
} else {
    Write-Host "⚠️  Redis container not found or already stopped" -ForegroundColor Yellow
}

# 상태 확인
Write-Host ""
Write-Host "📊 Container Status:" -ForegroundColor Cyan
docker ps -a --filter "name=foxya-coin-redis" --format "table {{.Names}}\t{{.Status}}"

