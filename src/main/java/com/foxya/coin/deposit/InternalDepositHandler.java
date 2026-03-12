package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.wallet.WalletRepository;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 입금 스캐너(coin_publish 등)용 내부 API.
 * API 키로 인증 후 감시 주소 목록 조회, 입금 감지 등록, 입금 완료 처리.
 */
@Slf4j
public class InternalDepositHandler extends BaseHandler {

    private final PgPool pool;
    private final WalletRepository walletRepository;
    private final TokenDepositService tokenDepositService;
    private final String depositScannerApiKey;

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    public InternalDepositHandler(Vertx vertx, PgPool pool, WalletRepository walletRepository,
                                  TokenDepositService tokenDepositService, String depositScannerApiKey) {
        super(vertx);
        this.pool = pool;
        this.walletRepository = walletRepository;
        this.tokenDepositService = tokenDepositService;
        this.depositScannerApiKey = depositScannerApiKey;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.route().handler(this::checkInternalApiKey);
        router.get("/watch-addresses").handler(this::getWatchAddresses);
        router.post("/register").handler(this::registerDeposit);
        router.post("/register-batch").handler(this::registerDepositBatch);
        router.post("/:depositId/complete").handler(this::completeDeposit);
        router.post("/complete-batch").handler(this::completeDepositBatch);
        router.get("/:depositId").handler(this::getDeposit);
        router.get("/sweeps/pending").handler(this::getPendingSweeps);
        router.post("/:depositId/sweep/submit").handler(this::submitSweep);
        router.post("/:depositId/sweep/fail").handler(this::failSweep);
        return router;
    }

