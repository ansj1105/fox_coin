package com.foxya.coin.retry;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 환율 외부 API 재시도 스트림에 적재되는 작업
 */
@Value
@Builder
public class ExchangeRateRetryJob {
    public static final int DEFAULT_MAX_RETRIES = 6;

    public static final String KEY_SOURCE = "source";
    public static final String KEY_RETRY_COUNT = "retryCount";
    public static final String KEY_MAX_RETRIES = "maxRetries";
    public static final String KEY_CREATED_AT = "createdAt";

    String source;
    int retryCount;
    int maxRetries;
    LocalDateTime createdAt;

    public JsonObject toJson() {
        return new JsonObject()
            .put(KEY_SOURCE, source)
            .put(KEY_RETRY_COUNT, retryCount)
            .put(KEY_MAX_RETRIES, maxRetries)
            .put(KEY_CREATED_AT, createdAt != null ? createdAt.toString() : null);
    }

    public static ExchangeRateRetryJob fromJson(JsonObject json) {
        String createdAtStr = json.getString(KEY_CREATED_AT);
        LocalDateTime createdAt = createdAtStr != null && !createdAtStr.isEmpty()
            ? LocalDateTime.parse(createdAtStr)
            : null;
        return ExchangeRateRetryJob.builder()
            .source(json.getString(KEY_SOURCE, "scheduler"))
            .retryCount(json.getInteger(KEY_RETRY_COUNT, 0))
            .maxRetries(json.getInteger(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES))
            .createdAt(createdAt)
            .build();
    }

    public ExchangeRateRetryJob withIncrementedRetry() {
        return ExchangeRateRetryJob.builder()
            .source(source)
            .retryCount(retryCount + 1)
            .maxRetries(maxRetries)
            .createdAt(createdAt)
            .build();
    }
}
