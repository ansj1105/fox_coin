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
    public static final String KEY_RETRY_EXCHANGE_RATE_STREAM = "retry:exchange-rate:stream";
    public static final String KEY_RETRY_EXCHANGE_RATE_DLQ_STREAM = "retry:exchange-rate:dlq:stream";
    public static final String KEY_BULK_NOTIFICATION_STREAM = "bulk:notification:stream";
    public static final String KEY_BULK_NOTIFICATION_DLQ_STREAM = "bulk:notification:dlq:stream";
    private static final String KEY_BULK_NOTIFICATION_STATUS_PREFIX = "bulk:notification:status:";
    private static final int BULK_NOTIFICATION_STATUS_TTL_SECONDS = 3 * 24 * 60 * 60;
    private static final String FIELD_JOB = "job";

    private final RedisAPI redis;

    public RetryQueuePublisher(RedisAPI redis) {
        this.redis = redis;
    }

    public record FcmRetryMessage(String messageId, FcmRetryJob job) { }
    public record ExchangeRateRetryMessage(String messageId, ExchangeRateRetryJob job) { }
    public record BulkNotificationMessage(String messageId, BulkNotificationJob job) { }

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

    /**
     * 환율 재시도 Consumer Group 생성 (이미 있으면 성공 처리)
     */
    public Future<Void> ensureExchangeRateRetryConsumerGroup(String consumerGroup) {
        if (redis == null) {
            return Future.succeededFuture();
        }
        return redis.xgroup(List.of("CREATE", KEY_RETRY_EXCHANGE_RATE_STREAM, consumerGroup, "0", "MKSTREAM"))
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
     * 환율 외부 API 재시도 작업을 Redis Stream에 적재
     */
    public Future<Void> enqueueExchangeRateRetry(ExchangeRateRetryJob job) {
        if (redis == null) {
            log.warn("Redis not available, skipping exchange-rate retry enqueue. source={}, retryCount={}",
                job.getSource(), job.getRetryCount());
            return Future.succeededFuture();
        }
        String payload = job.toJson().encode();
        return redis.xadd(List.of(KEY_RETRY_EXCHANGE_RATE_STREAM, "*", FIELD_JOB, payload))
            .<Void>map(ok -> {
                log.info("Exchange-rate retry job enqueued. source={}, retryCount={}", job.getSource(), job.getRetryCount());
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue exchange-rate retry. source={}", job.getSource(), throwable));
    }

    /**
     * 환율 외부 API 재시도 최종 실패 작업을 DLQ 스트림에 적재
     */
    public Future<Void> enqueueExchangeRateDlq(ExchangeRateRetryJob job, String lastError) {
        if (redis == null) {
            log.error("Redis not available, cannot enqueue exchange-rate dlq. source={}, error={}", job.getSource(), lastError);
            return Future.succeededFuture();
        }
        String payload = job.toJson().put("lastError", lastError).encode();
        return redis.xadd(List.of(KEY_RETRY_EXCHANGE_RATE_DLQ_STREAM, "*", FIELD_JOB, payload))
            .<Void>map(ok -> {
                log.error("Exchange-rate retry job moved to DLQ. source={}, retryCount={}, error={}",
                    job.getSource(), job.getRetryCount(), lastError);
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue exchange-rate dlq. source={}", job.getSource(), throwable));
    }

    /**
     * Consumer Group으로 환율 재시도 작업 읽기
     */
    public Future<List<ExchangeRateRetryMessage>> readExchangeRateRetryBatch(String consumerGroup, String consumerName, int count, int blockMs) {
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
        args.add(KEY_RETRY_EXCHANGE_RATE_STREAM);
        args.add(">");

        return redis.xreadgroup(args)
            .map(this::parseExchangeRateRetryMessages)
            .onFailure(throwable -> log.error("Failed to read exchange-rate retry stream. group={}, consumer={}",
                consumerGroup, consumerName, throwable));
    }

    /**
     * 처리된 환율 재시도 메시지 ACK + Stream 엔트리 삭제
     */
    public Future<Void> ackExchangeRateRetry(String consumerGroup, String messageId) {
        if (redis == null || messageId == null || messageId.isBlank()) {
            return Future.succeededFuture();
        }
        Future<?> ackFuture = redis.xack(List.of(KEY_RETRY_EXCHANGE_RATE_STREAM, consumerGroup, messageId))
            .compose(v -> redis.xdel(List.of(KEY_RETRY_EXCHANGE_RATE_STREAM, messageId))
                .recover(err -> Future.succeededFuture((Response) null)));

        return ackFuture
            .map(v -> (Void) null)
            .onFailure(throwable -> log.error("Failed to ack exchange-rate retry message. group={}, messageId={}",
                consumerGroup, messageId, throwable));
    }

    private List<ExchangeRateRetryMessage> parseExchangeRateRetryMessages(Response response) {
        List<ExchangeRateRetryMessage> messages = new ArrayList<>();
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
                            ExchangeRateRetryJob job = ExchangeRateRetryJob.fromJson(new JsonObject(payload));
                            messages.add(new ExchangeRateRetryMessage(messageId, job));
                        } catch (Exception e) {
                            log.warn("Failed to parse exchange-rate retry payload: {}", payload, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse exchange-rate retry stream response", e);
        }
        return messages;
    }

    /**
     * 다건 알림 Consumer Group 생성 (이미 있으면 성공 처리)
     */
    public Future<Void> ensureBulkNotificationConsumerGroup(String consumerGroup) {
        if (redis == null) {
            return Future.succeededFuture();
        }
        return redis.xgroup(List.of("CREATE", KEY_BULK_NOTIFICATION_STREAM, consumerGroup, "0", "MKSTREAM"))
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
     * 다건 알림 작업을 Redis Stream에 적재
     */
    public Future<Void> enqueueBulkNotification(BulkNotificationJob job) {
        if (redis == null) {
            log.warn("Redis not available, skipping bulk-notification enqueue. requestId={}, userId={}", job.getRequestId(), job.getUserId());
            return Future.succeededFuture();
        }
        String payload = job.toJson().encode();
        return redis.xadd(List.of(KEY_BULK_NOTIFICATION_STREAM, "*", FIELD_JOB, payload))
            .<Void>map(ok -> {
                log.info("Bulk-notification job enqueued. requestId={}, userId={}, retryCount={}",
                    job.getRequestId(), job.getUserId(), job.getRetryCount());
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue bulk-notification job. requestId={}, userId={}",
                job.getRequestId(), job.getUserId(), throwable));
    }

    /**
     * 다건 알림 최종 실패 작업을 DLQ 스트림에 적재
     */
    public Future<Void> enqueueBulkNotificationDlq(BulkNotificationJob job, String lastError) {
        if (redis == null) {
            log.error("Redis not available, cannot enqueue bulk-notification DLQ. requestId={}, userId={}, error={}",
                job.getRequestId(), job.getUserId(), lastError);
            return Future.succeededFuture();
        }
        String payload = job.toJson().put("lastError", lastError).encode();
        return redis.xadd(List.of(KEY_BULK_NOTIFICATION_DLQ_STREAM, "*", FIELD_JOB, payload))
            .<Void>map(ok -> {
                log.error("Bulk-notification job moved to DLQ. requestId={}, userId={}, retryCount={}, error={}",
                    job.getRequestId(), job.getUserId(), job.getRetryCount(), lastError);
                return null;
            })
            .onFailure(throwable -> log.error("Failed to enqueue bulk-notification DLQ. requestId={}, userId={}",
                job.getRequestId(), job.getUserId(), throwable));
    }

    /**
     * Consumer Group으로 다건 알림 작업 읽기
     */
    public Future<List<BulkNotificationMessage>> readBulkNotificationBatch(String consumerGroup, String consumerName, int count, int blockMs) {
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
        args.add(KEY_BULK_NOTIFICATION_STREAM);
        args.add(">");

        return redis.xreadgroup(args)
            .map(this::parseBulkNotificationMessages)
            .onFailure(throwable -> log.error("Failed to read bulk-notification stream. group={}, consumer={}",
                consumerGroup, consumerName, throwable));
    }

    /**
     * 처리된 다건 알림 메시지 ACK + Stream 엔트리 삭제
     */
    public Future<Void> ackBulkNotification(String consumerGroup, String messageId) {
        if (redis == null || messageId == null || messageId.isBlank()) {
            return Future.succeededFuture();
        }
        Future<?> ackFuture = redis.xack(List.of(KEY_BULK_NOTIFICATION_STREAM, consumerGroup, messageId))
            .compose(v -> redis.xdel(List.of(KEY_BULK_NOTIFICATION_STREAM, messageId))
                .recover(err -> Future.succeededFuture((Response) null)));

        return ackFuture
            .map(v -> (Void) null)
            .onFailure(throwable -> log.error("Failed to ack bulk-notification message. group={}, messageId={}",
                consumerGroup, messageId, throwable));
    }

    public Future<Void> initBulkNotificationStatus(String requestId, Long adminId, int total) {
        if (redis == null || requestId == null || requestId.isBlank()) {
            return Future.succeededFuture();
        }
        LocalDateTime now = LocalDateTime.now();
        JsonObject meta = new JsonObject()
            .put("requestId", requestId)
            .put("adminId", adminId)
            .put("total", Math.max(0, total))
            .put("createdAt", now.toString())
            .put("updatedAt", now.toString());

        List<Future<?>> futures = new ArrayList<>();
        futures.add(redis.setex(bulkStatusMetaKey(requestId), String.valueOf(BULK_NOTIFICATION_STATUS_TTL_SECONDS), meta.encode()));
        futures.add(setBulkCounter(requestId, "enqueued", 0));
        futures.add(setBulkCounter(requestId, "processed", 0));
        futures.add(setBulkCounter(requestId, "success", 0));
        futures.add(setBulkCounter(requestId, "failed", 0));
        futures.add(setBulkCounter(requestId, "dlq", 0));
        futures.add(setBulkCounter(requestId, "retryScheduled", 0));
        futures.add(setBulkCounter(requestId, "enqueueFailed", 0));
        return Future.all(futures).mapEmpty();
    }

    public Future<Void> incrementBulkEnqueued(String requestId) {
        return incrementBulkStatusCounter(requestId, "enqueued", 1);
    }

    public Future<Void> incrementBulkProcessed(String requestId) {
        return incrementBulkStatusCounter(requestId, "processed", 1);
    }

    public Future<Void> incrementBulkSuccess(String requestId) {
        return incrementBulkStatusCounter(requestId, "success", 1);
    }

    public Future<Void> incrementBulkFailed(String requestId) {
        return incrementBulkStatusCounter(requestId, "failed", 1);
    }

    public Future<Void> incrementBulkDlq(String requestId) {
        return incrementBulkStatusCounter(requestId, "dlq", 1);
    }

    public Future<Void> incrementBulkRetryScheduled(String requestId) {
        return incrementBulkStatusCounter(requestId, "retryScheduled", 1);
    }

    public Future<Void> incrementBulkEnqueueFailed(String requestId) {
        return incrementBulkStatusCounter(requestId, "enqueueFailed", 1);
    }

    public Future<JsonObject> getBulkNotificationStatus(String requestId) {
        if (redis == null || requestId == null || requestId.isBlank()) {
            return Future.succeededFuture(null);
        }
        return redis.get(bulkStatusMetaKey(requestId))
            .compose(metaResponse -> {
                if (metaResponse == null || metaResponse.toString() == null || metaResponse.toString().isBlank()) {
                    return Future.succeededFuture((JsonObject) null);
                }
                JsonObject meta;
                try {
                    meta = new JsonObject(metaResponse.toString());
                } catch (Exception e) {
                    log.warn("Failed to parse bulk-notification status meta. requestId={}", requestId, e);
                    return Future.succeededFuture((JsonObject) null);
                }

                Future<Long> enqueuedFuture = getBulkCounter(requestId, "enqueued");
                Future<Long> processedFuture = getBulkCounter(requestId, "processed");
                Future<Long> successFuture = getBulkCounter(requestId, "success");
                Future<Long> failedFuture = getBulkCounter(requestId, "failed");
                Future<Long> dlqFuture = getBulkCounter(requestId, "dlq");
                Future<Long> retryScheduledFuture = getBulkCounter(requestId, "retryScheduled");
                Future<Long> enqueueFailedFuture = getBulkCounter(requestId, "enqueueFailed");

                List<Future<?>> counterFutures = List.of(
                    enqueuedFuture,
                    processedFuture,
                    successFuture,
                    failedFuture,
                    dlqFuture,
                    retryScheduledFuture,
                    enqueueFailedFuture
                );
                return Future.all(counterFutures)
                    .map(results -> {
                        long total = meta.getLong("total", 0L);
                        long enqueued = (Long) counterFutures.get(0).result();
                        long processed = (Long) counterFutures.get(1).result();
                        long success = (Long) counterFutures.get(2).result();
                        long failed = (Long) counterFutures.get(3).result();
                        long dlq = (Long) counterFutures.get(4).result();
                        long retryScheduled = (Long) counterFutures.get(5).result();
                        long enqueueFailed = (Long) counterFutures.get(6).result();
                        long pending = Math.max(0L, total - processed);
                        boolean completed = processed >= total;
                        return new JsonObject()
                            .put("requestId", requestId)
                            .put("adminId", meta.getLong("adminId"))
                            .put("createdAt", meta.getString("createdAt"))
                            .put("updatedAt", meta.getString("updatedAt"))
                            .put("total", total)
                            .put("enqueued", enqueued)
                            .put("processed", processed)
                            .put("success", success)
                            .put("failed", failed)
                            .put("dlq", dlq)
                            .put("retryScheduled", retryScheduled)
                            .put("enqueueFailed", enqueueFailed)
                            .put("pending", pending)
                            .put("completed", completed);
                    });
            });
    }

    private List<BulkNotificationMessage> parseBulkNotificationMessages(Response response) {
        List<BulkNotificationMessage> messages = new ArrayList<>();
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
                            BulkNotificationJob job = BulkNotificationJob.fromJson(new JsonObject(payload));
                            messages.add(new BulkNotificationMessage(messageId, job));
                        } catch (Exception e) {
                            log.warn("Failed to parse bulk-notification payload: {}", payload, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse bulk-notification stream response", e);
        }
        return messages;
    }

    private Future<Void> setBulkCounter(String requestId, String counter, long value) {
        return redis.setex(
                bulkStatusCounterKey(requestId, counter),
                String.valueOf(BULK_NOTIFICATION_STATUS_TTL_SECONDS),
                String.valueOf(Math.max(0L, value))
            )
            .mapEmpty();
    }

    private Future<Void> incrementBulkStatusCounter(String requestId, String counter, long delta) {
        if (redis == null || requestId == null || requestId.isBlank() || delta <= 0L) {
            return Future.succeededFuture();
        }
        String key = bulkStatusCounterKey(requestId, counter);
        return redis.incrby(key, String.valueOf(delta))
            .compose(v -> redis.expire(List.of(key, String.valueOf(BULK_NOTIFICATION_STATUS_TTL_SECONDS)))
                .recover(ignored -> Future.succeededFuture((Response) null)))
            .compose(v -> touchBulkStatusMeta(requestId))
            .mapEmpty();
    }

    private Future<Void> touchBulkStatusMeta(String requestId) {
        return redis.get(bulkStatusMetaKey(requestId))
            .compose(metaResponse -> {
                if (metaResponse == null || metaResponse.toString() == null || metaResponse.toString().isBlank()) {
                    return Future.<Void>succeededFuture();
                }
                JsonObject meta;
                try {
                    meta = new JsonObject(metaResponse.toString());
                } catch (Exception e) {
                    return Future.<Void>succeededFuture();
                }
                meta.put("updatedAt", LocalDateTime.now().toString());
                return redis.setex(
                    bulkStatusMetaKey(requestId),
                    String.valueOf(BULK_NOTIFICATION_STATUS_TTL_SECONDS),
                    meta.encode()
                ).mapEmpty();
            })
            .recover(ignored -> Future.<Void>succeededFuture());
    }

    private Future<Long> getBulkCounter(String requestId, String counter) {
        String key = bulkStatusCounterKey(requestId, counter);
        return redis.get(key)
            .map(response -> {
                if (response == null || response.toString() == null || response.toString().isBlank()) {
                    return 0L;
                }
                try {
                    return Long.parseLong(response.toString());
                } catch (Exception e) {
                    return 0L;
                }
            })
            .recover(ignored -> Future.succeededFuture(0L));
    }

    private String bulkStatusMetaKey(String requestId) {
        return KEY_BULK_NOTIFICATION_STATUS_PREFIX + requestId + ":meta";
    }

    private String bulkStatusCounterKey(String requestId, String counter) {
        return KEY_BULK_NOTIFICATION_STATUS_PREFIX + requestId + ":" + counter;
    }
}
