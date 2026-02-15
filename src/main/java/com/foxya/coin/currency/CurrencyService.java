package com.foxya.coin.currency;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.currency.dto.ExchangeRatesDto;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Currency/exchange-rate service.
 *
 * Goal: return a reliable, real-time-ish USDT->KRW (and ETH->KRW) rate.
 * - Public price APIs are rate-limited: cache results with TTL.
 * - If the primary provider fails, try a secondary provider.
 * - On failures, serve the last cached value instead of hard-failing.
 */
@Slf4j
public class CurrencyService extends BaseService {

    private final CurrencyRepository currencyRepository; // kept for future use
    private final WebClient webClient;

    private static final String COINGECKO_API_URL = "https://api.coingecko.com/api/v3/simple/price";
    private static final String COINPAPRIKA_API_URL = "https://api.coinpaprika.com/v1/tickers";

    // Cache/HTTP tuning
    private static final long CACHE_TTL_MS = parseLongEnv("EXCHANGE_RATE_CACHE_TTL_MS", 60_000L);
    private static final long HTTP_TIMEOUT_MS = parseLongEnv("EXCHANGE_RATE_HTTP_TIMEOUT_MS", 10_000L);

    // Optional CoinGecko API keys (header-based)
    private static final String COINGECKO_DEMO_KEY = System.getenv("COINGECKO_DEMO_API_KEY"); // x-cg-demo-api-key
    private static final String COINGECKO_PRO_KEY = System.getenv("COINGECKO_PRO_API_KEY");   // x-cg-pro-api-key

    // Fallback defaults (used only until we get a successful refresh)
    private static final BigDecimal DEFAULT_ETH_KRW = new BigDecimal("5000000.0");
    private static final BigDecimal DEFAULT_USDT_KRW = new BigDecimal("1300.0");
    private static final BigDecimal RATE_KRWT = BigDecimal.ONE;

    // Cached values
    private final AtomicReference<BigDecimal> cachedEthRate = new AtomicReference<>(DEFAULT_ETH_KRW);
    private final AtomicReference<BigDecimal> cachedUsdtRate = new AtomicReference<>(DEFAULT_USDT_KRW);
    private final AtomicReference<Instant> cachedUpdatedAt = new AtomicReference<>(Instant.now());

    // Prevent thundering herd refreshes
    private final AtomicReference<Future<ExchangeRatesDto>> refreshInFlight = new AtomicReference<>(null);

    public CurrencyService(PgPool pool, CurrencyRepository currencyRepository, WebClient webClient) {
        super(pool);
        this.currencyRepository = currencyRepository;
        this.webClient = webClient;
    }

    /**
     * Returns KRW per 1 unit of each currency.
     * - USDT: 1 USDT -> KRW
     * - ETH:  1 ETH  -> KRW
     * - KRWT: always 1.0
     */
    public Future<ExchangeRatesDto> getExchangeRates() {
        Instant now = Instant.now();
        Instant updatedAt = cachedUpdatedAt.get();

        // Fresh cache
        if (Duration.between(updatedAt, now).toMillis() < CACHE_TTL_MS) {
            return Future.succeededFuture(buildRatesFromCache());
        }

        // Reuse in-flight refresh if present
        Future<ExchangeRatesDto> inFlight = refreshInFlight.get();
        if (inFlight != null && !inFlight.isComplete()) {
            return inFlight;
        }

        Promise<ExchangeRatesDto> promise = Promise.promise();
        Future<ExchangeRatesDto> created = promise.future();

        if (!refreshInFlight.compareAndSet(inFlight, created)) {
            Future<ExchangeRatesDto> raced = refreshInFlight.get();
            return raced != null ? raced : Future.succeededFuture(buildRatesFromCache());
        }

        refreshRatesFromProviders()
            .onComplete(ar -> {
                try {
                    if (ar.succeeded()) {
                        boolean anyUpdated = applyRates(ar.result());
                        if (anyUpdated) {
                            cachedUpdatedAt.set(Instant.now());
                        }
                    } else {
                        log.warn("Exchange rate refresh failed; serving cached rates. Error: {}", ar.cause().getMessage());
                    }
                } finally {
                    refreshInFlight.set(null);
                    promise.complete(buildRatesFromCache());
                }
            });

        return created;
    }

    private Future<Map<String, BigDecimal>> refreshRatesFromProviders() {
        return fetchFromCoinGecko()
            .recover(err -> {
                log.warn("CoinGecko fetch failed, trying CoinPaprika. Error: {}", err.getMessage());
                return fetchFromCoinPaprika();
            });
    }

