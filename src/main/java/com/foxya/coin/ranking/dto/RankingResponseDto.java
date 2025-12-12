package com.foxya.coin.ranking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RankingResponseDto {
    private List<RankingInfo> top3;
    private List<RankingInfo> rankings;
    private RankingInfo myRank;
    private Integer totalCount;
    private AggregationInfo aggregation;
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RankingInfo {
        private Long userId;
        private Integer rank;
        private String nickname;
        private String profileImage;
        private Integer level;
        private String levelName;
        private BigDecimal totalAmount;
        private Long teamCount;
        private String country;
        private String flag;
    }
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AggregationInfo {
        private String formula;
        private BigDecimal calculation;
    }
}

