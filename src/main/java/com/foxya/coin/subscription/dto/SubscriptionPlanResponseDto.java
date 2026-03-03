package com.foxya.coin.subscription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionPlanResponseDto {
    private List<PlanItem> plans;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanItem {
        private String planCode;
        private String packageType;
        private String displayName;
        private Integer durationDays;
        private Integer priceKrw;
        private String priceUsd;
        private Integer miningEfficiencyBonusPercent;
        private Boolean adFree;
        private Boolean autoBoostMining;
        private Boolean referralReregisterUnlimited;
        private Boolean fullMiningHistoryAccess;
        private Boolean profileImageUnlock;
    }
}
