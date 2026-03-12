package com.foxya.coin.currency;

import com.foxya.coin.common.BaseHandler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
        router.get("/coin-prices/stream")
            .handler(this::streamCoinPrices);
        
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
        Set<String> codes = parseRequestedCodes(ctx);
        log.info("코인 가격 조회 요청 - codes={}", codes);
        response(ctx, currencyService.getCoinPrices(codes));
    }

    private void streamCoinPrices(RoutingContext ctx) {
        Set<String> codes = parseRequestedCodes(ctx);
        log.info("코인 가격 SSE 구독 요청 - codes={}", codes);

        ctx.response()
            .setChunked(true)
            .setStatusCode(200)
            .putHeader("Content-Type", "text/event-stream; charset=utf-8")
            .putHeader("Cache-Control", "no-cache")
            .putHeader("Connection", "keep-alive")
            .putHeader("X-Accel-Buffering", "no");

        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<String> subscriberIdRef = new AtomicReference<>();
        String subscriberId = currencyService.subscribeCoinPrices(codes, dto -> {
            if (closed.get()) {
                return;
            }
            try {
                ctx.response().write("event: coinPrices\n");
                ctx.response().write("data: " + Json.encode(dto) + "\n\n");
            } catch (Exception e) {
                log.warn("코인 가격 SSE write 실패 - codes={}, error={}", codes, e.getMessage());
                closeCoinPriceStream(ctx, subscriberIdRef.get(), closed, null);
            }
        });
        subscriberIdRef.set(subscriberId);

        long heartbeatTimerId = getVertx().setPeriodic(30_000L, id -> {
            if (closed.get()) {
                getVertx().cancelTimer(id);
                return;
            }
            try {
                ctx.response().write(": ping\n\n");
            } catch (Exception e) {
                log.warn("코인 가격 SSE heartbeat 실패 - codes={}, error={}", codes, e.getMessage());
                closeCoinPriceStream(ctx, subscriberId, closed, id);
            }
        });

        ctx.request().connection().closeHandler(v -> closeCoinPriceStream(ctx, subscriberId, closed, heartbeatTimerId));
        ctx.request().connection().exceptionHandler(err -> closeCoinPriceStream(ctx, subscriberId, closed, heartbeatTimerId));
        ctx.response().exceptionHandler(err -> closeCoinPriceStream(ctx, subscriberId, closed, heartbeatTimerId));
        ctx.response().endHandler(v -> closeCoinPriceStream(ctx, subscriberId, closed, heartbeatTimerId));

        currencyService.getCoinPrices(codes)
            .onSuccess(dto -> {
                if (closed.get()) {
                    return;
                }
                ctx.response().write("event: coinPrices\n");
                ctx.response().write("data: " + Json.encode(dto) + "\n\n");
            })
            .onFailure(err -> {
                log.warn("코인 가격 SSE 초기 snapshot 로드 실패 - codes={}, error={}", codes, err.getMessage());
                closeCoinPriceStream(ctx, subscriberId, closed, heartbeatTimerId);
            });
    }

    private Set<String> parseRequestedCodes(RoutingContext ctx) {
        String codesParam = ctx.request().getParam("codes");
        return codesParam == null || codesParam.isBlank()
            ? Set.of()
            : Arrays.stream(codesParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private void closeCoinPriceStream(RoutingContext ctx, String subscriberId, AtomicBoolean closed, Long heartbeatTimerId) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        currencyService.unsubscribeCoinPrices(subscriberId);
        if (heartbeatTimerId != null) {
            getVertx().cancelTimer(heartbeatTimerId);
        }
        if (!ctx.response().ended()) {
            ctx.response().end();
        }
    }
}
