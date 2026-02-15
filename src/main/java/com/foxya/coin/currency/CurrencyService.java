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
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String DEFAULT_COINGECKO_API_URL = "https://api.coingecko.com/api/v3/simple/price";
    private static final String DEFAULT_COINPAPRIKA_API_URL = "https://api.coinpaprika.com/v1/tickers";

    private final String coingeckoApiUrl;
    private final String coinpaprikaApiUrl;

    private static final long HTTP_TIMEOUT_MS = parseLongEnv("EXCHANGE_RATE_HTTP_TIMEOUT_MS", 10_000L);

    // Optional CoinGecko API keys (header-based)
    private static final String COINGECKO_DEMO_KEY = System.getenv("COINGECKO_DEMO_API_KEY"); // x-cg-demo-api-key
    private static final String COINGECKO_PRO_KEY = System.getenv("COINGECKO_PRO_API_KEY");   // x-cg-pro-api-key

    // Defaults (used until DB seed + first refresh)
    // Keep these as coarse fallbacks only; scheduler refresh should overwrite quickly.
    private static final BigDecimal DEFAULT_ETH_KRW = new BigDecimal("5000000.0");
    private static final BigDecimal DEFAULT_USDT_KRW = new BigDecimal("1300.0");
    private static final BigDecimal DEFAULT_BTC_KRW = new BigDecimal("80000000.0");
    private static final BigDecimal DEFAULT_TRX_KRW = new BigDecimal("180.0");
    private static final BigDecimal RATE_KRWT = BigDecimal.ONE;

    // In-memory cache for internal services (swap/exchange calculations etc).
    // This cache is updated on DB read and on refresh success.
    private final ConcurrentHashMap<String, BigDecimal> cachedRates = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> cachedUpdatedAt = new AtomicReference<>(Instant.now());

    // Prevent overlapping refresh jobs
    private final AtomicReference<Future<Void>> refreshInFlight = new AtomicReference<>(null);

    public CurrencyService(PgPool pool,
                           CurrencyRepository currencyRepository,
                           ExchangeRateRepository exchangeRateRepository,
                           WebClient webClient) {
        this(pool, currencyRepository, exchangeRateRepository, webClient, DEFAULT_COINGECKO_API_URL, DEFAULT_COINPAPRIKA_API_URL);
    }

    // Visible for tests: lets unit tests point providers to a local mock server.
    CurrencyService(PgPool pool,
                    CurrencyRepository currencyRepository,
                    ExchangeRateRepository exchangeRateRepository,
                    WebClient webClient,
                    String coingeckoApiUrl,
                    String coinpaprikaApiUrl) {
        super(pool);
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.webClient = webClient;
        this.coingeckoApiUrl = (coingeckoApiUrl != null && !coingeckoApiUrl.isBlank()) ? coingeckoApiUrl.trim() : DEFAULT_COINGECKO_API_URL;
        this.coinpaprikaApiUrl = (coinpaprikaApiUrl != null && !coinpaprikaApiUrl.isBlank()) ? coinpaprikaApiUrl.trim() : DEFAULT_COINPAPRIKA_API_URL;

        // Seed cache with coarse defaults so internal calculations keep working even if DB is temporarily unavailable.
        cachedRates.put("ETH", DEFAULT_ETH_KRW);
        cachedRates.put("USDT", DEFAULT_USDT_KRW);
        cachedRates.put("BTC", DEFAULT_BTC_KRW);
        cachedRates.put("TRX", DEFAULT_TRX_KRW);
        cachedRates.put("KRWT", RATE_KRWT);
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

                // Persist internal rates (no external provider calls)
                Map<String, BigDecimal> internalRates = new HashMap<>();
                internalRates.put("KRWT", RATE_KRWT);
                BigDecimal koriKrw = parseDecimalEnv("KORI_KRW_RATE");
                if (koriKrw != null) {
                    internalRates.put("KORI", koriKrw);
                }
                Future<Void> upsertInternal = exchangeRateRepository.upsertRates(pool, internalRates, "INTERNAL");

                return CompositeFuture.all(upsertProvider, upsertInternal).mapEmpty()
                    .onSuccess(v -> {
                        Instant now = Instant.now();
                        Map<String, BigDecimal> merged = new HashMap<>(providerRates);
                        merged.putAll(internalRates);
                        applyToCache(ExchangeRatesDto.builder().rates(merged).updatedAt(now).build());
                        cachedUpdatedAt.set(now);
                        log.info("Exchange rates refreshed and persisted. providerSource={}, rates={}", fetch.source(), merged.keySet());
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
        String url = coingeckoApiUrl + "?ids=ethereum,tether,bitcoin,tron&vs_currencies=krw";

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

                BigDecimal btc = extractNestedDecimal(json, "bitcoin", "krw");
                if (btc != null) rates.put("BTC", btc);

                BigDecimal trx = extractNestedDecimal(json, "tron", "krw");
                if (trx != null) rates.put("TRX", trx);

                if (rates.isEmpty()) {
                    return Future.failedFuture("CoinGecko response missing rates");
                }

                return Future.succeededFuture(new FetchResult("COINGECKO", rates));
            });
    }

    private Future<FetchResult> fetchFromCoinPaprika() {
        // /v1/tickers/{coinId}?quotes=KRW
        Future<BigDecimal> ethF = fetchCoinPaprikaKrwPrice("eth-ethereum").recover(e -> Future.succeededFuture(null));
        Future<BigDecimal> usdtF = fetchCoinPaprikaKrwPrice("usdt-tether").recover(e -> Future.succeededFuture(null));
        Future<BigDecimal> btcF = fetchCoinPaprikaKrwPrice("btc-bitcoin").recover(e -> Future.succeededFuture(null));
        Future<BigDecimal> trxF = fetchCoinPaprikaKrwPrice("trx-tron").recover(e -> Future.succeededFuture(null));

        return CompositeFuture.all(ethF, usdtF, btcF, trxF)
            .compose(cf -> {
                Map<String, BigDecimal> rates = new HashMap<>();
                BigDecimal eth = cf.resultAt(0);
                BigDecimal usdt = cf.resultAt(1);
                BigDecimal btc = cf.resultAt(2);
                BigDecimal trx = cf.resultAt(3);

                if (eth != null) rates.put("ETH", eth);
                if (usdt != null) rates.put("USDT", usdt);
                if (btc != null) rates.put("BTC", btc);
                if (trx != null) rates.put("TRX", trx);

                if (rates.isEmpty()) {
                    return Future.failedFuture("CoinPaprika response missing rates");
                }
                return Future.succeededFuture(new FetchResult("COINPAPRIKA", rates));
            });
    }

    private Future<BigDecimal> fetchCoinPaprikaKrwPrice(String coinId) {
        String url = coinpaprikaApiUrl + "/" + coinId + "?quotes=KRW";

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

        dto.getRates().forEach((k, v) -> {
            if (k == null || v == null) return;
            cachedRates.put(k.toUpperCase(), v);
        });

        if (dto.getUpdatedAt() != null) cachedUpdatedAt.set(dto.getUpdatedAt());
    }

    private ExchangeRatesDto buildRatesFromCache() {
        Map<String, BigDecimal> rates = new HashMap<>(cachedRates);
        rates.putIfAbsent("KRWT", RATE_KRWT);

        return ExchangeRatesDto.builder()
            .rates(rates)
            .updatedAt(cachedUpdatedAt.get())
            .build();
    }

    public BigDecimal getRateForCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return BigDecimal.ONE;
        String code = currencyCode.trim().toUpperCase();
        if ("KRWT".equals(code)) return RATE_KRWT;
        return cachedRates.getOrDefault(code, BigDecimal.ONE);
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

    private static BigDecimal parseDecimalEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record FetchResult(String source, Map<String, BigDecimal> rates) {}
}
