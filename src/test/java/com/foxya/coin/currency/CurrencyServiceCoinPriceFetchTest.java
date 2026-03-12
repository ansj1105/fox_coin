package com.foxya.coin.currency;

import com.foxya.coin.currency.dto.CoinPriceDto;
import com.foxya.coin.currency.dto.CoinPricesDto;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class CurrencyServiceCoinPriceFetchTest {

    @Test
    void getCoinPrices_refreshesFromProviders_andReturnsKori(Vertx vertx, VertxTestContext tc) {
        HttpServer server = vertx.createHttpServer().requestHandler(req -> {
            if ("/api/v3/simple/price".equals(req.path())) {
                JsonObject body = new JsonObject()
                    .put("bitcoin", new JsonObject().put("usd", 90000).put("usd_24h_change", 3.4))
                    .put("tron", new JsonObject().put("usd", 0.29).put("usd_24h_change", 1.5));
                req.response().putHeader("content-type", "application/json").end(body.encode());
                return;
            }

            if ("/wallet/triggerconstantcontract".equals(req.path())) {
                String reserveHex = "0000000000000000000000000000000000000000000000000000000165a0bc00"
                    + "000000000000000000000000000000000000000000000000000000000023c346"
                    + "0000000000000000000000000000000000000000000000000000000069b15b14";
                JsonObject body = new JsonObject()
                    .put("constant_result", new JsonArray().add(reserveHex));
                req.response().putHeader("content-type", "application/json").end(body.encode());
                return;
            }

            req.response().setStatusCode(404).end();
        });

        server.listen(0).onComplete(tc.succeeding(srv -> {
            String base = "http://127.0.0.1:" + srv.actualPort();
            InMemoryCoinPriceRepository coinPriceRepository = new InMemoryCoinPriceRepository();
            CurrencyService service = new CurrencyService(
                null,
                null,
                new ExchangeRateRepository(),
                coinPriceRepository,
                WebClient.create(vertx),
                base + "/api/v3/simple/price",
                base + "/v1/tickers",
                base
            );

            service.getCoinPrices(Set.of("KORI", "TRX", "BTC")).onComplete(tc.succeeding(result -> tc.verify(() -> {
                assertThat(result.getPrices()).containsKeys("KORI", "TRX", "BTC");
                assertThat(result.getPrices().get("BTC").getUsdPrice()).isEqualByComparingTo("90000");
                assertThat(result.getPrices().get("TRX").getUsdPrice()).isEqualByComparingTo("0.29");
                assertThat(result.getPrices().get("KORI").getUsdPrice()).isEqualByComparingTo("0.0001132813");
                assertThat(result.getPrices().get("KORI").getSource()).isEqualTo("SUNSWAP");
                srv.close();
                tc.completeNow();
            })));
        }));
    }

    @Test
    void getCoinPrices_whenProviderFails_returnsStoredDbValue(Vertx vertx, VertxTestContext tc) {
        InMemoryCoinPriceRepository coinPriceRepository = new InMemoryCoinPriceRepository();
        coinPriceRepository.store.put("BTC", CoinPriceDto.builder()
            .usdPrice(new BigDecimal("81234.5"))
            .change24hPercent(new BigDecimal("1.2"))
            .source("COINGECKO")
            .updatedAt(Instant.now().minusSeconds(3600))
            .build());

        CurrencyService service = new CurrencyService(
            null,
            null,
            new ExchangeRateRepository(),
            coinPriceRepository,
            WebClient.create(vertx),
            "http://127.0.0.1:1/api/v3/simple/price",
            "http://127.0.0.1:1/v1/tickers",
            "http://127.0.0.1:1"
        );

        service.getCoinPrices(Set.of("BTC")).onComplete(tc.succeeding(result -> tc.verify(() -> {
            assertThat(result.getPrices().get("BTC").getUsdPrice()).isEqualByComparingTo("81234.5");
            assertThat(result.getPrices().get("BTC").getChange24hPercent()).isEqualByComparingTo("1.2");
            tc.completeNow();
        })));
    }

    static class InMemoryCoinPriceRepository extends CoinPriceRepository {
        final Map<String, CoinPriceDto> store = new HashMap<>();

        @Override
        public Future<CoinPricesDto> getCoinPrices(SqlClient client, Set<String> currencyCodes) {
            Map<String, CoinPriceDto> prices = new HashMap<>();
            Instant updatedAt = Instant.now();
            for (String code : currencyCodes) {
                CoinPriceDto dto = store.get(code);
                if (dto != null) {
                    prices.put(code, dto);
                    if (dto.getUpdatedAt() != null && dto.getUpdatedAt().isBefore(updatedAt)) {
                        updatedAt = dto.getUpdatedAt();
                    }
                }
            }
            return Future.succeededFuture(CoinPricesDto.builder()
                .prices(prices)
                .updatedAt(updatedAt)
                .build());
        }

        @Override
        public Future<Void> upsertCoinPrices(PgPool pool, Map<String, CoinPriceDto> prices) {
            if (prices != null) {
                prices.forEach((code, dto) -> {
                    if (code != null && dto != null) {
                        store.put(code, dto);
                    }
                });
            }
            return Future.succeededFuture();
        }
    }
}
