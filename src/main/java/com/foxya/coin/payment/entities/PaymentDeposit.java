package com.foxya.coin.payment.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 입금 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDeposit {
    
    private Long id;
    private String depositId;          // UUID
    private Long userId;
    private String orderNumber;         // 주문번호
    private Integer currencyId;
    private BigDecimal amount;          // 입금 금액 (토큰)
    private String depositMethod;      // CARD, BANK_TRANSFER, PAY
    private BigDecimal paymentAmount;   // 결제 금액 (원화 등)
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

