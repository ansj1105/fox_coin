package com.foxya.coin.agency;

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
public class AgencyHandler extends BaseHandler {
    
    private final AgencyService agencyService;
    private final JWTAuth jwtAuth;
    
    public AgencyHandler(Vertx vertx, AgencyService agencyService, JWTAuth jwtAuth) {
        super(vertx);
        this.agencyService = agencyService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 에이전시 가입 상태 조회
        router.get("/status")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getAgencyStatus);
        
        return router;
    }
    
    private void getAgencyStatus(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting agency status for user: {}", userId);
        response(ctx, agencyService.getAgencyStatus(userId));
    }
}

