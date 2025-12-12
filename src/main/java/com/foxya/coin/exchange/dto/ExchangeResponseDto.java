package com.foxya.coin.exchange.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 환전 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeResponseDto {
    
    @JsonProperty("exchangeId")
    private String exchangeId;
    
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
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}

