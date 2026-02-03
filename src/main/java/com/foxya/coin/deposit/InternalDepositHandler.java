package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.wallet.WalletRepository;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

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
        router.post("/:depositId/complete").handler(this::completeDeposit);
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
}
