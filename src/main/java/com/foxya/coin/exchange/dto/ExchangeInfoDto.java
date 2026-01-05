package com.foxya.coin.exchange.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 환전 정보 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExchangeInfoDto {
    
    private BigDecimal exchangeRate; // 환전 비율 (KRWT 1.0 = BLUEDIA 0.8)
    private BigDecimal minExchangeAmount; // 최소 환전 금액
    private Map<String, BigDecimal> minExchangeAmountByCurrency; // 통화별 최소 환전 금액
    private String fromCurrency; // FROM 통화 (KRWT)
    private String toCurrency; // TO 통화 (BLUEDIA)
    private String note; // 참고 사항
}

