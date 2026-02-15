package com.foxya.coin.referral.entities;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReferralRevenueTier {
    private Long id;
    private Integer minTeamSize;
    private Integer maxTeamSize;
    private BigDecimal revenuePercent;
    private Boolean isActive;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
