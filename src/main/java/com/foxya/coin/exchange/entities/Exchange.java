package com.foxya.coin.exchange.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 환전 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exchange {
    
    private Long id;
    private String exchangeId;          // UUID
    private Long userId;
    private String orderNumber;         // 주문번호
    private Integer fromCurrencyId;     // KRWT
    private Integer toCurrencyId;       // BLUEDIA
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
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

