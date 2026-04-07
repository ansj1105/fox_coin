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
    /**
     * 알림 수신 대상 경계.
     * USER: 사용자 알림에만 노출 / OPS: 운영 모니터링에만 노출 / BOTH: 사용자+운영 모두
     */
    private String audience;
    /**
     * 로그 발생 원천 흐름.
     * SETTLEMENT_FLOW / COLLATERAL_FLOW / TRUST_FLOW / RECONCILIATION_FLOW / SYSTEM
     */
    private String logSource;
}
