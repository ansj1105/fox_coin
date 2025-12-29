package com.foxya.coin.mining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiningInfoResponseDto {
    private BigDecimal todayMiningAmount;
    private BigDecimal totalBalance;
    private Integer bonusEfficiency;
    private String remainingTime;
    private Boolean isActive;
    private BigDecimal dailyMaxMining;
    private Integer currentLevel;
    private BigDecimal nextLevelRequired;
    private Integer adWatchCount;
    private Integer maxAdWatchCount;
}

