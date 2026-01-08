package com.foxya.coin.airdrop.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AirdropTransfer {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    
    private Long id;
    private String transferId;
    private Long userId;
    private Long walletId;
    private Integer currencyId;
    private BigDecimal amount;
    private String status;
    private String orderNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

