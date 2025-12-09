package com.foxya.coin.mining.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyLimitResponseDto {
    private Integer currentLevel;
    private BigDecimal dailyMaxMining;
    private BigDecimal todayMiningAmount;
    private LocalDateTime resetAt;
    private Boolean isLimitReached;
}

