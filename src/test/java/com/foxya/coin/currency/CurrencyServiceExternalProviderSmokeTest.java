package com.foxya.coin.currency;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Optional smoke test that calls real external providers.
 *
 * Disabled by default because it depends on network + provider uptime/rate limits.
 * Enable via env: RUN_EXTERNAL_RATE_TESTS=true
 */
@ExtendWith(VertxExtension.class)
public class CurrencyServiceExternalProviderSmokeTest {

    @Test
    @Timeout(30)
    void refreshExchangeRates_callsRealProviders_whenEnabled(Vertx vertx, VertxTestContext tc) {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUN_EXTERNAL_RATE_TESTS")),
            "Set RUN_EXTERNAL_RATE_TESTS=true to enable external provider smoke test");

        WebClient webClient = WebClient.create(vertx);
        CapturingExchangeRateRepository repo = new CapturingExchangeRateRepository();

        CurrencyService service = new CurrencyService(
            null,
            null,
            repo,
            webClient,
            "https://api.coingecko.com/api/v3/simple/price",
            "https://api.coinpaprika.com/v1/tickers"
        );

        service.refreshExchangeRates().onComplete(tc.succeeding(v -> tc.verify(() -> {
            CapturingExchangeRateRepository.UpsertCall provider = repo.findFirstNonInternal();
            assertThat(provider).as("provider upsert should exist").isNotNull();
            assertThat(provider.source()).isIn("COINGECKO", "COINPAPRIKA");
            assertThat(provider.rates()).containsKeys("ETH", "USDT", "BTC", "TRX");

            // Basic sanity: all are positive numbers.
            assertPositive(provider.rates().get("ETH"));
            assertPositive(provider.rates().get("USDT"));
            assertPositive(provider.rates().get("BTC"));
            assertPositive(provider.rates().get("TRX"));

            // Internal KRWT should always be written too.
            CapturingExchangeRateRepository.UpsertCall internal = repo.findFirstBySource("INTERNAL");
            assertThat(internal).isNotNull();
            assertThat(internal.rates()).containsKey("KRWT");
            assertThat(internal.rates().get("KRWT")).isEqualByComparingTo(BigDecimal.ONE);

            tc.completeNow();
        })));
    }

    private static void assertPositive(BigDecimal v) {
        assertThat(v).isNotNull();
        assertThat(v.compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    /**
     * Minimal stub: captures upsert calls without requiring a real DB.
     */
    static class CapturingExchangeRateRepository extends ExchangeRateRepository {
        record UpsertCall(String source, Map<String, BigDecimal> rates) {}

        private final List<UpsertCall> upserts = new ArrayList<>();

        @Override
        public Future<Void> upsertRates(PgPool pool, Map<String, BigDecimal> rates, String source) {
            Map<String, BigDecimal> copy = new HashMap<>();
            if (rates != null) {
                rates.forEach((k, v) -> {
                    if (k != null && v != null) copy.put(k, v);
                });
            }
            upserts.add(new UpsertCall(source, copy));
            return Future.succeededFuture();
        }

        @Override
        public Future<com.foxya.coin.currency.dto.ExchangeRatesDto> getExchangeRates(SqlClient client) {
            return Future.succeededFuture(com.foxya.coin.currency.dto.ExchangeRatesDto.builder()
                .rates(Map.of("KRWT", BigDecimal.ONE))
                .updatedAt(Instant.now())
                .build());
        }

        UpsertCall findFirstBySource(String source) {
            for (UpsertCall c : upserts) {
                if (source.equals(c.source())) return c;
            }
            return null;
        }

        UpsertCall findFirstNonInternal() {
            for (UpsertCall c : upserts) {
                if (!"INTERNAL".equals(c.source())) return c;
            }
            return null;
        }
    }
}
