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
        private List<String> benefits;
    }
}

