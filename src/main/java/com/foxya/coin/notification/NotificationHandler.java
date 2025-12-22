package com.foxya.coin.notification;

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
public class NotificationHandler extends BaseHandler {
    
    private final NotificationService notificationService;
    private final JWTAuth jwtAuth;
    
    public NotificationHandler(Vertx vertx, NotificationService notificationService, JWTAuth jwtAuth) {
        super(vertx);
        this.notificationService = notificationService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 알림 목록 조회
        router.get("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getNotifications);
        
        // 읽지 않은 알림 개수 조회
        router.get("/unread-count")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getUnreadCount);
        
        // 특정 알림을 읽음 처리
        router.patch("/:id/read")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::markAsRead);
        
        // 모든 알림을 읽음 처리
        router.patch("/read-all")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::markAllAsRead);
        
        return router;
    }
    
    private void getNotifications(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        Integer limit = ctx.queryParams().contains("limit") 
            ? Integer.parseInt(ctx.queryParams().get("limit")) 
            : 20;
        Integer offset = ctx.queryParams().contains("offset") 
            ? Integer.parseInt(ctx.queryParams().get("offset")) 
            : 0;
        
        log.info("Getting notifications for user: {}, limit: {}, offset: {}", userId, limit, offset);
        response(ctx, notificationService.getNotifications(userId, limit, offset));
    }
    
    private void getUnreadCount(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        log.info("Getting unread count for user: {}", userId);
        response(ctx, notificationService.getUnreadCount(userId));
    }
    
    private void markAsRead(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        Long notificationId = Long.parseLong(ctx.pathParam("id"));
        
        log.info("Marking notification {} as read for user: {}", notificationId, userId);
        response(ctx, notificationService.markAsRead(notificationId, userId));
    }
    
    private void markAllAsRead(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        log.info("Marking all notifications as read for user: {}", userId);
        response(ctx, notificationService.markAllAsRead(userId));
    }
}


