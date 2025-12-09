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
    
    private void subscribe(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String packageType = ctx.getBodyAsJson().getString("packageType");
        Integer months = ctx.getBodyAsJson().getInteger("months");
        
        log.info("Subscription request from user: {}, package: {}, months: {}", userId, packageType, months);
        response(ctx, subscriptionService.subscribe(userId, packageType, months));
    }
}