    private void checkInternalApiKey(RoutingContext ctx) {
        if (depositScannerApiKey == null || depositScannerApiKey.isEmpty()) {
            log.warn("Internal deposit API key not configured");
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Internal API not configured\"}");
            return;
        }
        String key = ctx.request().getHeader(HEADER_API_KEY);
        if (!depositScannerApiKey.equals(key)) {
            log.warn("Invalid or missing internal API key");
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Unauthorized\"}");
            return;
        }
        ctx.next();
    }

    private void getWatchAddresses(RoutingContext ctx) {
        response(ctx, walletRepository.getDepositWatchAddresses(pool));
    }

    private void registerDeposit(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        String depositId = body.getString("depositId");
        Long userId = body.getLong("userId");
        Integer currencyId = body.getInteger("currencyId");
        String amountStr = body.getString("amount");
        String network = body.getString("network");
        String senderAddress = body.getString("senderAddress");
        String toAddress = body.getString("toAddress");
        Integer logIndex = body.getInteger("logIndex");
        Long blockNumber = body.getLong("blockNumber");
        String txHash = body.getString("txHash");
        String orderNumber = body.getString("orderNumber");

        if (depositId == null || depositId.isBlank() || userId == null || currencyId == null || amountStr == null || network == null || txHash == null) {
            ctx.fail(400, new IllegalArgumentException("depositId, userId, currencyId, amount, network, txHash required"));
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            ctx.fail(400, new IllegalArgumentException("Invalid amount: " + amountStr));
            return;
        }

        TokenDeposit deposit = TokenDeposit.builder()
            .depositId(depositId)
            .userId(userId)
            .currencyId(currencyId)
            .amount(amount)
            .network(network)
            .senderAddress(senderAddress)
            .toAddress(toAddress)
            .logIndex(logIndex)
            .blockNumber(blockNumber)
            .txHash(txHash)
            .orderNumber(orderNumber)
            .status(TokenDeposit.STATUS_PENDING)
            .build();

        log.info("Internal register deposit: depositId={}, userId={}, amount={}, txHash={}", depositId, userId, amount, txHash);
        response(ctx, tokenDepositService.registerTokenDeposit(deposit));
    }

    private void completeDeposit(RoutingContext ctx) {
        String depositId = ctx.pathParam("depositId");
        if (depositId == null || depositId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("depositId required"));
            return;
        }
        log.info("Internal complete deposit: depositId={}", depositId);
        response(ctx, tokenDepositService.completeTokenDeposit(depositId));
    }

    private void registerDepositBatch(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        JsonArray depositsArray = body.getJsonArray("deposits");
        if (depositsArray == null || depositsArray.isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("deposits required"));
            return;
        }

        List<io.vertx.core.Future<TokenDeposit>> futures = new ArrayList<>();
        for (Object item : depositsArray) {
            if (!(item instanceof JsonObject depositObj)) {
                continue;
            }
            String depositId = depositObj.getString("depositId");
            Long userId = depositObj.getLong("userId");
            Integer currencyId = depositObj.getInteger("currencyId");
            String amountStr = depositObj.getString("amount");
            String network = depositObj.getString("network");
            String txHash = depositObj.getString("txHash");
            if (depositId == null || depositId.isBlank() || userId == null || currencyId == null
                || amountStr == null || network == null || txHash == null) {
                continue;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                continue;
            }
            TokenDeposit deposit = TokenDeposit.builder()
                .depositId(depositId)
                .userId(userId)
                .currencyId(currencyId)
                .amount(amount)
                .network(network)
                .senderAddress(depositObj.getString("senderAddress"))
                .toAddress(depositObj.getString("toAddress"))
                .logIndex(depositObj.getInteger("logIndex"))
                .blockNumber(depositObj.getLong("blockNumber"))
                .txHash(txHash)
                .orderNumber(depositObj.getString("orderNumber"))
                .status(TokenDeposit.STATUS_PENDING)
                .build();
            futures.add(tokenDepositService.registerTokenDeposit(deposit));
        }

        if (futures.isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("No valid deposits"));
            return;
        }

        response(ctx, io.vertx.core.Future.all(futures).map(result -> new JsonObject()
            .put("requested", depositsArray.size())
            .put("processed", futures.size())
            .put("results", new JsonArray(result.list()))));
    }

    private void completeDepositBatch(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        JsonArray depositIds = body.getJsonArray("depositIds");
        if (depositIds == null || depositIds.isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("depositIds required"));
            return;
        }

        List<io.vertx.core.Future<TokenDeposit>> futures = new ArrayList<>();
        for (Object item : depositIds) {
            if (item instanceof String depositId && !depositId.isBlank()) {
                futures.add(tokenDepositService.completeTokenDeposit(depositId));
            }
        }
        if (futures.isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("No valid depositIds"));
            return;
        }

        response(ctx, io.vertx.core.Future.all(futures).map(result -> new JsonObject()
            .put("requested", depositIds.size())
            .put("processed", futures.size())
            .put("results", new JsonArray(result.list()))));
    }

    private void getDeposit(RoutingContext ctx) {
        String depositId = ctx.pathParam("depositId");
        if (depositId == null || depositId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("depositId required"));
            return;
        }
        response(ctx, tokenDepositService.getTokenDepositByDepositId(depositId));
    }

    private void getPendingSweeps(RoutingContext ctx) {
        int limit = 100;
        try {
            String raw = ctx.request().getParam("limit");
            if (raw != null && !raw.isBlank()) {
                limit = Integer.parseInt(raw);
            }
        } catch (NumberFormatException e) {
            ctx.fail(400, new IllegalArgumentException("limit must be integer"));
            return;
        }

        response(ctx, tokenDepositService.listPendingSweepRequests(limit));
    }

    private void submitSweep(RoutingContext ctx) {
        String depositId = ctx.pathParam("depositId");
        if (depositId == null || depositId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("depositId required"));
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
        response(ctx, tokenDepositService.submitSweepStatus(depositId, txHash));
    }

    private void failSweep(RoutingContext ctx) {
        String depositId = ctx.pathParam("depositId");
        if (depositId == null || depositId.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("depositId required"));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }
        String errorMessage = body.getString("errorMessage", "");
        response(ctx, tokenDepositService.failSweepStatus(depositId, errorMessage));
    }
}
