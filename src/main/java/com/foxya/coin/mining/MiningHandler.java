package com.foxya.coin.mining;

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
public class MiningHandler extends BaseHandler {
    
    private final MiningService miningService;
    private final JWTAuth jwtAuth;
    
    public MiningHandler(Vertx vertx, MiningService miningService, JWTAuth jwtAuth) {
        super(vertx);
        this.miningService = miningService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 일일 최대 채굴량 조회
        router.get("/daily-limit")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getDailyLimit);
        
        // 레벨별 일일 최대 채굴량 정보
        router.get("/level-info")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getLevelInfo);
        
        // 채굴 내역 조회
        router.get("/history")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getMiningHistory);
        
        return router;
    }
    
    private void getDailyLimit(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting daily limit for user: {}", userId);
        response(ctx, miningService.getDailyLimit(userId));
    }
    
    private void getLevelInfo(RoutingContext ctx) {
        log.info("Getting level info");
        response(ctx, miningService.getLevelInfo());
    }
    
    private void getMiningHistory(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String period = ctx.queryParams().get("period");
        Integer limit = ctx.queryParams().contains("limit") 
            ? Integer.parseInt(ctx.queryParams().get("limit")) 
            : null;
        Integer offset = ctx.queryParams().contains("offset") 
            ? Integer.parseInt(ctx.queryParams().get("offset")) 
            : null;
        
        log.info("Getting mining history for user: {}, period: {}, limit: {}, offset: {}", 
            userId, period, limit, offset);
        
        response(ctx, miningService.getMiningHistory(userId, period, limit, offset));
    }
}

