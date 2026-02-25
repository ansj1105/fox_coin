package com.foxya.coin.notification;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.notification.entities.Notification;
import com.foxya.coin.notification.enums.NotificationType;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
public class AdminNotificationHandler extends BaseHandler {

    private final NotificationService notificationService;
    private final JWTAuth jwtAuth;

    public AdminNotificationHandler(Vertx vertx, NotificationService notificationService, JWTAuth jwtAuth) {
        super(vertx);
        this.notificationService = notificationService;
        this.jwtAuth = jwtAuth;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());

        router.post("/test")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN))
            .handler(this::createTestNotification);

        return router;
    }

    private void createTestNotification(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new BadRequestException("요청 본문이 필요합니다."));
            return;
        }

        Long userId = body.getLong("userId");
        String title = body.getString("title");
        String message = body.getString("message");
        if (userId == null || title == null || title.isBlank() || message == null || message.isBlank()) {
            ctx.fail(400, new BadRequestException("userId, title, message 값이 필요합니다."));
            return;
        }

        String typeRaw = body.getString("type", NotificationType.NOTICE.name());
        NotificationType type;
        try {
            type = NotificationType.valueOf(typeRaw.trim().toUpperCase());
        } catch (Exception e) {
            String allowed = Arrays.stream(NotificationType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
            ctx.fail(400, new BadRequestException("type 값이 올바르지 않습니다. allowed: " + allowed));
            return;
        }

        Long relatedId = body.getLong("relatedId");
        String metadata = normalizeMetadata(body.getValue("metadata"));

        log.info("Admin test notification request: adminId={}, targetUserId={}, type={}",
            AuthUtils.getUserIdOf(ctx.user()), userId, type);

        response(ctx, notificationService.createNotification(userId, type, title, message, relatedId, metadata),
            this::toResponse);
    }

    private JsonObject toResponse(Notification notification) {
        return new JsonObject()
            .put("created", notification != null)
            .put("notification", notification == null ? null : JsonObject.mapFrom(notification));
    }

    private String normalizeMetadata(Object metadataValue) {
        if (metadataValue == null) return null;
        if (metadataValue instanceof String) {
            String value = ((String) metadataValue).trim();
            return value.isEmpty() ? null : value;
        }
        if (metadataValue instanceof JsonObject || metadataValue instanceof JsonArray) {
            return Json.encode(metadataValue);
        }
        return Json.encode(metadataValue);
    }
}

