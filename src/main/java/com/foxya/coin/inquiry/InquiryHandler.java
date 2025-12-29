package com.foxya.coin.inquiry;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.inquiry.dto.CreateInquiryRequestDto;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InquiryHandler extends BaseHandler {
    
    private final InquiryService inquiryService;
    private final JWTAuth jwtAuth;
    
    public InquiryHandler(Vertx vertx, InquiryService inquiryService, JWTAuth jwtAuth) {
        super(vertx);
        this.inquiryService = inquiryService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 문의 제출
        router.post("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::createInquiry);
        
        return router;
    }
    
    private void createInquiry(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        JsonObject body = ctx.getBodyAsJson();
        
        CreateInquiryRequestDto dto = new CreateInquiryRequestDto();
        dto.setSubject(body.getString("subject"));
        dto.setContent(body.getString("content"));
        dto.setEmail(body.getString("email"));
        
        log.info("Creating inquiry for user: {}, subject: {}", userId, dto.getSubject());
        response(ctx, inquiryService.createInquiry(userId, dto));
    }
}

