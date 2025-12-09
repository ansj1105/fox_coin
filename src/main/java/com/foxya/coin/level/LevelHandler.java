package com.foxya.coin.level;

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
public class LevelHandler extends BaseHandler {
    
    private final LevelService levelService;
    private final JWTAuth jwtAuth;
    
    public LevelHandler(Vertx vertx, LevelService levelService, JWTAuth jwtAuth) {
        super(vertx);
        this.levelService = levelService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 사용자 레벨 및 경험치 정보
        router.get("/level")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getUserLevel);
        
        // 레벨 가이드 정보
        router.get("/level-guide")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getLevelGuide);
        
        return router;
    }
    
    private void getUserLevel(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting user level for user: {}", userId);
        response(ctx, levelService.getUserLevel(userId));
    }
    
    private void getLevelGuide(RoutingContext ctx) {
        log.info("Getting level guide");
        response(ctx, levelService.getLevelGuide());
    }
}

