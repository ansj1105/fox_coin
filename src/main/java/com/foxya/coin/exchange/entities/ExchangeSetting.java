package com.foxya.coin.exchange.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExchangeSetting {
    private Long id;
    private String fromCurrencyCode;
    private String toCurrencyCode;
    private BigDecimal exchangeRate;
    private BigDecimal fee;
    private BigDecimal minExchangeAmount;
    private String note;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
