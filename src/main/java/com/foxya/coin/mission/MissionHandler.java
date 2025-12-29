package com.foxya.coin.mission;

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
public class MissionHandler extends BaseHandler {
    
    private final MissionService missionService;
    private final JWTAuth jwtAuth;
    
    public MissionHandler(Vertx vertx, MissionService missionService, JWTAuth jwtAuth) {
        super(vertx);
        this.missionService = missionService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 오늘의 미션 목록 조회
        router.get("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getTodayMissions);
        
        // 미션 완료 처리
        router.post("/:id/complete")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::completeMission);
        
        return router;
    }
    
    private void getTodayMissions(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        log.info("Getting today missions for user: {}", userId);
        response(ctx, missionService.getTodayMissions(userId));
    }
    
    private void completeMission(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        Long missionId = Long.parseLong(ctx.pathParam("id"));
        
        log.info("Completing mission {} for user: {}", missionId, userId);
        response(ctx, missionService.completeMission(userId, missionId));
    }
}

