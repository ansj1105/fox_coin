package com.foxya.coin.swap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 스왑 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapRequestDto {
    
    @JsonProperty("fromCurrencyCode")
    private String fromCurrencyCode;
    
    @JsonProperty("toCurrencyCode")
    private String toCurrencyCode;
    
    @JsonProperty("fromAmount")
    private BigDecimal fromAmount;
    
    @JsonProperty("network")
    private String network;
}

