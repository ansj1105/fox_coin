package com.foxya.coin.deposit.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 토큰 입금 엔티티 (외부에서 들어오는 입금)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenDeposit {
    
    private Long id;
    private String depositId;          // UUID
    private Long userId;               // 자동 매칭 시
    private String orderNumber;         // 주문번호
    private Integer currencyId;
    private BigDecimal amount;
    private String network;            // Ether, TRON 등
    private String senderAddress;       // 송신 지갑 주소
    private String txHash;             // 블록체인 트랜잭션 해시
    private String status;              // PENDING, COMPLETED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime failedAt;
    private String errorMessage;
    
    // 상태 상수
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}

