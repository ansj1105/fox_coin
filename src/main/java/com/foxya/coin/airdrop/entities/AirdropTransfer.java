package com.foxya.coin.airdrop.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 에어드랍 전송 엔티티
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AirdropTransfer {
    
    private Long id;
    private String transferId;          // UUID
    private Long userId;
    private Long walletId;
    private Integer currencyId;
    private BigDecimal amount;
    private String status;             // PENDING, COMPLETED, FAILED, CANCELLED
    private String orderNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 상태 상수
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
}

