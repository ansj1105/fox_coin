package com.foxya.coin.referral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralRevenueTierDto {
    private Integer minTeamSize;
    private Integer maxTeamSize;
    private BigDecimal revenuePercent;
}
