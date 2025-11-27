package com.foxya.coin.wallet;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WalletHandler extends BaseHandler {
    
    private final WalletService walletService;
    
    public WalletHandler(Vertx vertx, WalletService walletService) {
        super(vertx);
        this.walletService = walletService;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(vertx);
        
        // 인증 필요 - 본인 지갑만 조회 가능
        router.get("/my")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getMyWallets);
        
        return router;
    }
    
    private void getMyWallets(RoutingContext ctx) {
        // JWT에서 userId 추출
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Fetching wallets for user: {}", userId);
        response(ctx, walletService.getUserWallets(userId));
    }
}

