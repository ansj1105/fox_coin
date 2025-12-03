package com.foxya.coin.verticle;

import com.foxya.coin.event.Event;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventSubscriber;
import com.foxya.coin.event.EventType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisReplicas;
import lombok.extern.slf4j.Slf4j;

/**
 * 이벤트 처리 Verticle
 * 
 * Redis 구성 모드:
 * - standalone: 단일 Redis 인스턴스 (기본값)
 * - cluster: Redis Cluster (3+ Master 노드)
 * - sentinel: Redis Sentinel (Master-Slave + Failover)
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
        String mode = redisConfig.getString("mode", "standalone");
        
        // Redis 클라이언트 옵션 생성
        RedisOptions options = createRedisOptions(redisConfig, mode);
        
        redisClient = Redis.createClient(vertx, options);
        
        // Pub/Sub 전용 클라이언트 (Cluster 모드에서는 별도 연결 필요)
        RedisOptions subscriberOptions = createRedisOptions(redisConfig, mode);
        subscriberClient = Redis.createClient(vertx, subscriberOptions);
        
        redisClient.connect()
            .onSuccess(conn -> {
                log.info("Redis connected successfully (mode: {})", mode);
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
     * Redis 모드에 따른 옵션 생성
     */
    private RedisOptions createRedisOptions(JsonObject redisConfig, String mode) {
        RedisOptions options = new RedisOptions();
        
        String password = redisConfig.getString("password");
        if (password != null && !password.isEmpty()) {
            options.setPassword(password);
        }
        
        switch (mode) {
            case "cluster" -> {
                // Redis Cluster 모드
                options.setType(RedisClientType.CLUSTER);
                options.setUseReplicas(RedisReplicas.SHARE); // Replica 노드도 읽기에 사용
                
                // Cluster 노드들 추가
                JsonArray nodes = redisConfig.getJsonArray("nodes", new JsonArray());
                if (nodes.isEmpty()) {
                    // 기본 노드 설정 (로컬 개발용)
                    options.addConnectionString("redis://localhost:7001");
                    options.addConnectionString("redis://localhost:7002");
                    options.addConnectionString("redis://localhost:7003");
                    options.addConnectionString("redis://localhost:7004");
                    options.addConnectionString("redis://localhost:7005");
                    options.addConnectionString("redis://localhost:7006");
                } else {
                    for (int i = 0; i < nodes.size(); i++) {
                        String node = nodes.getString(i);
                        options.addConnectionString(node);
                    }
                }
                log.info("Redis Cluster mode configured with {} nodes", 
                    nodes.isEmpty() ? 6 : nodes.size());
            }
            
            case "sentinel" -> {
                // Redis Sentinel 모드
                options.setType(RedisClientType.SENTINEL);
                options.setMasterName(redisConfig.getString("masterName", "mymaster"));
                options.setRole(io.vertx.redis.client.RedisRole.MASTER);
                
                // Sentinel 노드들 추가
                JsonArray sentinels = redisConfig.getJsonArray("sentinels", new JsonArray());
                if (sentinels.isEmpty()) {
                    options.addConnectionString("redis://localhost:26379");
                } else {
                    for (int i = 0; i < sentinels.size(); i++) {
                        String sentinel = sentinels.getString(i);
                        options.addConnectionString(sentinel);
                    }
                }
                log.info("Redis Sentinel mode configured with master: {}", 
                    redisConfig.getString("masterName", "mymaster"));
            }
            
            default -> {
                // Standalone 모드 (기본)
                options.setType(RedisClientType.STANDALONE);
                String host = redisConfig.getString("host", "localhost");
                int port = redisConfig.getInteger("port", 6379);
                options.setConnectionString("redis://" + host + ":" + port);
                log.info("Redis Standalone mode configured: {}:{}", host, port);
            }
        }
        
        // 공통 옵션
        options.setMaxPoolSize(redisConfig.getInteger("maxPoolSize", 8));
        options.setMaxPoolWaiting(redisConfig.getInteger("maxPoolWaiting", 32));
        options.setPoolRecycleTimeout(redisConfig.getInteger("poolRecycleTimeout", 15000));
        
        return options;
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
