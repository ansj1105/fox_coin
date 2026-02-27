package com.foxya.coin.retry;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 이메일·외부 API 실패 시 Redis 재시도 큐에 적재
 */
@Slf4j
public class RetryQueuePublisher {

    public static final String KEY_RETRY_EMAIL = "retry:email";
    public static final String KEY_RETRY_EMAIL_DLQ = "retry:email:dlq";
    public static final String KEY_RETRY_FCM_STREAM = "retry:fcm:stream";
    public static final String KEY_RETRY_FCM_DLQ_STREAM = "retry:fcm:dlq:stream";
    private static final String FIELD_JOB = "job";

    private final RedisAPI redis;

    public RetryQueuePublisher(RedisAPI redis) {
        this.redis = redis;
    }

    public record FcmRetryMessage(String messageId, FcmRetryJob job) { }

    /**
     * 이메일 재시도 작업을 큐에 적재
     */
    public Future<Void> enqueueEmailRetry(EmailRetryType type, String email, String codeOrPassword) {
        if (redis == null) {
            log.warn("Redis not available, skipping email retry enqueue. type={}, email={}", type, email);
            return Future.succeededFuture();
        }
        EmailRetryJob job = EmailRetryJob.builder()
            .type(type)
            .email(email)
            .codeOrPassword(codeOrPassword)
            .retryCount(0)
            .maxRetries(EmailRetryJob.DEFAULT_MAX_RETRIES)
            .createdAt(LocalDateTime.now())
            .build();
        return enqueueEmailRetry(job);
    }

