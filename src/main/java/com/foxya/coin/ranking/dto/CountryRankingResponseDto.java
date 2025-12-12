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
public class CountryRankingResponseDto {
    private List<CountryRankingInfo> top3;
    private List<CountryRankingInfo> rankings;
    private CountryRankingInfo myCountry;
    private Integer totalCount;
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CountryRankingInfo {
        private Integer rank;
        private String country;
        private String countryName;
        private String flag;
        private BigDecimal totalMinedCoins;
        private Long totalMembers;
        private BigDecimal aggregation;
    }
}

