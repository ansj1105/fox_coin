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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exchange rate source of truth: DB.
 *
 * - Client/API requests read from DB (fast, stable, no per-user external calls).
 * - A scheduler periodically refreshes rates from external providers and upserts into DB.
 */
@Slf4j
public class CurrencyService extends BaseService {

    private final CurrencyRepository currencyRepository; // kept for other currency domain usage
    private final ExchangeRateRepository exchangeRateRepository;
    private final WebClient webClient;

    // Providers
    private static final String COINGECKO_API_URL = "https://api.coingecko.com/api/v3/simple/price";
    private static final String COINPAPRIKA_API_URL = "https://api.coinpaprika.com/v1/tickers";

    private static final long HTTP_TIMEOUT_MS = parseLongEnv("EXCHANGE_RATE_HTTP_TIMEOUT_MS", 10_000L);

    // Optional CoinGecko API keys (header-based)
    private static final String COINGECKO_DEMO_KEY = System.getenv("COINGECKO_DEMO_API_KEY"); // x-cg-demo-api-key
    private static final String COINGECKO_PRO_KEY = System.getenv("COINGECKO_PRO_API_KEY");   // x-cg-pro-api-key

    // Defaults (used until DB seed + first refresh)
    private static final BigDecimal DEFAULT_ETH_KRW = new BigDecimal("5000000.0");
    private static final BigDecimal DEFAULT_USDT_KRW = new BigDecimal("1300.0");
    private static final BigDecimal RATE_KRWT = BigDecimal.ONE;

    // In-memory cache for internal services (swap/exchange calculations etc).
    // This cache is updated on DB read and on refresh success.
    private final AtomicReference<BigDecimal> cachedEthRate = new AtomicReference<>(DEFAULT_ETH_KRW);
    private final AtomicReference<BigDecimal> cachedUsdtRate = new AtomicReference<>(DEFAULT_USDT_KRW);
    private final AtomicReference<Instant> cachedUpdatedAt = new AtomicReference<>(Instant.now());

    // Prevent overlapping refresh jobs
    private final AtomicReference<Future<Void>> refreshInFlight = new AtomicReference<>(null);

    public CurrencyService(PgPool pool,
                           CurrencyRepository currencyRepository,
                           ExchangeRateRepository exchangeRateRepository,
                           WebClient webClient) {
        super(pool);
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.webClient = webClient;
    }

    /**
     * Public API: returns KRW per 1 unit of each currency (e.g. 1 USDT -> KRW).
     * Source: DB.
     */
    public Future<ExchangeRatesDto> getExchangeRates() {
        return exchangeRateRepository.getExchangeRates(pool)
            .map(dto -> {
                applyToCache(dto);
                // Always ensure KRWT exists for clients.
                if (dto.getRates() != null) {
                    dto.getRates().putIfAbsent("KRWT", RATE_KRWT);
                }
                return dto;
            })
            .recover(err -> {
                log.warn("Exchange rates DB read failed; serving cached values. Error: {}", err.getMessage());
                return Future.succeededFuture(buildRatesFromCache());
            });
    }

    /**
     * Scheduler entrypoint: fetch latest rates from external providers and upsert into DB.
     * This method never blocks request handlers; it should be called periodically from Vert.x setPeriodic.
     */
    public Future<Void> refreshExchangeRates() {
        Future<Void> inFlight = refreshInFlight.get();
        if (inFlight != null && !inFlight.isComplete()) {
            return inFlight;
        }

        Promise<Void> promise = Promise.promise();
        Future<Void> created = promise.future();

        if (!refreshInFlight.compareAndSet(inFlight, created)) {
            Future<Void> raced = refreshInFlight.get();
            return raced != null ? raced : Future.succeededFuture();
        }

        fetchFromProviders()
            .compose(fetch -> {
                Map<String, BigDecimal> providerRates = fetch.rates();
                // Persist provider rates with provider source
                Future<Void> upsertProvider = exchangeRateRepository.upsertRates(pool, providerRates, fetch.source());
                // Persist KRWT always as 1.0 (internal)
                Map<String, BigDecimal> krwt = Map.of("KRWT", RATE_KRWT);
                Future<Void> upsertKrwt = exchangeRateRepository.upsertRates(pool, krwt, "INTERNAL");

                return CompositeFuture.all(upsertProvider, upsertKrwt).mapEmpty()
                    .onSuccess(v -> {
                        Instant now = Instant.now();
                        applyToCache(ExchangeRatesDto.builder().rates(providerRates).updatedAt(now).build());
                        cachedUpdatedAt.set(now);
                        log.info("Exchange rates refreshed and persisted. source={}, rates={}", fetch.source(), providerRates.keySet());
                    });
            })
            .onComplete(ar -> {
                refreshInFlight.set(null);
                if (ar.succeeded()) {
                    promise.complete();
                } else {
                    log.warn("Exchange rate refresh failed; DB not updated. Error: {}", ar.cause().getMessage());
                    promise.fail(ar.cause());
                }
            });

        return created;
    }

    private Future<FetchResult> fetchFromProviders() {
        // Primary: CoinGecko (ethereum,tether vs krw)
        return fetchFromCoinGecko()
            // Secondary: CoinPaprika (eth-ethereum/usdt-tether quotes=KRW)
            .recover(err -> {
                log.warn("CoinGecko fetch failed, trying CoinPaprika. Error: {}", err.getMessage());
                return fetchFromCoinPaprika();
            });
    }

    private Future<FetchResult> fetchFromCoinGecko() {
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

        return req.send()
            .compose(res -> {
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

                return Future.succeededFuture(new FetchResult("COINGECKO", rates));
            });
    }

    private Future<FetchResult> fetchFromCoinPaprika() {
        // /v1/tickers/{coinId}?quotes=KRW
        Future<BigDecimal> ethF = fetchCoinPaprikaKrwPrice("eth-ethereum").recover(e -> Future.succeededFuture(null));
        Future<BigDecimal> usdtF = fetchCoinPaprikaKrwPrice("usdt-tether").recover(e -> Future.succeededFuture(null));

        return CompositeFuture.all(ethF, usdtF)
            .compose(cf -> {
                Map<String, BigDecimal> rates = new HashMap<>();
                BigDecimal eth = cf.resultAt(0);
                BigDecimal usdt = cf.resultAt(1);

                if (eth != null) rates.put("ETH", eth);
                if (usdt != null) rates.put("USDT", usdt);

                if (rates.isEmpty()) {
                    return Future.failedFuture("CoinPaprika response missing ETH/USDT krw");
                }
                return Future.succeededFuture(new FetchResult("COINPAPRIKA", rates));
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

    private void applyToCache(ExchangeRatesDto dto) {
        if (dto == null || dto.getRates() == null) return;

        BigDecimal eth = dto.getRates().get("ETH");
        if (eth != null) cachedEthRate.set(eth);

        BigDecimal usdt = dto.getRates().get("USDT");
        if (usdt != null) cachedUsdtRate.set(usdt);

        if (dto.getUpdatedAt() != null) cachedUpdatedAt.set(dto.getUpdatedAt());
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

    private record FetchResult(String source, Map<String, BigDecimal> rates) {}
}
