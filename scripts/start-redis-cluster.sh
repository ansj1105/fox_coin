#!/bin/bash

# Redis Cluster 시작 스크립트
# 사용법: ./scripts/start-redis-cluster.sh

set -e

echo "🚀 Starting Redis Cluster..."

# 기존 클러스터 데이터 정리 (선택적)
if [ "$1" == "--clean" ]; then
    echo "🧹 Cleaning up existing cluster data..."
    rm -rf redis-cluster/node-*/
    mkdir -p redis-cluster/node-{1,2,3,4,5,6}
fi

# Docker Compose로 클러스터 시작
docker-compose -f docker-compose.cluster.yml up -d

# 클러스터 초기화 대기
echo "⏳ Waiting for cluster initialization..."
sleep 10

# 클러스터 상태 확인
echo "📊 Checking cluster status..."
docker exec redis-node-1 redis-cli -p 7001 cluster info | head -5

echo ""
echo "✅ Redis Cluster is ready!"
echo ""
echo "📝 Cluster nodes:"
docker exec redis-node-1 redis-cli -p 7001 cluster nodes

echo ""
echo "🔗 Connection info:"
echo "   - Node 1: localhost:7001"
echo "   - Node 2: localhost:7002"
echo "   - Node 3: localhost:7003"
echo "   - Node 4: localhost:7004"
echo "   - Node 5: localhost:7005"
echo "   - Node 6: localhost:7006"
echo ""
echo "💡 To use cluster mode, set 'env': 'local-cluster' in config.json"

