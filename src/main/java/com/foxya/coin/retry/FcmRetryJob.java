package com.foxya.coin.retry;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * FCM 재시도 스트림에 적재되는 작업
 */
@Value
@Builder
public class FcmRetryJob {
    public static final int DEFAULT_MAX_RETRIES = 5;

    public static final String KEY_USER_ID = "userId";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_TITLE = "title";
    public static final String KEY_BODY = "body";
    public static final String KEY_DATA = "data";
    public static final String KEY_RETRY_COUNT = "retryCount";
    public static final String KEY_MAX_RETRIES = "maxRetries";
    public static final String KEY_CREATED_AT = "createdAt";

    Long userId;
    String token;
    String title;
    String body;
    JsonObject data;
    int retryCount;
    int maxRetries;
    LocalDateTime createdAt;

    public JsonObject toJson() {
        return new JsonObject()
            .put(KEY_USER_ID, userId)
            .put(KEY_TOKEN, token)
            .put(KEY_TITLE, title)
            .put(KEY_BODY, body)
            .put(KEY_DATA, data != null ? data : new JsonObject())
            .put(KEY_RETRY_COUNT, retryCount)
            .put(KEY_MAX_RETRIES, maxRetries)
            .put(KEY_CREATED_AT, createdAt != null ? createdAt.toString() : null);
    }

    public static FcmRetryJob fromJson(JsonObject json) {
        String createdAtStr = json.getString(KEY_CREATED_AT);
        LocalDateTime createdAt = createdAtStr != null && !createdAtStr.isEmpty()
            ? LocalDateTime.parse(createdAtStr)
            : null;
        return FcmRetryJob.builder()
            .userId(json.getLong(KEY_USER_ID))
            .token(json.getString(KEY_TOKEN))
            .title(json.getString(KEY_TITLE))
            .body(json.getString(KEY_BODY))
            .data(json.getJsonObject(KEY_DATA, new JsonObject()))
            .retryCount(json.getInteger(KEY_RETRY_COUNT, 0))
            .maxRetries(json.getInteger(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES))
            .createdAt(createdAt)
            .build();
    }

    public FcmRetryJob withIncrementedRetry() {
        return FcmRetryJob.builder()
            .userId(userId)
            .token(token)
            .title(title)
            .body(body)
            .data(data != null ? data.copy() : new JsonObject())
            .retryCount(retryCount + 1)
            .maxRetries(maxRetries)
            .createdAt(createdAt)
            .build();
    }
}
