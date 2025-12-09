package com.foxya.coin.bonus;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BonusHandler extends BaseHandler {
    
    private final BonusService bonusService;
    private final JWTAuth jwtAuth;
    
    public BonusHandler(Vertx vertx, BonusService bonusService, JWTAuth jwtAuth) {
        super(vertx);
        this.bonusService = bonusService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 보너스 채굴 효율 조회
        router.get("/efficiency")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getBonusEfficiency);
        
        return router;
    }
    
    private void getBonusEfficiency(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting bonus efficiency for user: {}", userId);
        response(ctx, bonusService.getBonusEfficiency(userId));
    }
}

