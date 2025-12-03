package com.foxya.coin.transfer.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 내부 전송 엔티티
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransfer {
    
    private Long id;
    private String transferId;          // UUID
    private Long senderId;
    private Long senderWalletId;
    private Long receiverId;
    private Long receiverWalletId;
    private Integer currencyId;
    private BigDecimal amount;
    private BigDecimal fee;
    private String status;              // PENDING, COMPLETED, FAILED, CANCELLED
    private String transferType;        // INTERNAL, REFERRAL_REWARD, ADMIN_GRANT
    private String memo;
    private String requestIp;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private String errorMessage;
    
    // 상태 상수
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    
    // 전송 타입 상수
    public static final String TYPE_INTERNAL = "INTERNAL";
    public static final String TYPE_REFERRAL_REWARD = "REFERRAL_REWARD";
    public static final String TYPE_ADMIN_GRANT = "ADMIN_GRANT";
}

