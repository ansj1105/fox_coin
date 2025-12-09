package com.foxya.coin.notice;

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
public class NoticeHandler extends BaseHandler {
    
    private final NoticeService noticeService;
    private final JWTAuth jwtAuth;
    
    public NoticeHandler(Vertx vertx, NoticeService noticeService, JWTAuth jwtAuth) {
        super(vertx);
        this.noticeService = noticeService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 공지사항 목록 조회
        router.get("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getNotices);
        
        // 공지사항 상세 조회
        router.get("/:id")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getNotice);
        
        return router;
    }
    
    private void getNotices(RoutingContext ctx) {
        Integer limit = ctx.queryParams().contains("limit") 
            ? Integer.parseInt(ctx.queryParams().get("limit")) 
            : 10;
        Integer offset = ctx.queryParams().contains("offset") 
            ? Integer.parseInt(ctx.queryParams().get("offset")) 
            : 0;
        
        log.info("Getting notices with limit: {}, offset: {}", limit, offset);
        response(ctx, noticeService.getNotices(limit, offset));
    }
    
    private void getNotice(RoutingContext ctx) {
        Long id = Long.valueOf(ctx.pathParam("id"));
        log.info("Getting notice: {}", id);
        response(ctx, noticeService.getNoticeById(id));
    }
}

