package com.foxya.coin.exchange.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 환전 예상 수량 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeQuoteDto {
    
    private String fromCurrencyCode;
    private String toCurrencyCode;
    private BigDecimal fromAmount;
    private BigDecimal exchangeRate;
    private BigDecimal feeRate;
    private BigDecimal feeAmount;
    private BigDecimal toAmount;
}
