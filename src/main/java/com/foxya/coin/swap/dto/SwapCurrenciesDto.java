package com.foxya.coin.swap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 스왑 가능한 통화 목록 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwapCurrenciesDto {
    
    private List<CurrencyInfo> currencies;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrencyInfo {
        private String code;
        private String name;
        private String symbol;
        private String network;
        private Integer decimals;
        private java.math.BigDecimal minSwapAmount;
    }
}

