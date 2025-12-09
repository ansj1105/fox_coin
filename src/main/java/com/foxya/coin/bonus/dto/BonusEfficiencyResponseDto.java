package com.foxya.coin.bonus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BonusEfficiencyResponseDto {
    private Integer totalEfficiency;
    private List<BonusInfo> bonuses;
    
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BonusInfo {
        private String type;
        private String name;
        private Integer efficiency;
        private Boolean isActive;
        private Boolean isPermanent;
        private LocalDateTime expiresAt;
        private Integer currentCount;
        private Integer maxCount;
    }
}

