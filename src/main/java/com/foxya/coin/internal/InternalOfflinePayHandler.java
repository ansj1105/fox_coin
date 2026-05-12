package com.foxya.coin.internal;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.Utils;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalOfflinePayHandler extends BaseHandler {

    private final InternalOfflinePayService service;
    private final String internalApiKey;

    public InternalOfflinePayHandler(Vertx vertx, InternalOfflinePayService service, String internalApiKey) {
        super(vertx);
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.post("/settlements/history")
            .handler(this::authenticate)
            .handler(this::recordSettlementHistory);
        return router;
    }

    private void authenticate(RoutingContext ctx) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            log.error("Internal offline-pay API key is not configured");
            ctx.fail(new UnauthorizedException("internal api key is not configured"));
            return;
        }

        String provided = ctx.request().getHeader("x-internal-api-key");
        if (!internalApiKey.equals(provided)) {
            ctx.fail(new UnauthorizedException("invalid internal api key"));
            return;
        }
        ctx.next();
    }

    private void recordSettlementHistory(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        OfflinePaySettlementHistoryRequest request = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(body),
            OfflinePaySettlementHistoryRequest.class
        );

        log.info("Offline-pay settlement history request - settlementId: {}, transferRef: {}, userId: {}, historyType: {}",
            request.settlementId(), request.transferRef(), request.userId(), request.historyType());

        response(ctx, service.recordSettlementHistory(request));
    }
}
