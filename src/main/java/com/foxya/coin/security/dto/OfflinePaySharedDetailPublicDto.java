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
public class OfflinePaySharedDetailPublicDto {
    private String token;
    private String itemId;
    private JsonObject payload;
    private LocalDateTime expiresAt;
}
