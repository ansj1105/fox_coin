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
public class OfflinePayTrustCenterLogDto {
    private String id;
    private String eventType;
    private String eventStatus;
    private String message;
    private String reasonCode;
    private JsonObject metadata;
    private LocalDateTime createdAt;
}
