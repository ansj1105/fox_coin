#!/bin/bash

# Redis Cluster 중지 스크립트
# 사용법: ./scripts/stop-redis-cluster.sh

echo "🛑 Stopping Redis Cluster..."

docker-compose -f docker-compose.cluster.yml down

echo "✅ Redis Cluster stopped!"

