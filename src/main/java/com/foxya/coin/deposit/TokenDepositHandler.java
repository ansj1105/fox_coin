package com.foxya.coin.deposit;

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
public class TokenDepositHandler extends BaseHandler {
    
    private final TokenDepositService tokenDepositService;
    private final JWTAuth jwtAuth;
    
    public TokenDepositHandler(Vertx vertx, TokenDepositService tokenDepositService, JWTAuth jwtAuth) {
        super(vertx);
        this.tokenDepositService = tokenDepositService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 모든 토큰 입금 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        
        // 토큰 입금 목록 조회
        router.get("/")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getTokenDeposits);
        
        return router;
    }
    
    /**
     * 토큰 입금 목록 조회
     */
    private void getTokenDeposits(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        int limit = Integer.parseInt(ctx.request().getParam("limit", "20"));
        int offset = Integer.parseInt(ctx.request().getParam("offset", "0"));
        String currencyCode = ctx.request().getParam("currencyCode");
        
        log.info("토큰 입금 목록 조회 - userId: {}, currencyCode: {}, limit: {}, offset: {}", 
            userId, currencyCode, limit, offset);
        
        response(ctx, tokenDepositService.getTokenDeposits(userId, currencyCode, limit, offset));
    }
}

