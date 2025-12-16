package com.foxya.coin.ranking;

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
public class RankingHandler extends BaseHandler {
    
    private final RankingService rankingService;
    private final JWTAuth jwtAuth;
    
    public RankingHandler(Vertx vertx, RankingService rankingService, JWTAuth jwtAuth) {
        super(vertx);
        this.rankingService = rankingService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 개인 랭킹 조회 (명시적으로 경로 지정)
        router.get("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getRankings);
        
        // 국가별 팀 랭킹 조회
        router.get("/country")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getCountryRankings);
        
        return router;
    }
    
    private void getRankings(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String scope = ctx.request().getParam("scope");
        String period = ctx.request().getParam("period");
        
        if (scope == null || scope.isEmpty()) {
            scope = "REGIONAL";
        }
        if (period == null || period.isEmpty()) {
            period = "TODAY";
        }
        
        log.info("Getting rankings for user: {}, scope: {}, period: {}", userId, scope, period);
        response(ctx, rankingService.getRankings(userId, scope, period));
    }
    
    private void getCountryRankings(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String period = ctx.request().getParam("period");
        if (period == null || period.isEmpty()) {
            period = "TODAY";
        }
        
        log.info("Getting country rankings for user: {}, period: {}", userId, period);
        response(ctx, rankingService.getCountryRankings(userId, period));
    }
}

