package com.foxya.coin.verticle;

import com.foxya.coin.event.Event;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventSubscriber;
import com.foxya.coin.event.EventType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 처리 Verticle
 */
@Slf4j
public class EventVerticle extends AbstractVerticle {
    
    private Redis redisClient;
    private Redis subscriberClient;
    private RedisAPI redisApi;
    private EventPublisher eventPublisher;
    private EventSubscriber eventSubscriber;
    
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting EventVerticle...");
        
        JsonObject redisConfig = config().getJsonObject("redis", new JsonObject());
        
        // Redis 클라이언트 생성
        RedisOptions options = new RedisOptions()
            .setConnectionString("redis://" + redisConfig.getString("host", "localhost") + ":" + redisConfig.getInteger("port", 6379));
        
        String password = redisConfig.getString("password");
        if (password != null && !password.isEmpty()) {
            options.setPassword(password);
        }
        
        redisClient = Redis.createClient(vertx, options);
        subscriberClient = Redis.createClient(vertx, options); // Pub/Sub 전용
        
        redisClient.connect()
            .onSuccess(conn -> {
                log.info("Redis connected successfully");
                redisApi = RedisAPI.api(conn);
                
                // EventPublisher, EventSubscriber 초기화
                eventPublisher = new EventPublisher(redisApi);
                eventSubscriber = new EventSubscriber(redisApi);
                
                // 이벤트 구독 시작
                subscribeToEvents();
                
                // 지연 이벤트 처리 시작
                startDelayedEventProcessor();
                
                startPromise.complete();
            })
            .onFailure(throwable -> {
                log.error("Failed to connect to Redis", throwable);
                startPromise.fail(throwable);
            });
    }
    
    /**
     * 이벤트 구독
     */
    private void subscribeToEvents() {
        // 트랜잭션 이벤트 구독 (Pub/Sub)
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.TRANSACTION_PENDING, this::handleTransactionPending);
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.TRANSACTION_CONFIRMED, this::handleTransactionConfirmed);
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.TRANSACTION_FAILED, this::handleTransactionFailed);
        
        // 출금 이벤트 구독
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.WITHDRAWAL_REQUESTED, this::handleWithdrawalRequested);
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.WITHDRAWAL_COMPLETED, this::handleWithdrawalCompleted);
        
        // 입금 이벤트 구독
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.DEPOSIT_DETECTED, this::handleDepositDetected);
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.DEPOSIT_CONFIRMED, this::handleDepositConfirmed);
        
        // 레퍼럴 이벤트 구독
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.REFERRAL_REGISTERED, this::handleReferralRegistered);
        eventSubscriber.subscribe(vertx, subscriberClient, EventType.REFERRAL_REWARD, this::handleReferralReward);
        
        log.info("Event subscriptions initialized");
    }
    
    /**
     * 지연 이벤트 처리기 시작
     */
    private void startDelayedEventProcessor() {
        vertx.setPeriodic(5000, id -> {
            eventSubscriber.processDelayedEvents(this::handleDelayedEvent);
        });
        
        log.info("Delayed event processor started");
    }
    
    // ========== 이벤트 핸들러 ==========
    
    private void handleTransactionPending(Event event) {
        log.info("Handling TRANSACTION_PENDING: {}", event.getPayload());
        // TODO: 트랜잭션 상태 확인 로직
        // 블록체인에서 트랜잭션 상태 조회
        // 확인되면 TRANSACTION_CONFIRMED 이벤트 발행
    }
    
    private void handleTransactionConfirmed(Event event) {
        log.info("Handling TRANSACTION_CONFIRMED: {}", event.getPayload());
        // TODO: 지갑 잔액 업데이트
        // TODO: 사용자 알림
    }
    
    private void handleTransactionFailed(Event event) {
        log.info("Handling TRANSACTION_FAILED: {}", event.getPayload());
        // TODO: 실패 처리
        // TODO: 사용자 알림
    }
    
    private void handleWithdrawalRequested(Event event) {
        log.info("Handling WITHDRAWAL_REQUESTED: {}", event.getPayload());
        // TODO: 출금 요청 처리
        // TODO: 블록체인 트랜잭션 생성
    }
    
    private void handleWithdrawalCompleted(Event event) {
        log.info("Handling WITHDRAWAL_COMPLETED: {}", event.getPayload());
        // TODO: 출금 완료 처리
    }
    
    private void handleDepositDetected(Event event) {
        log.info("Handling DEPOSIT_DETECTED: {}", event.getPayload());
        // TODO: 입금 확인 대기
        // TODO: 확인 후 지갑 잔액 업데이트
    }
    
    private void handleDepositConfirmed(Event event) {
        log.info("Handling DEPOSIT_CONFIRMED: {}", event.getPayload());
        // TODO: 입금 확인 완료 처리
    }
    
    private void handleReferralRegistered(Event event) {
        log.info("Handling REFERRAL_REGISTERED: {}", event.getPayload());
        // TODO: 레퍼럴 통계 업데이트
        // TODO: 리워드 지급
    }
    
    private void handleReferralReward(Event event) {
        log.info("Handling REFERRAL_REWARD: {}", event.getPayload());
        // TODO: 리워드 지급 처리
    }
    
    private void handleDelayedEvent(Event event) {
        log.info("Handling DELAYED_EVENT: {} - {}", event.getType(), event.getPayload());
        // 이벤트 타입에 따라 처리
        if (event.getType() != null) {
            switch (event.getType()) {
                case TRANSACTION_PENDING -> handleTransactionPending(event);
                case WITHDRAWAL_REQUESTED -> handleWithdrawalRequested(event);
                default -> log.warn("Unknown delayed event type: {}", event.getType());
            }
        }
    }
    
    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Stopping EventVerticle...");
        
        if (redisClient != null) {
            redisClient.close();
        }
        if (subscriberClient != null) {
            subscriberClient.close();
        }
        
        stopPromise.complete();
    }
    
    public EventPublisher getEventPublisher() {
        return eventPublisher;
    }
    
    public EventSubscriber getEventSubscriber() {
        return eventSubscriber;
    }
}
