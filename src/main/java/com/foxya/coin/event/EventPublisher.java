package com.foxya.coin.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.foxya.coin.common.utils.DateUtils;
import io.vertx.core.Future;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis를 사용한 이벤트 발행자
 */
@Slf4j
public class EventPublisher {
    
    private final RedisAPI redis;
    private final ObjectMapper objectMapper;
    
    public EventPublisher(RedisAPI redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 이벤트 발행 (Pub/Sub)
     */
    public Future<Void> publish(EventType eventType, Map<String, Object> payload) {
        Event event = Event.builder()
            .id(UUID.randomUUID().toString())
            .type(eventType)
            .payload(payload)
            .createdAt(DateUtils.now())
            .retryCount(0)
            .status("PENDING")
            .build();
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            
            // Redis Pub/Sub으로 발행
            return redis.publish(eventType.getChannel(), eventJson)
                .<Void>map(response -> {
                    log.info("Event published: {} to channel: {}", event.getId(), eventType.getChannel());
                    return null;
                })
                .onFailure(throwable -> log.error("Failed to publish event: {}", event.getId(), throwable));
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            return Future.failedFuture(e);
        }
    }
    
    /**
     * 이벤트 발행 및 Stream에 저장 (영속성 보장)
     */
    public Future<String> publishToStream(EventType eventType, Map<String, Object> payload) {
        Event event = Event.builder()
            .id(UUID.randomUUID().toString())
            .type(eventType)
            .payload(payload)
            .createdAt(DateUtils.now())
            .retryCount(0)
            .status("PENDING")
            .build();
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String streamKey = "events:" + eventType.getChannel();
            
            // Redis Stream에 추가 (XADD)
            return redis.xadd(List.of(
                streamKey,
                "*",  // auto-generate ID
                "event", eventJson
            )).map(response -> {
                String messageId = response.toString();
                log.info("Event added to stream: {} with ID: {}", event.getId(), messageId);
                
                // Pub/Sub으로도 발행 (실시간 처리용)
                redis.publish(eventType.getChannel(), eventJson);
                
                return messageId;
            }).onFailure(throwable -> log.error("Failed to add event to stream: {}", event.getId(), throwable));
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            return Future.failedFuture(e);
        }
    }
    
    /**
     * 지연 이벤트 발행 (Sorted Set 사용)
     */
    public Future<Void> publishDelayed(EventType eventType, Map<String, Object> payload, long delaySeconds) {
        Event event = Event.builder()
            .id(UUID.randomUUID().toString())
            .type(eventType)
            .payload(payload)
            .createdAt(DateUtils.now())
            .retryCount(0)
            .status("PENDING")
            .build();
        
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String delayedKey = "delayed:events";
            long executeAt = System.currentTimeMillis() + (delaySeconds * 1000);
            
            // Sorted Set에 추가 (score = 실행 시간)
            return redis.zadd(List.of(
                delayedKey,
                String.valueOf(executeAt),
                eventJson
            )).<Void>map(response -> {
                log.info("Delayed event added: {} to execute at: {}", event.getId(), executeAt);
                return null;
            }).onFailure(throwable -> log.error("Failed to add delayed event: {}", event.getId(), throwable));
        } catch (Exception e) {
            log.error("Failed to serialize delayed event", e);
            return Future.failedFuture(e);
        }
    }
}

