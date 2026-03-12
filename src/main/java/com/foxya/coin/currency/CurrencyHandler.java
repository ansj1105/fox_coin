package com.foxya.coin.currency;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.currency.dto.CoinPricesDto;
import com.foxya.coin.currency.dto.ExchangeRatesDto;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 통화 관련 API 핸들러
 */
@Slf4j
public class CurrencyHandler extends BaseHandler {
    
    private final CurrencyService currencyService;
    private final JWTAuth jwtAuth;
    
    public CurrencyHandler(Vertx vertx, CurrencyService currencyService, JWTAuth jwtAuth) {
        super(vertx);
        this.currencyService = currencyService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 환율 조회 (인증 불필요 - 공개 API)
        router.get("/exchange-rates")
            .handler(this::getExchangeRates);
        router.get("/coin-prices")
            .handler(this::getCoinPrices);
        
        return router;
    }
    
    /**
     * 통화별 한화(KRW) 환율 조회
     */
    private void getExchangeRates(RoutingContext ctx) {
        log.info("환율 조회 요청");
        response(ctx, currencyService.getExchangeRates());
    }

    private void getCoinPrices(RoutingContext ctx) {
        String codesParam = ctx.request().getParam("codes");
        Set<String> codes = codesParam == null || codesParam.isBlank()
            ? Set.of()
            : Arrays.stream(codesParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        log.info("코인 가격 조회 요청 - codes={}", codes);
        response(ctx, currencyService.getCoinPrices(codes));
    }
}
