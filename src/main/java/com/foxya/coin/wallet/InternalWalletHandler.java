package com.foxya.coin.wallet;

import com.foxya.coin.common.BaseHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 외부 wallet issuer(coin_manage)용 내부 API.
 * API 키로 인증 후 user_wallets 주소/개인키를 동기화한다.
 */
@Slf4j
public class InternalWalletHandler extends BaseHandler {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    private final WalletService walletService;
    private final String internalApiKey;

    public InternalWalletHandler(Vertx vertx, WalletService walletService, String internalApiKey) {
        super(vertx);
        this.walletService = walletService;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.route().handler(this::checkInternalApiKey);
        router.post("/sync-virtual").handler(this::syncVirtualWallet);
        return router;
    }

    private void checkInternalApiKey(RoutingContext ctx) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            log.warn("Internal wallet API key not configured");
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

    private void syncVirtualWallet(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new IllegalArgumentException("Body required"));
            return;
        }

        Long userId = body.getLong("userId");
        Integer currencyId = body.getInteger("currencyId");
        String address = body.getString("address");
        String privateKey = body.getString("privateKey");

        if (userId == null || currencyId == null || address == null || address.isBlank() || privateKey == null || privateKey.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("userId, currencyId, address, privateKey required"));
            return;
        }

        log.info("Internal wallet sync: userId={}, currencyId={}, address={}", userId, currencyId, address);
        response(ctx, walletService.syncInternalVirtualWallet(userId, currencyId, address, privateKey));
    }
}