    /**
     * 이메일 재시도 작업(재시도 카운트 포함)을 큐에 적재
     */
    public Future<Void> enqueueEmailRetry(EmailRetryJob job) {
        if (redis == null) {
            log.warn("Redis not available, skipping email retry enqueue. type={}, email={}", job.getType(), job.getEmail());
            return Future.succeededFuture();
        }
        String payload = job.toJson().encode();
        return redis.lpush(List.of(KEY_RETRY_EMAIL, payload))
            .<Void>map(ok -> {
                log.info("Email retry job enqueued. type={}, email={}, retryCount={}", job.getType(), job.getEmail(), job.getRetryCount());
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue email retry. type={}, email={}", job.getType(), job.getEmail(), throwable));
    }

    /**
     * 최종 실패 작업을 DLQ에 적재 (에러 처리·모니터링용)
     */
    public Future<Void> enqueueEmailDlq(EmailRetryJob job, String lastError) {
        if (redis == null) {
            log.error("Redis not available, cannot enqueue to DLQ. type={}, email={}, error={}", job.getType(), job.getEmail(), lastError);
            return Future.succeededFuture();
        }
        String payload = job.toJson().put("lastError", lastError).encode();
        return redis.lpush(List.of(KEY_RETRY_EMAIL_DLQ, payload))
            .<Void>map(ok -> {
                log.error("Email retry job moved to DLQ. type={}, email={}, retryCount={}, error={}", job.getType(), job.getEmail(), job.getRetryCount(), lastError);
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue email DLQ. type={}, email={}", job.getType(), job.getEmail(), throwable));
    }

    /**
     * 재시도 큐에서 작업 하나 꺼내기 (RPOP, 소비자용)
     */
    public Future<EmailRetryJob> popEmailRetry() {
        if (redis == null) {
            return Future.succeededFuture(null);
        }
        return redis.rpop(List.of(KEY_RETRY_EMAIL))
            .map(response -> {
                if (response == null || response.toString() == null || response.toString().isEmpty()) {
                    return null;
                }
                try {
                    JsonObject json = new JsonObject(response.toString());
                    return EmailRetryJob.fromJson(json);
                } catch (Exception e) {
                    log.warn("Failed to parse email retry job from queue: {}", response.toString(), e);
                    return null;
                }
            })
            .onFailure(throwable -> log.error("Failed to pop email retry job", throwable));
    }

    /**
     * FCM 재시도 Consumer Group 생성 (이미 있으면 성공 처리)
     */
    public Future<Void> ensureFcmRetryConsumerGroup(String consumerGroup) {
        if (redis == null) {
            return Future.succeededFuture();
        }
        return redis.xgroup(List.of("CREATE", KEY_RETRY_FCM_STREAM, consumerGroup, "0", "MKSTREAM"))
            .recover(throwable -> {
                String msg = throwable.getMessage();
                if (msg != null && msg.contains("BUSYGROUP")) {
                    return Future.succeededFuture((Response) null);
                }
                return Future.failedFuture(throwable);
            })
            .mapEmpty();
    }

    /**
     * FCM 재시도 작업을 Redis Stream에 적재
     */
    public Future<Void> enqueueFcmRetry(FcmRetryJob job) {
        if (redis == null) {
            log.warn("Redis not available, skipping fcm retry enqueue. userId={}, token={}", job.getUserId(), tokenPrefix(job.getToken()));
            return Future.succeededFuture();
        }
        String payload = job.toJson().encode();
        return redis.xadd(List.of(KEY_RETRY_FCM_STREAM, "*", FIELD_JOB, payload))
            .<Void>map(ok -> {
                log.info("FCM retry job enqueued. userId={}, retryCount={}, token={}", job.getUserId(), job.getRetryCount(), tokenPrefix(job.getToken()));
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue fcm retry. userId={}, token={}", job.getUserId(), tokenPrefix(job.getToken()), throwable));
    }

    /**
     * FCM 재시도 최종 실패 작업을 DLQ 스트림에 적재
     */
    public Future<Void> enqueueFcmDlq(FcmRetryJob job, String lastError) {
        if (redis == null) {
            log.error("Redis not available, cannot enqueue fcm dlq. userId={}, token={}, error={}",
                job.getUserId(), tokenPrefix(job.getToken()), lastError);
            return Future.succeededFuture();
        }
        String payload = job.toJson().put("lastError", lastError).encode();
        return redis.xadd(List.of(KEY_RETRY_FCM_DLQ_STREAM, "*", FIELD_JOB, payload))
            .<Void>map(ok -> {
                log.error("FCM retry job moved to DLQ. userId={}, retryCount={}, token={}, error={}",
                    job.getUserId(), job.getRetryCount(), tokenPrefix(job.getToken()), lastError);
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue fcm dlq. userId={}, token={}",
                job.getUserId(), tokenPrefix(job.getToken()), throwable));
    }

    /**
     * Consumer Group으로 FCM 재시도 작업 읽기
     */
    public Future<List<FcmRetryMessage>> readFcmRetryBatch(String consumerGroup, String consumerName, int count, int blockMs) {
        if (redis == null) {
            return Future.succeededFuture(List.of());
        }
        List<String> args = new ArrayList<>();
        args.add("GROUP");
        args.add(consumerGroup);
        args.add(consumerName);
        args.add("COUNT");
        args.add(String.valueOf(Math.max(1, count)));
        if (blockMs > 0) {
            args.add("BLOCK");
            args.add(String.valueOf(blockMs));
        }
        args.add("STREAMS");
        args.add(KEY_RETRY_FCM_STREAM);
        args.add(">");

        return redis.xreadgroup(args)
            .map(this::parseFcmRetryMessages)
            .onFailure(throwable -> log.error("Failed to read fcm retry stream. group={}, consumer={}", consumerGroup, consumerName, throwable));
    }

    /**
     * 처리된 메시지 ACK + Stream 엔트리 삭제
     */
    public Future<Void> ackFcmRetry(String consumerGroup, String messageId) {
        if (redis == null || messageId == null || messageId.isBlank()) {
            return Future.succeededFuture();
        }
        Future<?> ackFuture = redis.xack(List.of(KEY_RETRY_FCM_STREAM, consumerGroup, messageId))
            .compose(v -> redis.xdel(List.of(KEY_RETRY_FCM_STREAM, messageId))
                .recover(err -> Future.succeededFuture((Response) null)));

        return ackFuture
            .map(v -> (Void) null)
            .onFailure(throwable -> log.error("Failed to ack fcm retry message. group={}, messageId={}", consumerGroup, messageId, throwable));
    }

    private List<FcmRetryMessage> parseFcmRetryMessages(Response response) {
        List<FcmRetryMessage> messages = new ArrayList<>();
        if (response == null) {
            return messages;
        }
        try {
            for (Response stream : response) {
                if (stream == null || stream.size() < 2) {
                    continue;
                }
                Response entries = stream.get(1);
                if (entries == null) {
                    continue;
                }
                for (Response entry : entries) {
                    if (entry == null || entry.size() < 2) {
                        continue;
                    }
                    String messageId = toStringValue(entry.get(0));
                    Response fields = entry.get(1);
                    if (messageId == null || fields == null) {
                        continue;
                    }
                    for (int i = 0; i + 1 < fields.size(); i += 2) {
                        String field = toStringValue(fields.get(i));
                        if (!FIELD_JOB.equals(field)) {
                            continue;
                        }
                        String payload = toStringValue(fields.get(i + 1));
                        if (payload == null || payload.isBlank()) {
                            continue;
                        }
                        try {
                            FcmRetryJob job = FcmRetryJob.fromJson(new JsonObject(payload));
                            messages.add(new FcmRetryMessage(messageId, job));
                        } catch (Exception e) {
                            log.warn("Failed to parse fcm retry payload: {}", payload, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse fcm retry stream response", e);
        }
        return messages;
    }

    private String tokenPrefix(String token) {
        if (token == null || token.isBlank()) {
            return "(blank)";
        }
        return token.substring(0, Math.min(20, token.length())) + "...";
    }

    private String toStringValue(Response response) {
        return response == null ? null : response.toString();
    }
}
