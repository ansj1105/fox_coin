package com.foxya.coin.mining.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LevelInfoResponseDto {
    private List<LevelInfo> levels;
    
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LevelInfo {
        private Integer level;
        private BigDecimal dailyMaxMining;
        private BigDecimal efficiency;
        private BigDecimal requiredExp;
        private BigDecimal perMinuteMining;
        private Integer expectedDays;
        private Integer dailyMaxAds;
        private Integer storeProductLimit;
        private String badgeCode;
        private String photoUrl;
    }
}
