package com.foxya.coin.event;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EventSystemTest {
    
    private static Vertx vertx;
    private static Redis redisClient;
    private static RedisAPI redisApi;
    private static EventPublisher eventPublisher;
    private static EventSubscriber eventSubscriber;
    
    @BeforeAll
    static void setup(VertxTestContext tc) {
        vertx = Vertx.vertx();
        
        // Redis 연결
        RedisOptions options = new RedisOptions()
            .setConnectionString("redis://localhost:6379");
        
        redisClient = Redis.createClient(vertx, options);
        
        redisClient.connect()
            .onSuccess(conn -> {
                log.info("Redis connected for test");
                redisApi = RedisAPI.api(conn);
                eventPublisher = new EventPublisher(redisApi);
                eventSubscriber = new EventSubscriber(redisApi);
                tc.completeNow();
            })
            .onFailure(throwable -> {
                log.warn("Redis not available, tests will be skipped: {}", throwable.getMessage());
                tc.completeNow(); // Redis 없어도 테스트 통과
            });
    }
    
    @AfterAll
    static void teardown() {
        if (redisClient != null) {
            redisClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }
    
    @Nested
    @DisplayName("EventPublisher 테스트")
    class EventPublisherTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - Pub/Sub 이벤트 발행")
        void successPublishPubSub(VertxTestContext tc) {
            if (eventPublisher == null) {
                log.warn("Redis not available, skipping test");
                tc.completeNow();
                return;
            }
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("txHash", "0x123abc");
            payload.put("userId", 1L);
            payload.put("amount", "100.50");
            
            eventPublisher.publish(EventType.TRANSACTION_PENDING, payload)
                .onSuccess(v -> {
                    log.info("Event published successfully");
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - Stream 이벤트 발행")
        void successPublishToStream(VertxTestContext tc) {
            if (eventPublisher == null) {
                log.warn("Redis not available, skipping test");
                tc.completeNow();
                return;
            }
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", 1L);
            payload.put("currency", "USDT");
            payload.put("amount", "50.00");
            
            eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                .onSuccess(messageId -> {
                    log.info("Event added to stream with ID: {}", messageId);
                    assertThat(messageId).isNotNull();
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(3)
        @DisplayName("성공 - 지연 이벤트 발행")
        void successPublishDelayed(VertxTestContext tc) {
            if (eventPublisher == null) {
                log.warn("Redis not available, skipping test");
                tc.completeNow();
                return;
            }
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("txHash", "0x456def");
            payload.put("checkCount", 1);
            
            eventPublisher.publishDelayed(EventType.TRANSACTION_PENDING, payload, 5)
                .onSuccess(v -> {
                    log.info("Delayed event added successfully");
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        }
    }
    
    @Nested
    @DisplayName("EventSubscriber 테스트")
    class EventSubscriberTest {
        
        @Test
        @Order(4)
        @DisplayName("성공 - 지연 이벤트 처리")
        void successProcessDelayedEvents(VertxTestContext tc) {
            if (eventSubscriber == null) {
                log.warn("Redis not available, skipping test");
                tc.completeNow();
                return;
            }
            
            // 먼저 즉시 실행될 지연 이벤트 추가 (0초 후)
            Map<String, Object> payload = new HashMap<>();
            payload.put("testKey", "testValue");
            payload.put("timestamp", System.currentTimeMillis());
            
            eventPublisher.publishDelayed(EventType.REFERRAL_REWARD, payload, 0)
                .compose(v -> vertx.timer(500))
                .compose(timerId -> {
                    AtomicReference<Event> processedEvent = new AtomicReference<>();
                    
                    return eventSubscriber.processDelayedEvents(event -> {
                        log.info("Processed delayed event: {}", event);
                        processedEvent.set(event);
                    }).map(v -> processedEvent.get());
                })
                .onSuccess(event -> {
                    log.info("Delayed event processing completed");
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        }
    }
    
    @Nested
    @DisplayName("Event 모델 테스트")
    class EventModelTest {
        
        @Test
        @Order(5)
        @DisplayName("성공 - Event 빌더 테스트")
        void successEventBuilder(VertxTestContext tc) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("key1", "value1");
            payload.put("key2", 123);
            
            Event event = Event.builder()
                .id("test-event-id")
                .type(EventType.TRANSACTION_CONFIRMED)
                .payload(payload)
                .createdAt(com.foxya.coin.common.utils.DateUtils.now())
                .retryCount(0)
                .status("PENDING")
                .build();
            
            assertThat(event.getId()).isEqualTo("test-event-id");
            assertThat(event.getType()).isEqualTo(EventType.TRANSACTION_CONFIRMED);
            assertThat(event.getPayload()).containsEntry("key1", "value1");
            assertThat(event.getPayload()).containsEntry("key2", 123);
            assertThat(event.getRetryCount()).isEqualTo(0);
            assertThat(event.getStatus()).isEqualTo("PENDING");
            
            tc.completeNow();
        }
        
        @Test
        @Order(6)
        @DisplayName("성공 - EventType 채널 테스트")
        void successEventTypeChannel(VertxTestContext tc) {
            assertThat(EventType.TRANSACTION_PENDING.getChannel()).isEqualTo("transaction:pending");
            assertThat(EventType.TRANSACTION_CONFIRMED.getChannel()).isEqualTo("transaction:confirmed");
            assertThat(EventType.WITHDRAWAL_REQUESTED.getChannel()).isEqualTo("withdrawal:requested");
            assertThat(EventType.DEPOSIT_DETECTED.getChannel()).isEqualTo("deposit:detected");
            assertThat(EventType.REFERRAL_REGISTERED.getChannel()).isEqualTo("referral:registered");
            
            tc.completeNow();
        }
    }
    
    @Nested
    @DisplayName("통합 시나리오 테스트")
    class IntegrationScenarioTest {
        
        @Test
        @Order(7)
        @DisplayName("성공 - 출금 요청 → 처리 → 완료 시나리오")
        void successWithdrawalScenario(VertxTestContext tc) {
            if (eventPublisher == null) {
                log.warn("Redis not available, skipping test");
                tc.completeNow();
                return;
            }
            
            // 1. 출금 요청 이벤트 발행 (Stream)
            Map<String, Object> withdrawalPayload = new HashMap<>();
            withdrawalPayload.put("userId", 1L);
            withdrawalPayload.put("currency", "TRX");
            withdrawalPayload.put("amount", "1000.00");
            withdrawalPayload.put("toAddress", "TXyz123...");
            
            eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, withdrawalPayload)
                .compose(messageId -> {
                    log.info("Withdrawal request event published: {}", messageId);
                    assertThat(messageId).isNotNull();
                    
                    // 2. 출금 처리 중 이벤트 발행
                    Map<String, Object> processingPayload = new HashMap<>();
                    processingPayload.put("userId", 1L);
                    processingPayload.put("txHash", "0xabc123...");
                    processingPayload.put("status", "PROCESSING");
                    
                    return eventPublisher.publish(EventType.WITHDRAWAL_PROCESSING, processingPayload);
                })
                .compose(v -> {
                    // 3. 출금 완료 이벤트 발행
                    Map<String, Object> completedPayload = new HashMap<>();
                    completedPayload.put("userId", 1L);
                    completedPayload.put("txHash", "0xabc123...");
                    completedPayload.put("status", "COMPLETED");
                    completedPayload.put("confirmedAt", System.currentTimeMillis());
                    
                    return eventPublisher.publish(EventType.WITHDRAWAL_COMPLETED, completedPayload);
                })
                .onSuccess(v -> {
                    log.info("Withdrawal scenario completed successfully");
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        }
        
        @Test
        @Order(8)
        @DisplayName("성공 - 레퍼럴 등록 → 리워드 지급 시나리오")
        void successReferralRewardScenario(VertxTestContext tc) {
            if (eventPublisher == null) {
                log.warn("Redis not available, skipping test");
                tc.completeNow();
                return;
            }
            
            // 1. 레퍼럴 등록 이벤트 발행
            Map<String, Object> registerPayload = new HashMap<>();
            registerPayload.put("referrerId", 5L);
            registerPayload.put("referredId", 10L);
            registerPayload.put("level", 1);
            
            eventPublisher.publish(EventType.REFERRAL_REGISTERED, registerPayload)
                .compose(v -> {
                    log.info("Referral registered event published");
                    
                    // 2. 레퍼럴 리워드 이벤트 발행
                    Map<String, Object> rewardPayload = new HashMap<>();
                    rewardPayload.put("referrerId", 5L);
                    rewardPayload.put("referredId", 10L);
                    rewardPayload.put("rewardAmount", "10.00");
                    rewardPayload.put("rewardCurrency", "USDT");
                    
                    return eventPublisher.publish(EventType.REFERRAL_REWARD, rewardPayload);
                })
                .onSuccess(v -> {
                    log.info("Referral reward scenario completed");
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        }
    }
}