    private Future<Map<String, BigDecimal>> fetchFromCoinGecko() {
        String url = COINGECKO_API_URL + "?ids=ethereum,tether&vs_currencies=krw";

        var req = webClient.getAbs(url)
            .timeout(HTTP_TIMEOUT_MS)
            .putHeader("Accept", "application/json")
            .putHeader("User-Agent", "foxya-coin-service/1.0");

        if (COINGECKO_DEMO_KEY != null && !COINGECKO_DEMO_KEY.isBlank()) {
            req.putHeader("x-cg-demo-api-key", COINGECKO_DEMO_KEY);
        }
        if (COINGECKO_PRO_KEY != null && !COINGECKO_PRO_KEY.isBlank()) {
            req.putHeader("x-cg-pro-api-key", COINGECKO_PRO_KEY);
        }

        return req.send().compose(res -> {
            if (res.statusCode() != 200) {
                String body = "";
                try { body = res.bodyAsString(); } catch (Exception ignored) {}
                return Future.failedFuture("CoinGecko non-200: status=" + res.statusCode() + " body=" + body);
            }

            JsonObject json;
            try {
                json = res.bodyAsJsonObject();
            } catch (Exception e) {
                String body = "";
                try { body = res.bodyAsString(); } catch (Exception ignored) {}
                return Future.failedFuture("CoinGecko invalid JSON: " + e.getMessage() + " body=" + body);
            }

            Map<String, BigDecimal> rates = new HashMap<>();
            BigDecimal eth = extractNestedDecimal(json, "ethereum", "krw");
            if (eth != null) rates.put("ETH", eth);

            BigDecimal usdt = extractNestedDecimal(json, "tether", "krw");
            if (usdt != null) rates.put("USDT", usdt);

            if (rates.isEmpty()) {
                return Future.failedFuture("CoinGecko response missing ETH/USDT krw");
            }

            return Future.succeededFuture(rates);
        });
    }

    private Future<Map<String, BigDecimal>> fetchFromCoinPaprika() {
        // /v1/tickers/{coinId}?quotes=KRW
        Future<BigDecimal> ethF = fetchCoinPaprikaKrwPrice("eth-ethereum")
            .recover(e -> Future.succeededFuture(null));
        Future<BigDecimal> usdtF = fetchCoinPaprikaKrwPrice("usdt-tether")
            .recover(e -> Future.succeededFuture(null));

        return CompositeFuture.all(ethF, usdtF)
            .map(cf -> {
                Map<String, BigDecimal> rates = new HashMap<>();
                BigDecimal eth = cf.resultAt(0);
                BigDecimal usdt = cf.resultAt(1);

                if (eth != null) rates.put("ETH", eth);
                if (usdt != null) rates.put("USDT", usdt);

                return rates;
            })
            .compose(rates -> {
                if (rates.isEmpty()) {
                    return Future.failedFuture("CoinPaprika response missing ETH/USDT krw");
                }
                return Future.succeededFuture(rates);
            });
    }

    private Future<BigDecimal> fetchCoinPaprikaKrwPrice(String coinId) {
        String url = COINPAPRIKA_API_URL + "/" + coinId + "?quotes=KRW";

        return webClient.getAbs(url)
            .timeout(HTTP_TIMEOUT_MS)
            .putHeader("Accept", "application/json")
            .putHeader("User-Agent", "foxya-coin-service/1.0")
            .send()
            .compose(res -> {
                if (res.statusCode() != 200) {
                    return Future.failedFuture("CoinPaprika non-200: status=" + res.statusCode());
                }

                JsonObject json;
                try {
                    json = res.bodyAsJsonObject();
                } catch (Exception e) {
                    return Future.failedFuture("CoinPaprika invalid JSON: " + e.getMessage());
                }

                BigDecimal price = extractNestedDecimal(json, "quotes", "KRW", "price");
                if (price == null) {
                    return Future.failedFuture("CoinPaprika missing quotes.KRW.price");
                }

                return Future.succeededFuture(price);
            });
    }

    private boolean applyRates(Map<String, BigDecimal> rates) {
        boolean updated = false;

        BigDecimal eth = rates.get("ETH");
        if (eth != null) {
            cachedEthRate.set(eth);
            updated = true;
            log.info("ETH rate updated: {} KRW", eth);
        }

        BigDecimal usdt = rates.get("USDT");
        if (usdt != null) {
            cachedUsdtRate.set(usdt);
            updated = true;
            log.info("USDT rate updated: {} KRW", usdt);
        }

        return updated;
    }

    private ExchangeRatesDto buildRatesFromCache() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("ETH", cachedEthRate.get());
        rates.put("USDT", cachedUsdtRate.get());
        rates.put("KRWT", RATE_KRWT);

        return ExchangeRatesDto.builder()
            .rates(rates)
            .updatedAt(cachedUpdatedAt.get())
            .build();
    }

    public BigDecimal getRateForCurrency(String currencyCode) {
        switch (currencyCode) {
            case "ETH":
                return cachedEthRate.get();
            case "USDT":
                return cachedUsdtRate.get();
            case "KRWT":
                return RATE_KRWT;
            default:
                return BigDecimal.ONE;
        }
    }

    /**
     * fromCurrency -> KRWT -> toCurrency
     * e.g. ETH -> USDT = (ETH/KRWT) / (USDT/KRWT)
     */
    public BigDecimal getExchangeRate(String fromCurrencyCode, String toCurrencyCode) {
        BigDecimal fromRate = getRateForCurrency(fromCurrencyCode);
        BigDecimal toRate = getRateForCurrency(toCurrencyCode);
        return fromRate.divide(toRate, 18, RoundingMode.HALF_UP);
    }

    private static BigDecimal extractNestedDecimal(JsonObject obj, String... path) {
        Object cur = obj;
        for (String key : path) {
            if (!(cur instanceof JsonObject)) return null;
            cur = ((JsonObject) cur).getValue(key);
            if (cur == null) return null;
        }
        try {
            return new BigDecimal(cur.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLongEnv(String key, long defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}