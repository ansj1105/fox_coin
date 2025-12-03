package com.foxya.coin.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * Redis를 사용한 이벤트 구독자
 */
@Slf4j
public class EventSubscriber {
    
    private final RedisAPI redis;
    private final ObjectMapper objectMapper;
    
    public EventSubscriber(RedisAPI redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 이벤트 구독 (Pub/Sub) - 별도의 연결 필요
     */
    public Future<Void> subscribe(Vertx vertx, Redis redisClient, EventType eventType, Consumer<Event> handler) {
        return redisClient.connect()
            .compose(conn -> {
                // 메시지 핸들러 등록
                conn.handler(message -> {
                    if (message != null && message.size() >= 3) {
                        String messageType = message.get(0).toString();
                        if ("message".equals(messageType)) {
                            String eventJson = message.get(2).toString();
                            try {
                                Event event = objectMapper.readValue(eventJson, Event.class);
                                log.info("Received event: {} from channel: {}", event.getId(), eventType.getChannel());
                                handler.accept(event);
                            } catch (Exception e) {
                                log.error("Failed to deserialize event", e);
                            }
                        }
                    }
                });
                
                // 구독
                return conn.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.SUBSCRIBE).arg(eventType.getChannel()));
            })
            .<Void>map(response -> {
                log.info("Subscribed to channel: {}", eventType.getChannel());
                return null;
            })
            .onFailure(throwable -> log.error("Failed to subscribe to channel: {}", eventType.getChannel(), throwable));
    }
    
    /**
     * Stream에서 이벤트 읽기 (Consumer Group 사용)
     */
    public Future<Void> consumeStream(EventType eventType, String consumerGroup, String consumerName, Consumer<Event> handler) {
        String streamKey = "events:" + eventType.getChannel();
        
        // Consumer Group 생성 (이미 존재하면 무시)
        return redis.xgroup(List.of("CREATE", streamKey, consumerGroup, "0", "MKSTREAM"))
            .recover(throwable -> {
                // 이미 존재하는 경우 무시
                log.debug("Consumer group already exists or error: {}", throwable.getMessage());
                return Future.succeededFuture();
            })
            .compose(v -> readFromStream(streamKey, consumerGroup, consumerName, handler));
    }
    
    private Future<Void> readFromStream(String streamKey, String consumerGroup, String consumerName, Consumer<Event> handler) {
        // XREADGROUP으로 메시지 읽기
        return redis.xreadgroup(List.of(
            "GROUP", consumerGroup, consumerName,
            "COUNT", "10",
            "BLOCK", "1000",
            "STREAMS", streamKey, ">"
        )).compose(response -> {
            if (response != null) {
                processStreamMessages(response, streamKey, consumerGroup, handler);
            }
            
            // 계속 읽기 (재귀)
            return readFromStream(streamKey, consumerGroup, consumerName, handler);
        }).onFailure(throwable -> {
            log.error("Failed to read from stream: {}", streamKey, throwable);
            // 에러 발생 시 재시도
            readFromStream(streamKey, consumerGroup, consumerName, handler);
        });
    }
    
    private void processStreamMessages(Response response, String streamKey, String consumerGroup, Consumer<Event> handler) {
        try {
            for (Response stream : response) {
                if (stream.size() >= 2) {
                    Response messages = stream.get(1);
                    for (Response message : messages) {
                        String messageId = message.get(0).toString();
                        Response fields = message.get(1);
                        
                        for (int i = 0; i < fields.size(); i += 2) {
                            String fieldName = fields.get(i).toString();
                            if ("event".equals(fieldName)) {
                                String eventJson = fields.get(i + 1).toString();
                                Event event = objectMapper.readValue(eventJson, Event.class);
                                
                                log.info("Processing event: {} from stream", event.getId());
                                handler.accept(event);
                                
                                // ACK 처리
                                redis.xack(List.of(streamKey, consumerGroup, messageId));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process stream messages", e);
        }
    }
    
    /**
     * 지연 이벤트 처리 (Sorted Set 폴링)
     */
    public Future<Void> processDelayedEvents(Consumer<Event> handler) {
        String delayedKey = "delayed:events";
        long now = System.currentTimeMillis();
        
        // 실행 시간이 된 이벤트 조회
        return redis.zrangebyscore(List.of(
            delayedKey,
            "0",
            String.valueOf(now),
            "LIMIT", "0", "10"
        )).compose(response -> {
            if (response != null && response.size() > 0) {
                for (Response item : response) {
                    try {
                        String eventJson = item.toString();
                        Event event = objectMapper.readValue(eventJson, Event.class);
                        
                        log.info("Processing delayed event: {}", event.getId());
                        handler.accept(event);
                        
                        // 처리 완료 후 제거
                        redis.zrem(List.of(delayedKey, eventJson));
                    } catch (Exception e) {
                        log.error("Failed to process delayed event", e);
                    }
                }
            }
            return Future.<Void>succeededFuture();
        }).onFailure(throwable -> log.error("Failed to process delayed events", throwable));
    }
}
