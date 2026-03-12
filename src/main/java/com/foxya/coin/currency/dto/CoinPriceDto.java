package com.foxya.coin.currency.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoinPriceDto {
    private BigDecimal usdPrice;
    private BigDecimal change24hPercent;
    private String source;
    private Instant updatedAt;
}
