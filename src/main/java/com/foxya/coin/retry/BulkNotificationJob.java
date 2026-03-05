package com.foxya.coin.retry;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 관리자 다건 알림 스트림에 적재되는 작업
 */
@Value
@Builder
public class BulkNotificationJob {
    public static final int DEFAULT_MAX_RETRIES = 3;

    public static final String KEY_REQUEST_ID = "requestId";
    public static final String KEY_ADMIN_ID = "adminId";
    public static final String KEY_USER_ID = "userId";
    public static final String KEY_TYPE = "type";
    public static final String KEY_TITLE = "title";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_RELATED_ID = "relatedId";
    public static final String KEY_METADATA = "metadata";
    public static final String KEY_RETRY_COUNT = "retryCount";
    public static final String KEY_MAX_RETRIES = "maxRetries";
    public static final String KEY_CREATED_AT = "createdAt";

    String requestId;
    Long adminId;
    Long userId;
    String type;
    String title;
    String message;
    Long relatedId;
    String metadata;
    int retryCount;
    int maxRetries;
    LocalDateTime createdAt;

    public JsonObject toJson() {
        return new JsonObject()
            .put(KEY_REQUEST_ID, requestId)
            .put(KEY_ADMIN_ID, adminId)
            .put(KEY_USER_ID, userId)
            .put(KEY_TYPE, type)
            .put(KEY_TITLE, title)
            .put(KEY_MESSAGE, message)
            .put(KEY_RELATED_ID, relatedId)
            .put(KEY_METADATA, metadata)
            .put(KEY_RETRY_COUNT, retryCount)
            .put(KEY_MAX_RETRIES, maxRetries)
            .put(KEY_CREATED_AT, createdAt != null ? createdAt.toString() : null);
    }

    public static BulkNotificationJob fromJson(JsonObject json) {
        String createdAtStr = json.getString(KEY_CREATED_AT);
        LocalDateTime createdAt = createdAtStr != null && !createdAtStr.isEmpty()
            ? LocalDateTime.parse(createdAtStr)
            : null;
        return BulkNotificationJob.builder()
            .requestId(json.getString(KEY_REQUEST_ID))
            .adminId(json.getLong(KEY_ADMIN_ID))
            .userId(json.getLong(KEY_USER_ID))
            .type(json.getString(KEY_TYPE))
            .title(json.getString(KEY_TITLE))
            .message(json.getString(KEY_MESSAGE))
            .relatedId(json.getLong(KEY_RELATED_ID))
            .metadata(json.getString(KEY_METADATA))
            .retryCount(json.getInteger(KEY_RETRY_COUNT, 0))
            .maxRetries(json.getInteger(KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES))
            .createdAt(createdAt)
            .build();
    }

    public BulkNotificationJob withIncrementedRetry() {
        return BulkNotificationJob.builder()
            .requestId(requestId)
            .adminId(adminId)
            .userId(userId)
            .type(type)
            .title(title)
            .message(message)
            .relatedId(relatedId)
            .metadata(metadata)
            .retryCount(retryCount + 1)
            .maxRetries(maxRetries)
            .createdAt(createdAt)
            .build();
    }
}
