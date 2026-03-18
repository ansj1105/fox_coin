package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 출금 워커(coin_publish 등)용 내부 API.
 * API 키로 인증 후: 제출(txHash), 컨펌 완료, 실패 처리.
 */
@Slf4j
public class InternalWithdrawalHandler extends BaseHandler {

    private final TransferService transferService;
    private final String internalApiKey;

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    public InternalWithdrawalHandler(Vertx vertx, TransferService transferService, String internalApiKey) {
        super(vertx);
        this.transferService = transferService;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.route().handler(this::checkInternalApiKey);
        router.get("/").handler(this::listByStatus);
        router.post("/coin-manage/:withdrawalId/state").handler(this::syncCoinManageState);
        router.post("/:transferId/submit").handler(this::submit);
        router.post("/:transferId/confirm").handler(this::confirm);
        router.post("/:transferId/fail").handler(this::fail);
        return router;
    }

    private void listByStatus(RoutingContext ctx) {
        String status = ctx.request().getParam("status");
        if (status == null || status.isBlank()) {
            status = "SUBMITTED";
        }
        int limit = 100;
        String limitParam = ctx.request().getParam("limit");
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                limit = Math.min(500, Math.max(1, Integer.parseInt(limitParam)));
            } catch (NumberFormatException ignored) { }
        }
        response(ctx, transferService.listExternalTransfersByStatus(status, limit));
    }

    private void checkInternalApiKey(RoutingContext ctx) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            log.warn("Internal withdrawal API key not configured");
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Internal API not configured\"}");
            return;
        }
        String key = ctx.request().getHeader(HEADER_API_KEY);
        if (!internalApiKey.equals(key)) {
            log.warn("Invalid or missing internal API key");
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Unauthorized\"}");
            return;
        }
        ctx.next();
    }

    private void submit(RoutingContext ctx) {
        String transferId = ctx.pathParam("transferId");
        if (transferId == null || transferId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("transferId required"));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        String txHash = body.getString("txHash");
        if (txHash == null || txHash.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("txHash required"));
            return;
        }
        log.info("Internal withdrawal submit: transferId={}, txHash={}", transferId, txHash);
        response(ctx, transferService.submitExternalTransfer(transferId, txHash));
    }

    private void syncCoinManageState(RoutingContext ctx) {
        String withdrawalId = ctx.pathParam("withdrawalId");
        if (withdrawalId == null || withdrawalId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("withdrawalId required"));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        String status = body.getString("status");
        if (status == null || status.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("status required"));
            return;
        }

        log.info("coin_manage withdrawal state sync: withdrawalId={}, status={}", withdrawalId, status);
        response(ctx, transferService.syncCoinManageWithdrawalState(
            withdrawalId,
            status,
            body.getString("txHash"),
            body.getInteger("requiredConfirmations"),
            body.getString("failReason")
        ));
    }

    private void confirm(RoutingContext ctx) {
        String transferId = ctx.pathParam("transferId");
        if (transferId == null || transferId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("transferId required"));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            body = new JsonObject();
        }
        int confirmations = body.getInteger("confirmations", 20);
        log.info("Internal withdrawal confirm: transferId={}, confirmations={}", transferId, confirmations);
        response(ctx, transferService.confirmExternalTransfer(transferId, confirmations));
    }

    private void fail(RoutingContext ctx) {
        String transferId = ctx.pathParam("transferId");
        if (transferId == null || transferId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("transferId required"));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        String errorCode = body.getString("errorCode", "UNKNOWN");
        String errorMessage = body.getString("errorMessage", "");
        log.info("Internal withdrawal fail: transferId={}, errorCode={}", transferId, errorCode);
        response(ctx, transferService.failExternalTransferAndRefund(transferId, errorCode, errorMessage));
    }
}
