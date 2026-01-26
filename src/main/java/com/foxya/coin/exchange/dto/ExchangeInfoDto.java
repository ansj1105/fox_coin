package com.foxya.coin.exchange.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 환전 정보 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExchangeInfoDto {
    
    private BigDecimal exchangeRate; // 환전 비율 (KORI 1.0 = F_COIN 21.5)
    private BigDecimal feeRate; // 환전 수수료율 (fromAmount 기준)
    private BigDecimal minExchangeAmount; // 최소 환전 금액 (KORI)
    private String fromCurrencyCode; // FROM 통화 (KORI)
    private String toCurrencyCode; // TO 통화 (F_COIN)
    private String note; // 참고 사항
}
