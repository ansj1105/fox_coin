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
    private String toAddress;           // 수신 지갑 주소
    private Integer logIndex;           // 이벤트 로그 인덱스 (EVM 등)
    private Long blockNumber;           // 블록 번호
    private String txHash;             // 블록체인 트랜잭션 해시
    private String status;              // PENDING, COMPLETED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime failedAt;
    private String errorMessage;
    private String sweepStatus;
    private String sweepTxHash;
    private LocalDateTime sweepRequestedAt;
    private LocalDateTime sweepSubmittedAt;
    private LocalDateTime sweepFailedAt;
    private String sweepErrorMessage;
    
    // 상태 상수
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String SWEEP_STATUS_REQUESTED = "REQUESTED";
    public static final String SWEEP_STATUS_SUBMITTED = "SUBMITTED";
    public static final String SWEEP_STATUS_FAILED = "FAILED";
}

