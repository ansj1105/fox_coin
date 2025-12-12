package com.foxya.coin.swap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 스왑 예상 수량 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwapQuoteDto {
    
    private String fromCurrencyCode;
    private String toCurrencyCode;
    private BigDecimal fromAmount;
    private BigDecimal exchangeRate;
    private BigDecimal fee; // 수수료 비율
    private BigDecimal feeAmount; // 수수료 금액
    private BigDecimal spread; // 스프레드 비율
    private BigDecimal spreadAmount; // 스프레드 금액
    private BigDecimal toAmount;
    private String network;
}

