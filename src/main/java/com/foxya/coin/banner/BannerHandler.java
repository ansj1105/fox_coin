package com.foxya.coin.banner;

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
public class BannerHandler extends BaseHandler {
    
    private final BannerService bannerService;
    private final JWTAuth jwtAuth;
    
    public BannerHandler(Vertx vertx, BannerService bannerService, JWTAuth jwtAuth) {
        super(vertx);
        this.bannerService = bannerService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 배너 목록 조회 (공개 API)
        router.get("/")
            .handler(this::getBanners);
        
        // 배너 클릭 이벤트 기록 (인증 선택)
        router.post("/:id/click")
            .handler(this::recordBannerClick);
        
        return router;
    }
    
    private void getBanners(RoutingContext ctx) {
        String position = ctx.request().getParam("position");
        if (position == null || position.isEmpty()) {
            position = "RANKING_TOP";
        }
        
        log.info("Getting banners - position: {}", position);
        response(ctx, bannerService.getBanners(position));
    }
    
    private void recordBannerClick(RoutingContext ctx) {
        Long bannerId;
        try {
            bannerId = Long.valueOf(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("잘못된 배너 ID입니다."));
            return;
        }
        
        // 사용자 ID (로그인한 경우)
        Long userId = null;
        if (ctx.user() != null) {
            userId = AuthUtils.getUserIdOf(ctx.user());
        }
        
        // IP 주소와 User Agent
        String ipAddress = ctx.request().remoteAddress() != null 
            ? ctx.request().remoteAddress().host() 
            : null;
        String userAgent = ctx.request().getHeader("User-Agent");
        
        log.info("Recording banner click - bannerId: {}, userId: {}", bannerId, userId);
        bannerService.recordBannerClick(bannerId, userId, ipAddress, userAgent)
            .onSuccess(v -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(com.foxya.coin.common.dto.ApiResponse.success(null).toString());
            })
            .onFailure(throwable -> ctx.fail(throwable));
    }
}

