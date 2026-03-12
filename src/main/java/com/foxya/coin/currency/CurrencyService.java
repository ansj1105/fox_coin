package com.foxya.coin.currency;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.currency.dto.CoinPriceDto;
import com.foxya.coin.currency.dto.CoinPricesDto;
import com.foxya.coin.currency.dto.ExchangeRatesDto;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private final CoinPriceRepository coinPriceRepository;
    private final WebClient webClient;

    // Providers
    private static final String DEFAULT_COINGECKO_API_URL = "https://api.coingecko.com/api/v3/simple/price";
    private static final String DEFAULT_COINPAPRIKA_API_URL = "https://api.coinpaprika.com/v1/tickers";
    private static final String DEFAULT_TRONGRID_API_URL = "https://api.trongrid.io";

    private final String coingeckoApiUrl;
    private final String coinpaprikaApiUrl;
    private final String trongridApiUrl;

    private static final long HTTP_TIMEOUT_MS = parseLongEnv("EXCHANGE_RATE_HTTP_TIMEOUT_MS", 10_000L);
    private static final long COIN_PRICE_STALE_MS = parseLongEnv("COIN_PRICE_STALE_MS", 60_000L);
    private static final String KORI_SUNSWAP_PAIR_ADDRESS = "TCHbWJUBZ9DVpaPb6QW9vb31yTSz7sfhQh";
    private static final BigDecimal TRON_SUN_UNIT = new BigDecimal("1000000");
    private static final Set<String> SUPPORTED_COIN_PRICE_CODES = Set.of("BTC", "ETH", "USDT", "TRX", "SOL", "KORI", "KORION");

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
    private final AtomicReference<Future<Void>> coinPriceRefreshInFlight = new AtomicReference<>(null);
    private final ConcurrentHashMap<String, CoinPriceStreamSubscriber> coinPriceSubscribers = new ConcurrentHashMap<>();

    public CurrencyService(PgPool pool,
                           CurrencyRepository currencyRepository,
                           ExchangeRateRepository exchangeRateRepository,
                           CoinPriceRepository coinPriceRepository,
                           WebClient webClient) {
        this(pool, currencyRepository, exchangeRateRepository, coinPriceRepository, webClient, DEFAULT_COINGECKO_API_URL, DEFAULT_COINPAPRIKA_API_URL, DEFAULT_TRONGRID_API_URL);
    }

    // Visible for tests: lets unit tests point providers to a local mock server.
    CurrencyService(PgPool pool,
                    CurrencyRepository currencyRepository,
                    ExchangeRateRepository exchangeRateRepository,
                    CoinPriceRepository coinPriceRepository,
                    WebClient webClient,
                    String coingeckoApiUrl,
                    String coinpaprikaApiUrl,
                    String trongridApiUrl) {
        super(pool);
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.coinPriceRepository = coinPriceRepository;
        this.webClient = webClient;
        this.coingeckoApiUrl = (coingeckoApiUrl != null && !coingeckoApiUrl.isBlank()) ? coingeckoApiUrl.trim() : DEFAULT_COINGECKO_API_URL;
        this.coinpaprikaApiUrl = (coinpaprikaApiUrl != null && !coinpaprikaApiUrl.isBlank()) ? coinpaprikaApiUrl.trim() : DEFAULT_COINPAPRIKA_API_URL;
        this.trongridApiUrl = (trongridApiUrl != null && !trongridApiUrl.isBlank()) ? trongridApiUrl.trim() : DEFAULT_TRONGRID_API_URL;

        // Seed cache with coarse defaults so internal calculations keep working even if DB is temporarily unavailable.
        cachedRates.put("ETH", DEFAULT_ETH_KRW);
        cachedRates.put("USDT", DEFAULT_USDT_KRW);
        cachedRates.put("BTC", DEFAULT_BTC_KRW);
        cachedRates.put("TRX", DEFAULT_TRX_KRW);
        cachedRates.put("KRWT", RATE_KRWT);
    }

    public Future<CoinPricesDto> getCoinPrices(Set<String> requestedCodes) {
        return getCoinPricesFromDb(requestedCodes);
    }

    public String subscribeCoinPrices(Set<String> requestedCodes, Consumer<CoinPricesDto> listener) {
        Set<String> codes = normalizeCoinPriceCodes(requestedCodes);
        String subscriberId = UUID.randomUUID().toString();
        coinPriceSubscribers.put(subscriberId, new CoinPriceStreamSubscriber(codes, listener));
        return subscriberId;
    }

    public void unsubscribeCoinPrices(String subscriberId) {
        if (subscriberId == null || subscriberId.isBlank()) {
            return;
        }
        coinPriceSubscribers.remove(subscriberId);
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

    public Future<Void> refreshCoinPrices(Set<String> requestedCodes) {
        Future<Void> inFlight = coinPriceRefreshInFlight.get();
        if (inFlight != null && !inFlight.isComplete()) {
            return inFlight;
        }

        Promise<Void> promise = Promise.promise();
        Future<Void> created = promise.future();

        if (!coinPriceRefreshInFlight.compareAndSet(inFlight, created)) {
            Future<Void> raced = coinPriceRefreshInFlight.get();
            return raced != null ? raced : Future.succeededFuture();
        }

        Set<String> codes = normalizeCoinPriceCodes(requestedCodes);

        fetchCoinPricesFromProviders(codes)
            .compose(prices -> coinPriceRepository.upsertCoinPrices(pool, prices))
            .onComplete(ar -> {
                coinPriceRefreshInFlight.set(null);
                if (ar.succeeded()) {
                    broadcastCoinPriceSnapshot();
                    promise.complete();
                } else {
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

    private Future<Map<String, CoinPriceDto>> fetchCoinPricesFromProviders(Set<String> requestedCodes) {
        Set<String> codes = canonicalizeCoinPriceCodes(requestedCodes);
        return fetchUsdPricesFromCoinGecko(codes)
            .compose(prices -> {
                if (!codes.contains("KORI")) {
                    return Future.succeededFuture(prices);
                }

                BigDecimal trxUsd = prices.containsKey("TRX") ? prices.get("TRX").getUsdPrice() : null;
                if (trxUsd == null) {
                    return Future.succeededFuture(prices);
                }

                return fetchKoriPriceFromSunSwap(trxUsd)
                    .map(koriPrice -> {
                        if (koriPrice != null) {
                            prices.put("KORI", koriPrice);
                        }
                        return prices;
                    })
                    .recover(err -> {
                        log.warn("Failed to fetch KORI price from SunSwap: {}", err.getMessage());
                        return Future.succeededFuture(prices);
                    });
            });
    }

    private Future<Map<String, CoinPriceDto>> fetchUsdPricesFromCoinGecko(Set<String> requestedCodes) {
        Map<String, String> geckoIdMap = Map.of(
            "BTC", "bitcoin",
            "ETH", "ethereum",
            "USDT", "tether",
            "TRX", "tron",
            "SOL", "solana"
        );

        List<String> ids = new ArrayList<>();
        Map<String, String> reverseMap = new HashMap<>();
        for (String code : requestedCodes) {
            String canonical = "KORION".equals(code) ? "KORI" : code;
            String geckoId = geckoIdMap.get(canonical);
            if (geckoId == null) {
                continue;
            }
            ids.add(geckoId);
            reverseMap.put(geckoId, canonical);
        }

        if (ids.isEmpty()) {
            return Future.succeededFuture(new HashMap<>());
        }

        String url = coingeckoApiUrl + "?ids=" + String.join(",", ids) + "&vs_currencies=usd&include_24hr_change=true";
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
                return Future.failedFuture("CoinGecko coin prices non-200: status=" + res.statusCode());
            }

            JsonObject json;
            try {
                json = res.bodyAsJsonObject();
            } catch (Exception e) {
                return Future.failedFuture("CoinGecko coin prices invalid JSON: " + e.getMessage());
            }

            Map<String, CoinPriceDto> prices = new HashMap<>();
            for (String geckoId : ids) {
                JsonObject item = json.getJsonObject(geckoId);
                String code = reverseMap.get(geckoId);
                if (item == null || code == null || item.getValue("usd") == null) {
                    continue;
                }

                BigDecimal usd = extractNestedDecimal(json, geckoId, "usd");
                BigDecimal change = extractNestedDecimal(json, geckoId, "usd_24h_change");
                if (usd == null) {
                    continue;
                }

                prices.put(code, CoinPriceDto.builder()
                    .usdPrice(usd)
                    .change24hPercent(change != null ? change : BigDecimal.ZERO)
                    .source("COINGECKO")
                    .updatedAt(Instant.now())
                    .build());
            }
            return Future.succeededFuture(prices);
        });
    }

    private Future<CoinPriceDto> fetchKoriPriceFromSunSwap(BigDecimal trxUsdPrice) {
        String url = trongridApiUrl + "/wallet/triggerconstantcontract";
        JsonObject payload = new JsonObject()
            .put("owner_address", KORI_SUNSWAP_PAIR_ADDRESS)
            .put("contract_address", KORI_SUNSWAP_PAIR_ADDRESS)
            .put("function_selector", "getReserves()")
            .put("visible", true);

        return webClient.postAbs(url)
            .timeout(HTTP_TIMEOUT_MS)
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json")
            .putHeader("User-Agent", "foxya-coin-service/1.0")
            .sendJsonObject(payload)
            .compose(res -> {
                if (res.statusCode() != 200) {
                    return Future.failedFuture("TronGrid getReserves non-200: status=" + res.statusCode());
                }

                JsonObject json;
                try {
                    json = res.bodyAsJsonObject();
                } catch (Exception e) {
                    return Future.failedFuture("TronGrid getReserves invalid JSON: " + e.getMessage());
                }

                String raw = json.getJsonArray("constant_result") != null && !json.getJsonArray("constant_result").isEmpty()
                    ? json.getJsonArray("constant_result").getString(0)
                    : null;
                if (raw == null || raw.isBlank() || raw.length() < 128) {
                    return Future.failedFuture("TronGrid getReserves missing constant_result");
                }

                BigInteger reserve0 = decodeUint256(raw.substring(0, 64));
                BigInteger reserve1 = decodeUint256(raw.substring(64, 128));
                if (reserve0 == null || reserve1 == null || reserve0.signum() <= 0 || reserve1.signum() <= 0) {
                    return Future.failedFuture("Invalid SunSwap reserves");
                }

                BigDecimal wtrxPerKori = new BigDecimal(reserve1)
                    .divide(new BigDecimal(reserve0), 18, RoundingMode.HALF_UP);
                BigDecimal usdPrice = wtrxPerKori.multiply(trxUsdPrice).setScale(10, RoundingMode.HALF_UP);

                return Future.succeededFuture(CoinPriceDto.builder()
                    .usdPrice(usdPrice)
                    .change24hPercent(BigDecimal.ZERO)
                    .source("SUNSWAP")
                    .updatedAt(Instant.now())
                    .build());
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

    private boolean needsCoinPriceRefresh(CoinPricesDto dto, Set<String> requestedCodes) {
        if (dto == null || dto.getPrices() == null) {
            return true;
        }

        Instant now = Instant.now();
        for (String requestedCode : requestedCodes) {
            String lookupCode = "KORION".equals(requestedCode) ? "KORI" : requestedCode;
            CoinPriceDto dtoItem = dto.getPrices().get(lookupCode);
            if (dtoItem == null || dtoItem.getUpdatedAt() == null) {
                return true;
            }
            if (dtoItem.getUpdatedAt().plusMillis(COIN_PRICE_STALE_MS).isBefore(now)) {
                return true;
            }
        }
        return false;
    }

    private CoinPricesDto applyCoinPriceAliases(CoinPricesDto dto, Set<String> requestedCodes) {
        if (dto == null) {
            return CoinPricesDto.builder().prices(Map.of()).updatedAt(Instant.now()).build();
        }

        Map<String, CoinPriceDto> prices = new HashMap<>(dto.getPrices() != null ? dto.getPrices() : Map.of());
        if (requestedCodes.contains("KORION") && prices.containsKey("KORI")) {
            prices.put("KORION", prices.get("KORI"));
        }

        return CoinPricesDto.builder()
            .prices(prices)
            .updatedAt(dto.getUpdatedAt())
            .build();
    }

    private Set<String> normalizeCoinPriceCodes(Set<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return Set.of("BTC", "ETH", "USDT", "TRX", "SOL", "KORI");
        }

        Set<String> normalized = new HashSet<>();
        for (String code : requestedCodes) {
            if (code == null || code.isBlank()) continue;
            String upper = code.trim().toUpperCase();
            if (SUPPORTED_COIN_PRICE_CODES.contains(upper)) {
                normalized.add(upper);
            }
        }
        return normalized;
    }

    private Set<String> canonicalizeCoinPriceCodes(Set<String> requestedCodes) {
        Set<String> canonical = new HashSet<>();
        for (String code : requestedCodes) {
            if ("KORION".equals(code)) {
                canonical.add("KORI");
            } else {
                canonical.add(code);
            }
        }
        return canonical;
    }

    private Future<CoinPricesDto> getCoinPricesFromDb(Set<String> requestedCodes) {
        Set<String> codes = normalizeCoinPriceCodes(requestedCodes);
        if (codes.isEmpty()) {
            return Future.succeededFuture(CoinPricesDto.builder()
                .prices(Map.of())
                .updatedAt(Instant.now())
                .build());
        }

        return coinPriceRepository.getCoinPrices(pool, canonicalizeCoinPriceCodes(codes))
            .map(dto -> applyCoinPriceAliases(dto, codes))
            .recover(err -> {
                log.warn("Coin price DB read failed; serving empty values. Error: {}", err.getMessage());
                return Future.succeededFuture(CoinPricesDto.builder()
                    .prices(Map.of())
                    .updatedAt(Instant.now())
                    .build());
            });
    }

    private void broadcastCoinPriceSnapshot() {
        if (coinPriceSubscribers.isEmpty()) {
            return;
        }

        Set<String> requestedCodes = new HashSet<>();
        coinPriceSubscribers.values().forEach(subscriber -> requestedCodes.addAll(subscriber.codes()));
        if (requestedCodes.isEmpty()) {
            requestedCodes.addAll(normalizeCoinPriceCodes(Set.of()));
        }

        getCoinPricesFromDb(requestedCodes)
            .onSuccess(snapshot -> {
                coinPriceSubscribers.forEach((id, subscriber) -> {
                    try {
                        CoinPricesDto filtered = filterCoinPrices(snapshot, subscriber.codes());
                        subscriber.listener().accept(applyCoinPriceAliases(filtered, subscriber.codes()));
                    } catch (Exception e) {
                        log.warn("Coin price SSE publish failed for subscriber {}: {}", id, e.getMessage());
                    }
                });
            })
            .onFailure(err -> log.warn("Coin price SSE snapshot load failed: {}", err.getMessage()));
    }

    private CoinPricesDto filterCoinPrices(CoinPricesDto dto, Set<String> requestedCodes) {
        if (dto == null || dto.getPrices() == null || dto.getPrices().isEmpty()) {
            return CoinPricesDto.builder()
                .prices(Map.of())
                .updatedAt(dto != null ? dto.getUpdatedAt() : Instant.now())
                .build();
        }

        Set<String> canonicalCodes = canonicalizeCoinPriceCodes(requestedCodes);
        Map<String, CoinPriceDto> filtered = new HashMap<>();
        canonicalCodes.forEach(code -> {
            CoinPriceDto item = dto.getPrices().get(code);
            if (item != null) {
                filtered.put(code, item);
            }
        });

        return CoinPricesDto.builder()
            .prices(filtered)
            .updatedAt(dto.getUpdatedAt())
            .build();
    }

    private record CoinPriceStreamSubscriber(
        Set<String> codes,
        Consumer<CoinPricesDto> listener
    ) {}

    private static BigInteger decodeUint256(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        try {
            return new BigInteger(hex, 16);
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
