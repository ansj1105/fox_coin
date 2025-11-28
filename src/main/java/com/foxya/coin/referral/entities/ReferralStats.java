package com.foxya.coin.referral.entities;

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
public class ReferralStats {
    private Long id;
    private Long userId;
    private Integer directCount;
    private Integer teamCount;
    private BigDecimal totalReward;
    private BigDecimal todayReward;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

