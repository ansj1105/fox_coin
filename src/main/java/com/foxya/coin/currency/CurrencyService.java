package com.foxya.coin.currency;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.currency.dto.ExchangeRatesDto;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 통화 관련 서비스
 */
@Slf4j
public class CurrencyService extends BaseService {
    
    private final CurrencyRepository currencyRepository;
    private final WebClient webClient;
    
    // CoinGecko API URL (무료, API 키 불필요)
    private static final String COINGECKO_API_URL = "https://api.coingecko.com/api/v3/simple/price";
    
    // 임시 환율 (API 실패 시 사용)
    private static final BigDecimal RATE_ETH = new BigDecimal("5000000.0");
    private static final BigDecimal RATE_USDT = new BigDecimal("1300.0");
    private static final BigDecimal RATE_KRWT = BigDecimal.ONE;
    
    public CurrencyService(PgPool pool, CurrencyRepository currencyRepository, WebClient webClient) {
        super(pool);
        this.currencyRepository = currencyRepository;
        this.webClient = webClient;
    }
    
    /**
     * 통화별 한화(KRW) 환율 조회
     */
    public Future<ExchangeRatesDto> getExchangeRates() {
        // CoinGecko API 호출 (ETH, USDT의 KRW 가격)
        String ids = "ethereum,tether";
        String vsCurrencies = "krw";
        String url = String.format("%s?ids=%s&vs_currencies=%s", COINGECKO_API_URL, ids, vsCurrencies);
        
        return webClient.getAbs(url)
            .send()
            .compose(response -> {
                if (response.statusCode() == 200) {
                    JsonObject body = response.bodyAsJsonObject();
                    Map<String, BigDecimal> rates = new HashMap<>();
                    
                    // ETH 환율
                    if (body.containsKey("ethereum") && body.getJsonObject("ethereum").containsKey("krw")) {
                        BigDecimal ethRate = new BigDecimal(body.getJsonObject("ethereum").getDouble("krw").toString());
                        rates.put("ETH", ethRate);
                    } else {
                        rates.put("ETH", RATE_ETH);
                    }
                    
                    // USDT 환율
                    if (body.containsKey("tether") && body.getJsonObject("tether").containsKey("krw")) {
                        BigDecimal usdtRate = new BigDecimal(body.getJsonObject("tether").getDouble("krw").toString());
                        rates.put("USDT", usdtRate);
                    } else {
                        rates.put("USDT", RATE_USDT);
                    }
                    
                    // KRWT는 항상 1.0
                    rates.put("KRWT", RATE_KRWT);
                    
                    return Future.succeededFuture(ExchangeRatesDto.builder()
                        .rates(rates)
                        .updatedAt(Instant.now())
                        .build());
                } else {
                    log.warn("CoinGecko API 호출 실패 (status: {}), 임시 환율 사용", response.statusCode());
                    return getFallbackRates();
                }
            })
            .recover(throwable -> {
                log.error("CoinGecko API 호출 중 오류 발생, 임시 환율 사용", throwable);
                return getFallbackRates();
            });
    }
    
    /**
     * 임시 환율 반환 (API 실패 시)
     */
    private Future<ExchangeRatesDto> getFallbackRates() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("ETH", RATE_ETH);
        rates.put("USDT", RATE_USDT);
        rates.put("KRWT", RATE_KRWT);
        
        return Future.succeededFuture(ExchangeRatesDto.builder()
            .rates(rates)
            .updatedAt(Instant.now())
            .build());
    }
    
    /**
     * 통화별 KRWT 기준 환율 조회 (동기 메서드, 캐시된 환율 사용)
     * SwapService에서 사용하기 위한 메서드
     */
    public BigDecimal getRateForCurrency(String currencyCode) {
        switch (currencyCode) {
            case "ETH":
                return RATE_ETH;
            case "USDT":
                return RATE_USDT;
            case "KRWT":
                return RATE_KRWT;
            default:
                // 기본값: 1:1
                return BigDecimal.ONE;
        }
    }
    
    /**
     * 두 통화 간 환율 계산
     * fromCurrency -> KRWT -> toCurrency 환율 계산
     */
    public BigDecimal getExchangeRate(String fromCurrencyCode, String toCurrencyCode) {
        BigDecimal fromRate = getRateForCurrency(fromCurrencyCode);
        BigDecimal toRate = getRateForCurrency(toCurrencyCode);
        
        // fromCurrency -> KRWT -> toCurrency 환율 계산
        // 예: ETH -> USDT = (ETH/KRWT) / (USDT/KRWT) = 5000000 / 1300 = 3846.15...
        return fromRate.divide(toRate, 18, RoundingMode.HALF_UP);
    }
}

