package com.foxya.coin.security.dto;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflinePayActivityLogDto {
    private String id;
    private String category;
    private String eventStatus;
    private String title;
    private String message;
    private String reasonCode;
    private String requestId;
    private String settlementId;
    private JsonObject metadata;
    private LocalDateTime createdAt;
}
