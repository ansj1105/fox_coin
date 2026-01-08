package com.foxya.coin.airdrop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirdropPhaseDto {
    private Long id;
    private Integer phase;
    private String status;
    private BigDecimal amount;
    private LocalDateTime unlockDate;
    private Integer daysRemaining;
}

