package com.foxya.coin.airdrop.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 에어드랍 Phase 엔티티
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AirdropPhase {
    
    private Long id;
    private Long userId;
    private Integer phase;              // 1~5
    private String status;              // RELEASED, PROCESSING
    private BigDecimal amount;
    private LocalDateTime unlockDate;
    private Integer daysRemaining;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 상태 상수
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_PROCESSING = "PROCESSING";
}

