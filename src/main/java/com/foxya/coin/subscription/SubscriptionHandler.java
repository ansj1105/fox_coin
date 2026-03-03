package com.foxya.coin.subscription;

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
public class SubscriptionHandler extends BaseHandler {
    
    private final SubscriptionService subscriptionService;
    private final JWTAuth jwtAuth;
    
    public SubscriptionHandler(Vertx vertx, SubscriptionService subscriptionService, JWTAuth jwtAuth) {
        super(vertx);
        this.subscriptionService = subscriptionService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 프리미엄 구독 상태 조회
        router.get("/status")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getSubscriptionStatus);

        // 구독 플랜 조회
        router.get("/plans")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getPlans);
        
        // 프리미엄 패키지 구독
        router.post("/subscribe")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::subscribe);
        
        return router;
    }
    
    private void getSubscriptionStatus(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting subscription status for user: {}", userId);
        response(ctx, subscriptionService.getSubscriptionStatus(userId));
    }

    private void getPlans(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting subscription plans for user: {}", userId);
        response(ctx, subscriptionService.getPlans());
    }

    private void subscribe(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        io.vertx.core.json.JsonObject body = ctx.getBodyAsJson();
        String planCode = body != null ? body.getString("planCode") : null;
        String packageType = body != null ? body.getString("packageType") : null;
        Integer months = body != null ? body.getInteger("months") : null;
        Integer days = body != null ? body.getInteger("days") : null;

        log.info("Subscription request from user: {}, planCode: {}, package: {}, months: {}, days: {}",
            userId, planCode, packageType, months, days);
        response(ctx, subscriptionService.subscribe(userId, planCode, packageType, months, days));
    }
}
