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
public class AirdropPhase {
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    
    private Long id;
    private Long userId;
    private Integer phase;
    private String status;
    private BigDecimal amount;
    private Boolean claimed;  // true: Release 완료(락업 해제 금액 반영), false: Release 버튼 가능
    private LocalDateTime unlockDate;
    private Integer daysRemaining;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

