package com.foxya.coin.wallet;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import com.foxya.coin.common.BaseHandler;

public class WalletHandler extends BaseHandler {
    
    private final WalletService walletService;
    
    public WalletHandler(Vertx vertx, WalletService walletService) {
        super(vertx);
        this.walletService = walletService;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(vertx);
        router.get("/my").handler(this::getMyWallets);
        return router;
    }
    
    private void getMyWallets(RoutingContext ctx) {
        // JWT에서 userId 추출 (나중에 구현)
        Long userId = 1L; // TODO: JWT에서 추출
        response(ctx, walletService.getUserWallets(userId));
    }
}

