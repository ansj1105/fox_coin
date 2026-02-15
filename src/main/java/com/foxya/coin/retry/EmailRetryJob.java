package com.foxya.coin.retry;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 이메일 재시도 큐에 적재되는 작업
 */
@Value
@Builder
public class EmailRetryJob {
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final String KEY_TYPE = "type";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_CODE_OR_PASSWORD = "codeOrPassword";
    public static final String KEY_RETRY_COUNT = "retryCount";
    public static final String KEY_MAX_RETRIES = "maxRetries";
    public static final String KEY_CREATED_AT = "createdAt";

    EmailRetryType type;
    String email;
    String codeOrPassword;
    int retryCount;
    int maxRetries;
    LocalDateTime createdAt;

    public JsonObject toJson() {
        return new JsonObject()
            .put(KEY_TYPE, type.name())
            .put(KEY_EMAIL, email)
            .put(KEY_CODE_OR_PASSWORD, codeOrPassword)
            .put(KEY_RETRY_COUNT, retryCount)
            .put(KEY_MAX_RETRIES, maxRetries)
            .put(KEY_CREATED_AT, createdAt != null ? createdAt.toString() : null);
    }

    public static EmailRetryJob fromJson(JsonObject json) {
        EmailRetryType type = json.getString(KEY_TYPE) != null
            ? EmailRetryType.valueOf(json.getString(KEY_TYPE))
            : null;
        String createdAtStr = json.getString(KEY_CREATED_AT);
        LocalDateTime createdAt = createdAtStr != null && !createdAtStr.isEmpty()
            ? LocalDateTime.parse(createdAtStr)
            : null;
        return EmailRetryJob.builder()
            .type(type)
            .email(json.getString(KEY_EMAIL))
            .codeOrPassword(json.getString(KEY_CODE_OR_PASSWORD))
            .retryCount(json.getInteger(KEY_RETRY_COUNT, 0))
            .maxRetries(json.getInteger(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES))
            .createdAt(createdAt)
            .build();
    }

    public EmailRetryJob withIncrementedRetry() {
        return EmailRetryJob.builder()
            .type(type)
            .email(email)
            .codeOrPassword(codeOrPassword)
            .retryCount(retryCount + 1)
            .maxRetries(maxRetries)
            .createdAt(createdAt)
            .build();
    }
}
