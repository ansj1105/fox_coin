package com.foxya.coin.review;

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
public class ReviewHandler extends BaseHandler {
    
    private final ReviewService reviewService;
    private final JWTAuth jwtAuth;
    
    public ReviewHandler(Vertx vertx, ReviewService reviewService, JWTAuth jwtAuth) {
        super(vertx);
        this.reviewService = reviewService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 리뷰 작성 여부 조회
        router.get("/status")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getReviewStatus);
        
        // 리뷰 작성
        router.post("/write")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::writeReview);
        
        return router;
    }
    
    private void getReviewStatus(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting review status for user: {}", userId);
        response(ctx, reviewService.getReviewStatus(userId));
    }
    
    private void writeReview(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String platform = ctx.getBodyAsJson().getString("platform");
        String reviewId = ctx.getBodyAsJson().getString("reviewId");
        
        log.info("Review write request from user: {}, platform: {}", userId, platform);
        response(ctx, reviewService.writeReview(userId, platform, reviewId));
    }
}

