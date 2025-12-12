package com.foxya.coin.currency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.currency.dto.ExchangeRatesDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CurrencyHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<ExchangeRatesDto>> refExchangeRates = new TypeReference<>() {};
    
    public CurrencyHandlerTest() {
        super("/api/v1/currencies");
    }
    
    @Nested
    @DisplayName("환율 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetExchangeRatesTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 환율 조회 (인증 불필요)")
        void successGetExchangeRates(VertxTestContext tc) {
            reqGet(getUrl("/exchange-rates"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get exchange rates response: {}", res.bodyAsJsonObject());
                    ExchangeRatesDto exchangeRates = expectSuccessAndGetResponse(res, refExchangeRates);
                    
                    assertThat(exchangeRates.getRates()).isNotNull();
                    assertThat(exchangeRates.getRates()).containsKeys("ETH", "USDT", "KRWT");
                    assertThat(exchangeRates.getRates().get("ETH")).isNotNull();
                    assertThat(exchangeRates.getRates().get("USDT")).isNotNull();
                    assertThat(exchangeRates.getRates().get("KRWT")).isEqualTo(java.math.BigDecimal.ONE);
                    assertThat(exchangeRates.getUpdatedAt()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
    }
}

