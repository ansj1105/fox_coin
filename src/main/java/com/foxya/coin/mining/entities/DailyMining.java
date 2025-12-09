package com.foxya.coin.mining.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyMining {
    private Long id;
    private Long userId;
    private LocalDate miningDate;
    private BigDecimal miningAmount;
    private LocalDateTime resetAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

