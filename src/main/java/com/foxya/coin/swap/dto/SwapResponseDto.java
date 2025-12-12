package com.foxya.coin.swap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 스왑 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapResponseDto {
    
    @JsonProperty("swapId")
    private String swapId;
    
    @JsonProperty("orderNumber")
    private String orderNumber;
    
    @JsonProperty("fromCurrencyCode")
    private String fromCurrencyCode;
    
    @JsonProperty("toCurrencyCode")
    private String toCurrencyCode;
    
    @JsonProperty("fromAmount")
    private BigDecimal fromAmount;
    
    @JsonProperty("toAmount")
    private BigDecimal toAmount;
    
    @JsonProperty("network")
    private String network;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}

