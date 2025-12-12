package com.foxya.coin.swap.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 스왑 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Swap {
    
    private Long id;
    private String swapId;              // UUID
    private Long userId;
    private String orderNumber;         // 주문번호
    private Integer fromCurrencyId;
    private Integer toCurrencyId;
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
    private String network;            // Ether, TRON 등
    private String status;              // PENDING, COMPLETED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private String errorMessage;
    
    // 상태 상수
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}

