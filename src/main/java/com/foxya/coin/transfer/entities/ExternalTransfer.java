package com.foxya.coin.transfer.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 외부 전송 (출금) 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTransfer {
    
    private Long id;
    private String transferId;          // UUID
    private Long userId;
    private Long walletId;
    private Integer currencyId;
    private String toAddress;           // 수신 주소 (외부 지갑)
    private BigDecimal amount;
    private BigDecimal fee;             // 서비스 수수료
    private BigDecimal networkFee;      // 네트워크 수수료 (가스비)
    private String status;              // PENDING, PROCESSING, SUBMITTED, CONFIRMED, FAILED, CANCELLED
    private String txHash;              // 블록체인 트랜잭션 해시
    private String chain;               // TRON, ETH 등
    private Integer confirmations;
    private Integer requiredConfirmations;
    private String memo;
    private String requestIp;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime failedAt;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    
    // 상태 상수
    public static final String STATUS_PENDING = "PENDING";           // 대기중
    public static final String STATUS_PROCESSING = "PROCESSING";     // 처리중 (잔액 잠금)
    public static final String STATUS_SUBMITTED = "SUBMITTED";       // 블록체인 제출됨
    public static final String STATUS_CONFIRMED = "CONFIRMED";       // 컨펌 완료
    public static final String STATUS_FAILED = "FAILED";             // 실패
    public static final String STATUS_CANCELLED = "CANCELLED";       // 취소
    
    // 체인 상수
    public static final String CHAIN_TRON = "TRON";
    public static final String CHAIN_ETH = "ETH";
}

