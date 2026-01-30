package com.foxya.coin.retry;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이메일·외부 API 실패 시 Redis 재시도 큐에 적재
 */
@Slf4j
public class RetryQueuePublisher {

    public static final String KEY_RETRY_EMAIL = "retry:email";
    public static final String KEY_RETRY_EMAIL_DLQ = "retry:email:dlq";

    private final RedisAPI redis;

    public RetryQueuePublisher(RedisAPI redis) {
        this.redis = redis;
    }

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
}
