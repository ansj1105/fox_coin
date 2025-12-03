# Event System (Redis Pub/Sub + Streams)

## 📌 개요

Redis를 사용한 이벤트 기반 아키텍처로 블록체인 트랜잭션 및 API 요청을 비동기로 처리합니다.

## 🚀 주요 기능

### 1. **Pub/Sub** (실시간 이벤트)
- 빠른 실시간 이벤트 전달
- 메시지 영속성 없음
- 구독자가 없으면 메시지 손실

### 2. **Streams** (영속성 보장)
- 메시지 영속성 보장
- Consumer Group으로 분산 처리
- 재처리 가능

### 3. **Delayed Events** (지연 실행)
- Sorted Set 사용
- 특정 시간 후 실행
- 트랜잭션 재확인 등에 활용

## 📝 사용 예제

### 이벤트 발행

```java
// 1. Pub/Sub (실시간)
Map<String, Object> payload = new HashMap<>();
payload.put("txHash", "0x123...");
payload.put("userId", 1L);
payload.put("amount", "100.50");

eventPublisher.publish(EventType.TRANSACTION_PENDING, payload);

// 2. Stream (영속성 보장)
eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload);

// 3. 지연 이벤트 (30초 후 실행)
eventPublisher.publishDelayed(EventType.TRANSACTION_PENDING, payload, 30);
```

### 이벤트 구독

```java
// 1. Pub/Sub 구독
eventSubscriber.subscribe(EventType.TRANSACTION_CONFIRMED, event -> {
    log.info("Transaction confirmed: {}", event.getPayload());
    // 지갑 잔액 업데이트
});

// 2. Stream 구독 (Consumer Group)
eventSubscriber.consumeStream(
    EventType.WITHDRAWAL_REQUESTED,
    "withdrawal-processor",  // Consumer Group
    "worker-1",              // Consumer Name
    event -> {
        log.info("Processing withdrawal: {}", event.getPayload());
        // 출금 처리
    }
);

// 3. 지연 이벤트 처리
eventSubscriber.processDelayedEvents(event -> {
    log.info("Processing delayed event: {}", event.getPayload());
    // 트랜잭션 재확인
});
```

## 🎯 이벤트 타입

### 트랜잭션 이벤트
- `TRANSACTION_PENDING`: 트랜잭션 대기 중
- `TRANSACTION_CONFIRMED`: 트랜잭션 확인됨
- `TRANSACTION_FAILED`: 트랜잭션 실패

### 출금 이벤트
- `WITHDRAWAL_REQUESTED`: 출금 요청
- `WITHDRAWAL_PROCESSING`: 출금 처리 중
- `WITHDRAWAL_COMPLETED`: 출금 완료
- `WITHDRAWAL_FAILED`: 출금 실패

### 입금 이벤트
- `DEPOSIT_DETECTED`: 입금 감지
- `DEPOSIT_CONFIRMED`: 입금 확인

### 레퍼럴 이벤트
- `REFERRAL_REGISTERED`: 레퍼럴 등록
- `REFERRAL_REWARD`: 레퍼럴 리워드

## 🔧 실제 사용 시나리오

### 1. 출금 요청 처리

```java
// Service에서 이벤트 발행
public Future<Void> requestWithdrawal(Long userId, String currency, BigDecimal amount) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("userId", userId);
    payload.put("currency", currency);
    payload.put("amount", amount.toString());
    payload.put("status", "REQUESTED");
    
    // Stream에 저장 (영속성 보장)
    return eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload);
}

// EventVerticle에서 처리
private void handleWithdrawalRequested(Event event) {
    Long userId = ((Number) event.getPayload().get("userId")).longValue();
    String currency = (String) event.getPayload().get("currency");
    BigDecimal amount = new BigDecimal((String) event.getPayload().get("amount"));
    
    // 1. 잔액 확인
    // 2. 블록체인 트랜잭션 생성
    // 3. WITHDRAWAL_PROCESSING 이벤트 발행
    // 4. 트랜잭션 상태 확인을 위한 지연 이벤트 발행 (30초 후)
}
```

### 2. 트랜잭션 상태 확인

```java
// 트랜잭션 생성 후 30초 후 재확인
Map<String, Object> payload = new HashMap<>();
payload.put("txHash", txHash);
payload.put("userId", userId);

eventPublisher.publishDelayed(EventType.TRANSACTION_PENDING, payload, 30);

// 30초 후 자동 실행
private void handleTransactionPending(Event event) {
    String txHash = (String) event.getPayload().get("txHash");
    
    // 블록체인에서 트랜잭션 상태 확인
    // - 확인됨: TRANSACTION_CONFIRMED 발행
    // - 대기 중: 다시 30초 후 확인 (재귀)
    // - 실패: TRANSACTION_FAILED 발행
}
```

### 3. 레퍼럴 리워드 지급

```java
// ReferralService에서 이벤트 발행
public Future<Void> registerReferralCode(Long userId, String referralCode) {
    return referralRepository.createReferralRelation(pool, referrerId, userId, 1)
        .compose(relation -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("referrerId", referrerId);
            payload.put("referredId", userId);
            payload.put("level", 1);
            
            // 레퍼럴 등록 이벤트 발행
            return eventPublisher.publish(EventType.REFERRAL_REGISTERED, payload);
        });
}

// EventVerticle에서 처리
private void handleReferralRegistered(Event event) {
    Long referrerId = ((Number) event.getPayload().get("referrerId")).longValue();
    
    // 1. 레퍼럴 통계 업데이트
    // 2. 리워드 지급
    // 3. 알림 발송
}
```

## 🐳 Docker로 Redis 실행

```bash
# docker-compose로 실행
docker-compose up -d redis

# 또는 단독 실행
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

## 📊 Redis 모니터링

```bash
# Redis CLI 접속
docker exec -it foxya-coin-redis redis-cli

# Pub/Sub 채널 확인
PUBSUB CHANNELS

# Stream 확인
XLEN events:transaction:pending

# Consumer Group 확인
XINFO GROUPS events:withdrawal:requested

# 지연 이벤트 확인
ZRANGE delayed:events 0 -1 WITHSCORES
```

## ⚙️ 설정

`config.json`:
```json
{
  "redis": {
    "host": "localhost",
    "port": 6379,
    "password": ""
  }
}
```

