package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.level.LevelService;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InternalOfflinePayHandler extends BaseHandler {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    private final TransferService transferService;
    private final LevelService levelService;
    private final String internalApiKey;

    public InternalOfflinePayHandler(Vertx vertx, TransferService transferService, LevelService levelService, String internalApiKey) {
        super(vertx);
        this.transferService = transferService;
        this.levelService = levelService;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.route().handler(this::checkInternalApiKey);
        router.get("/store-product-policy").handler(this::getStoreProductPolicy);
        router.post("/settlements/history").handler(this::recordSettlementHistory);
        return router;
    }

    private void getStoreProductPolicy(RoutingContext ctx) {
        String rawUserId = ctx.queryParams().get("userId");
        if (rawUserId == null || rawUserId.isBlank()) {
            ctx.fail(new BadRequestException("userId required"));
            return;
        }
        try {
            Long userId = Long.parseLong(rawUserId);
            if (userId <= 0) {
                ctx.fail(new BadRequestException("userId must be positive"));
                return;
            }
            levelService.getOfflinePayStorePolicy(userId)
                .map(policy -> policy.put("status", "OK"))
                .onSuccess(payload -> ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(payload.encode()))
                .onFailure(ctx::fail);
        } catch (NumberFormatException exception) {
            ctx.fail(new BadRequestException("userId must be numeric"));
        }
    }

    private void checkInternalApiKey(RoutingContext ctx) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Internal API not configured\"}");
            return;
        }
        String key = ctx.request().getHeader(HEADER_API_KEY);
        if (!internalApiKey.equals(key)) {
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Unauthorized\"}");
            return;
        }
        ctx.next();
    }

    private void recordSettlementHistory(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(new BadRequestException("Body required"));
            return;
        }
        Long userId = body.getLong("userId");
        String settlementId = body.getString("settlementId");
        String historyType = body.getString("historyType");
        String amountRaw = body.getString("amount");
        if (userId == null || settlementId == null || settlementId.isBlank() || historyType == null || historyType.isBlank() || amountRaw == null || amountRaw.isBlank()) {
            ctx.fail(new BadRequestException("userId, settlementId, historyType, amount required"));
            return;
        }
        // transferRef: unique transfer ID. Defaults to settlementId (sender). Receiver uses settlementId + ":R".
        String transferRef = body.getString("transferRef");
        if (transferRef == null || transferRef.isBlank()) {
            transferRef = settlementId;
        }

        response(ctx, transferService.recordOfflinePaySettlementHistory(
            userId,
            settlementId,
            transferRef,
            body.getString("batchId"),
            body.getString("collateralId"),
            body.getString("proofId"),
            body.getString("deviceId"),
            body.getString("assetCode"),
            amountRaw,
            body.getString("settlementStatus"),
            historyType
        ).map(ignored -> new JsonObject()
            .put("status", "OK")
            .put("message", "history recorded")));
    }
}
