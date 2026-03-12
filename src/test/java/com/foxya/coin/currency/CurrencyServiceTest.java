package com.foxya.coin.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyServiceTest {

    @Test
    @DisplayName("이전 종가가 있으면 24시간 등락률을 계산한다")
    void calculateChange24hPercentFromPreviousClose() {
        BigDecimal change = CurrencyService.calculateChange24hPercent(
            new BigDecimal("0.0300000000"),
            new BigDecimal("0.0375000000")
        );

        assertThat(change).isEqualByComparingTo("25.00000000");
    }

    @Test
    @DisplayName("이전 종가가 없으면 24시간 등락률은 0이다")
    void calculateChange24hPercentWithoutPreviousClose() {
        BigDecimal change = CurrencyService.calculateChange24hPercent(
            null,
            new BigDecimal("0.0375000000")
        );

        assertThat(change).isEqualByComparingTo("0");
    }
}
