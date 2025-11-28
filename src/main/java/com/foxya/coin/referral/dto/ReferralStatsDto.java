package com.foxya.coin.referral.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferralStatsDto {
    private Long userId;
    private Integer directCount;      // 직접 추천 수 (모든 추천, ACTIVE/DEACTIVE 포함)
    private Integer activeTeamCount;  // 전체 팀원 수 (ACTIVE 상태만)
    private BigDecimal totalReward;   // 총 리워드
    private BigDecimal todayReward;   // 오늘 리워드 (미구현)
}

