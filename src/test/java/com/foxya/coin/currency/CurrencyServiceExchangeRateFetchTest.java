package com.foxya.coin.currency;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class CurrencyServiceExchangeRateFetchTest {

    @Test
    void refreshExchangeRates_fetchesFromCoinGecko_andUpsertsProviderPlusInternal(Vertx vertx, VertxTestContext tc) {
        AtomicInteger coingeckoCalls = new AtomicInteger();
        AtomicInteger paprikaCalls = new AtomicInteger();

        HttpServer server = vertx.createHttpServer().requestHandler(req -> {
            track(req, coingeckoCalls, paprikaCalls);

            if ("/api/v3/simple/price".equals(req.path())) {
                // Minimal CoinGecko JSON shape expected by CurrencyService
                JsonObject body = new JsonObject()
                    .put("ethereum", new JsonObject().put("krw", 5100000))
                    .put("tether", new JsonObject().put("krw", 1350))
                    .put("bitcoin", new JsonObject().put("krw", 90000000))
                    .put("tron", new JsonObject().put("krw", 200));
                req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(body.encode());
                return;
            }

            req.response().setStatusCode(404).end();
        });

        server.listen(0).onComplete(tc.succeeding(srv -> {
            int port = srv.actualPort();
            String base = "http://127.0.0.1:" + port;

            WebClient webClient = WebClient.create(vertx);
            CapturingExchangeRateRepository repo = new CapturingExchangeRateRepository();

            CurrencyService service = new CurrencyService(
                null,
                null,
                repo,
                webClient,
                base + "/api/v3/simple/price",
                base + "/v1/tickers"
            );

            service.refreshExchangeRates().onComplete(tc.succeeding(v -> tc.verify(() -> {
                assertThat(coingeckoCalls.get()).isGreaterThanOrEqualTo(1);
                assertThat(paprikaCalls.get()).isEqualTo(0);

                // Provider upsert
                CapturingExchangeRateRepository.UpsertCall provider = repo.findFirstBySource("COINGECKO");
                assertThat(provider).isNotNull();
                assertThat(provider.rates()).containsKeys("ETH", "USDT", "BTC", "TRX");
                assertThat(provider.rates().get("ETH")).isEqualByComparingTo(new BigDecimal("5100000"));
                assertThat(provider.rates().get("USDT")).isEqualByComparingTo(new BigDecimal("1350"));
                assertThat(provider.rates().get("BTC")).isEqualByComparingTo(new BigDecimal("90000000"));
                assertThat(provider.rates().get("TRX")).isEqualByComparingTo(new BigDecimal("200"));

                // Internal upsert always contains KRWT
                CapturingExchangeRateRepository.UpsertCall internal = repo.findFirstBySource("INTERNAL");
                assertThat(internal).isNotNull();
                assertThat(internal.rates()).containsKey("KRWT");
                assertThat(internal.rates().get("KRWT")).isEqualByComparingTo(BigDecimal.ONE);

                srv.close();
                tc.completeNow();
            })));
        }));
    }

    @Test
    void refreshExchangeRates_whenCoinGeckoFails_fallsBackToCoinPaprika(Vertx vertx, VertxTestContext tc) {
        AtomicInteger coingeckoCalls = new AtomicInteger();
        Map<String, AtomicInteger> paprikaCalls = new HashMap<>();
        paprikaCalls.put("eth-ethereum", new AtomicInteger());
        paprikaCalls.put("usdt-tether", new AtomicInteger());
        paprikaCalls.put("btc-bitcoin", new AtomicInteger());
        paprikaCalls.put("trx-tron", new AtomicInteger());

        HttpServer server = vertx.createHttpServer().requestHandler(req -> {
            if ("/api/v3/simple/price".equals(req.path())) {
                coingeckoCalls.incrementAndGet();
                req.response().setStatusCode(500).end("{\"error\":\"boom\"}");
                return;
            }

            if (req.path() != null && req.path().startsWith("/v1/tickers/")) {
                String coinId = req.path().substring("/v1/tickers/".length());
                AtomicInteger counter = paprikaCalls.get(coinId);
                if (counter != null) counter.incrementAndGet();

                // Minimal CoinPaprika JSON shape expected by CurrencyService
                // quotes.KRW.price must exist.
                double price = switch (coinId) {
                    case "eth-ethereum" -> 5200000.0;
                    case "usdt-tether" -> 1400.0;
                    case "btc-bitcoin" -> 91000000.0;
                    case "trx-tron" -> 210.0;
                    default -> 0.0;
                };
                JsonObject body = new JsonObject()
                    .put("quotes", new JsonObject()
                        .put("KRW", new JsonObject().put("price", price)));
                req.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(200)
                    .end(body.encode());
                return;
            }

            req.response().setStatusCode(404).end();
        });

        server.listen(0).onComplete(tc.succeeding(srv -> {
            int port = srv.actualPort();
            String base = "http://127.0.0.1:" + port;

            WebClient webClient = WebClient.create(vertx);
            CapturingExchangeRateRepository repo = new CapturingExchangeRateRepository();

            CurrencyService service = new CurrencyService(
                null,
                null,
                repo,
                webClient,
                base + "/api/v3/simple/price",
                base + "/v1/tickers"
            );

            service.refreshExchangeRates().onComplete(tc.succeeding(v -> tc.verify(() -> {
                assertThat(coingeckoCalls.get()).isGreaterThanOrEqualTo(1);

                CapturingExchangeRateRepository.UpsertCall provider = repo.findFirstBySource("COINPAPRIKA");
                assertThat(provider).isNotNull();
                assertThat(provider.rates()).containsKeys("ETH", "USDT", "BTC", "TRX");
                assertThat(provider.rates().get("ETH")).isEqualByComparingTo(new BigDecimal("5200000.0"));
                assertThat(provider.rates().get("USDT")).isEqualByComparingTo(new BigDecimal("1400.0"));
                assertThat(provider.rates().get("BTC")).isEqualByComparingTo(new BigDecimal("91000000.0"));
                assertThat(provider.rates().get("TRX")).isEqualByComparingTo(new BigDecimal("210.0"));

                assertThat(paprikaCalls.get("eth-ethereum").get()).isGreaterThanOrEqualTo(1);
                assertThat(paprikaCalls.get("usdt-tether").get()).isGreaterThanOrEqualTo(1);
                assertThat(paprikaCalls.get("btc-bitcoin").get()).isGreaterThanOrEqualTo(1);
                assertThat(paprikaCalls.get("trx-tron").get()).isGreaterThanOrEqualTo(1);

                srv.close();
                tc.completeNow();
            })));
        }));
    }

    private static void track(HttpServerRequest req, AtomicInteger coingeckoCalls, AtomicInteger paprikaCalls) {
        if ("/api/v3/simple/price".equals(req.path())) {
            coingeckoCalls.incrementAndGet();
            return;
        }
        if (req.path() != null && req.path().startsWith("/v1/tickers/")) {
            paprikaCalls.incrementAndGet();
        }
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
                .updatedAt(java.time.Instant.now())
                .build());
        }

        UpsertCall findFirstBySource(String source) {
            for (UpsertCall c : upserts) {
                if (source.equals(c.source())) return c;
            }
            return null;
        }
    }
}
