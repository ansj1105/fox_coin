package com.foxya.coin.level.dto;

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
public class LevelGuideResponseDto {
    private String title;
    private String description;
    private List<LevelInfo> levels;
    
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LevelInfo {
        private Integer level;
        private BigDecimal requiredExp;
        private BigDecimal dailyMaxMining;
        private BigDecimal efficiency;
        private BigDecimal perMinuteMining;
        private Integer expectedDays;
        private Integer dailyMaxAds;
        private Integer storeProductLimit;
        private String badgeCode;
        private String photoUrl;
        private List<String> benefits;
    }
}
