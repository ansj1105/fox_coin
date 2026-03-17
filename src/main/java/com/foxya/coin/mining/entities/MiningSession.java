package com.foxya.coin.mining.entities;

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
public class MiningSession {
    private Long id;
    private Long userId;
    private LocalDateTime startedAt;
    private LocalDateTime endsAt;
    private BigDecimal ratePerHour;
    private LocalDateTime lastSettledAt;
    private BigDecimal creditedAmount;
    private LocalDateTime createdAt;
}
