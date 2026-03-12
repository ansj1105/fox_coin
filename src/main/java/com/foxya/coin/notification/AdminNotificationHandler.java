package com.foxya.coin.notification;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.retry.BulkNotificationJob;
import com.foxya.coin.retry.RetryQueuePublisher;
import com.foxya.coin.user.UserRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class AdminNotificationHandler extends BaseHandler {
    private static final int BROADCAST_USER_BATCH_SIZE = 500;

    private final PgPool pool;
    private final NotificationService notificationService;
    private final RetryQueuePublisher retryQueuePublisher;
    private final UserRepository userRepository;
    private final JWTAuth jwtAuth;

    public AdminNotificationHandler(Vertx vertx, NotificationService notificationService, RetryQueuePublisher retryQueuePublisher, JWTAuth jwtAuth) {
        this(
            vertx,
            notificationService != null ? notificationService.getPool() : null,
            notificationService,
            retryQueuePublisher,
            notificationService != null ? notificationService.getUserRepository() : null,
            jwtAuth
        );
    }

    public AdminNotificationHandler(Vertx vertx, PgPool pool, NotificationService notificationService, RetryQueuePublisher retryQueuePublisher, UserRepository userRepository, JWTAuth jwtAuth) {
        super(vertx);
        this.pool = pool;
        this.notificationService = notificationService;
        this.retryQueuePublisher = retryQueuePublisher;
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());

        router.post("/test")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.SUPER_ADMIN))
            .handler(this::createTestNotification);
        router.post("/test/bulk")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.SUPER_ADMIN))
            .handler(this::createBulkTestNotification);
        router.post("/test/broadcast")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.SUPER_ADMIN))
            .handler(this::createBroadcastTestNotification);
        router.get("/test/bulk/:requestId/status")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.SUPER_ADMIN))
            .handler(this::getBulkTestNotificationStatus);

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

        NotificationType type = parseNotificationTypeOrFail(ctx, body.getString("type", NotificationType.NOTICE.name()));
        if (type == null) return;

        Long relatedId = body.getLong("relatedId");
        String metadata = normalizeMetadata(body.getValue("metadata"));

        log.info("Admin test notification request: adminId={}, targetUserId={}, type={}",
            AuthUtils.getUserIdOf(ctx.user()), userId, type);

        response(ctx, notificationService.createNotification(userId, type, title, message, relatedId, metadata),
            this::toResponse);
    }

    private void createBulkTestNotification(RoutingContext ctx) {
        if (retryQueuePublisher == null) {
            ctx.fail(503, new BadRequestException("Redis가 비활성화되어 다건 알림 기능을 사용할 수 없습니다."));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new BadRequestException("요청 본문이 필요합니다."));
            return;
        }

        JsonArray userIdArray = body.getJsonArray("userIds");
        if (userIdArray == null || userIdArray.isEmpty()) {
            ctx.fail(400, new BadRequestException("userIds 배열이 필요합니다."));
            return;
        }

        String title = body.getString("title");
        String message = body.getString("message");
        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            ctx.fail(400, new BadRequestException("title, message 값이 필요합니다."));
            return;
        }

        NotificationType type = parseNotificationTypeOrFail(ctx, body.getString("type", NotificationType.NOTICE.name()));
        if (type == null) return;

        Set<Long> dedupedUserIds = new LinkedHashSet<>();
        for (Object value : userIdArray) {
            Long parsed = toPositiveLong(value);
            if (parsed != null) {
                dedupedUserIds.add(parsed);
            }
        }
        if (dedupedUserIds.isEmpty()) {
            ctx.fail(400, new BadRequestException("유효한 userId가 없습니다."));
            return;
        }

        int maxRetries = body.getInteger("maxRetries", BulkNotificationJob.DEFAULT_MAX_RETRIES);
        maxRetries = Math.max(1, Math.min(maxRetries, 10));
        final int finalMaxRetries = maxRetries;
        Long relatedId = body.getLong("relatedId");
        String metadata = normalizeMetadata(body.getValue("metadata"));
        Long adminId = AuthUtils.getUserIdOf(ctx.user());
        String requestId = UUID.randomUUID().toString().replace("-", "");
        List<Long> userIds = dedupedUserIds.stream().toList();

        log.info("Admin bulk notification enqueue request: adminId={}, requestId={}, total={}, type={}",
            adminId, requestId, userIds.size(), type);

        response(ctx,
            retryQueuePublisher.initBulkNotificationStatus(requestId, adminId, userIds.size())
                .compose(v -> enqueueBulkJobs(userIds, 0, requestId, adminId, type, title, message, relatedId, metadata, finalMaxRetries))
                .compose(result -> retryQueuePublisher.getBulkNotificationStatus(requestId)
                    .map(status -> new JsonObject()
                        .put("requestId", requestId)
                        .put("total", userIds.size())
                        .put("enqueued", result.enqueued())
                        .put("enqueueFailed", result.enqueueFailed())
                        .put("status", status)
                    )),
            result -> result
        );
    }

    private void getBulkTestNotificationStatus(RoutingContext ctx) {
        if (retryQueuePublisher == null) {
            ctx.fail(503, new BadRequestException("Redis가 비활성화되어 다건 알림 상태를 조회할 수 없습니다."));
            return;
        }
        String requestId = ctx.pathParam("requestId");
        if (requestId == null || requestId.isBlank()) {
            ctx.fail(400, new BadRequestException("requestId가 필요합니다."));
            return;
        }
        response(ctx,
            retryQueuePublisher.getBulkNotificationStatus(requestId)
                .compose(status -> {
                    if (status == null) {
                        return Future.failedFuture(new BadRequestException("요청 상태를 찾을 수 없습니다."));
                    }
                    return Future.succeededFuture(status);
                }),
            status -> status
        );
    }

    private void createBroadcastTestNotification(RoutingContext ctx) {
        if (retryQueuePublisher == null) {
            ctx.fail(503, new BadRequestException("Redis가 비활성화되어 전체 알림 기능을 사용할 수 없습니다."));
            return;
        }
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new BadRequestException("요청 본문이 필요합니다."));
            return;
        }

        String title = body.getString("title");
        String message = body.getString("message");
        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            ctx.fail(400, new BadRequestException("title, message 값이 필요합니다."));
            return;
        }

        NotificationType type = parseNotificationTypeOrFail(ctx, body.getString("type", NotificationType.NOTICE.name()));
        if (type == null) return;

        int maxRetries = body.getInteger("maxRetries", BulkNotificationJob.DEFAULT_MAX_RETRIES);
        maxRetries = Math.max(1, Math.min(maxRetries, 10));
        final int finalMaxRetries = maxRetries;
        Long relatedId = body.getLong("relatedId");
        String metadata = normalizeMetadata(body.getValue("metadata"));
        Long adminId = AuthUtils.getUserIdOf(ctx.user());
        String requestId = UUID.randomUUID().toString().replace("-", "");

        log.info("Admin broadcast notification enqueue request: adminId={}, requestId={}, type={}",
            adminId, requestId, type);

        response(ctx,
            userRepository.countActiveUsers(pool)
                .compose(totalUsers -> {
                    int total = totalUsers == null ? 0 : (int) Math.min(totalUsers, Integer.MAX_VALUE);
                    return retryQueuePublisher.initBulkNotificationStatus(requestId, adminId, total)
                        .compose(v -> enqueueBroadcastJobs(
                            requestId,
                            adminId,
                            type,
                            title,
                            message,
                            relatedId,
                            metadata,
                            finalMaxRetries,
                            0L
                        ))
                        .compose(result -> retryQueuePublisher.getBulkNotificationStatus(requestId)
                            .map(status -> new JsonObject()
                                .put("requestId", requestId)
                                .put("total", total)
                                .put("enqueued", result.enqueued())
                                .put("enqueueFailed", result.enqueueFailed())
                                .put("status", status)
                            ));
                }),
            result -> result
        );
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

    private NotificationType parseNotificationTypeOrFail(RoutingContext ctx, String typeRaw) {
        try {
            return NotificationType.valueOf(typeRaw.trim().toUpperCase());
        } catch (Exception e) {
            String allowed = Arrays.stream(NotificationType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
            ctx.fail(400, new BadRequestException("type 값이 올바르지 않습니다. allowed: " + allowed));
            return null;
        }
    }

    private Future<BulkEnqueueResult> enqueueBulkJobs(List<Long> userIds,
                                                      int index,
                                                      String requestId,
                                                      Long adminId,
                                                      NotificationType type,
                                                      String title,
                                                      String message,
                                                      Long relatedId,
                                                      String metadata,
                                                      int maxRetries) {
        if (index >= userIds.size()) {
            return Future.succeededFuture(new BulkEnqueueResult(0, 0));
        }
        Long userId = userIds.get(index);
        BulkNotificationJob job = BulkNotificationJob.builder()
            .requestId(requestId)
            .adminId(adminId)
            .userId(userId)
            .type(type.name())
            .title(title)
            .message(message)
            .relatedId(relatedId)
            .metadata(metadata)
            .retryCount(0)
            .maxRetries(maxRetries)
            .createdAt(java.time.LocalDateTime.now())
            .build();

        return retryQueuePublisher.enqueueBulkNotification(job)
            .compose(v -> retryQueuePublisher.incrementBulkEnqueued(requestId))
            .compose(v -> enqueueBulkJobs(userIds, index + 1, requestId, adminId, type, title, message, relatedId, metadata, maxRetries))
            .map(next -> next.plusEnqueued())
            .recover(error -> retryQueuePublisher.incrementBulkEnqueueFailed(requestId)
                .compose(v -> retryQueuePublisher.incrementBulkProcessed(requestId))
                .compose(v -> retryQueuePublisher.incrementBulkFailed(requestId))
                .recover(ignore -> Future.succeededFuture())
                .compose(v -> enqueueBulkJobs(userIds, index + 1, requestId, adminId, type, title, message, relatedId, metadata, maxRetries))
                .map(next -> next.plusFailed()));
    }

    private Future<BulkEnqueueResult> enqueueBroadcastJobs(String requestId,
                                                           Long adminId,
                                                           NotificationType type,
                                                           String title,
                                                           String message,
                                                           Long relatedId,
                                                           String metadata,
                                                           int maxRetries,
                                                           Long afterUserId) {
        return userRepository.getActiveUserIdsAfter(pool, afterUserId, BROADCAST_USER_BATCH_SIZE)
            .compose(userIds -> {
                if (userIds == null || userIds.isEmpty()) {
                    return Future.succeededFuture(new BulkEnqueueResult(0, 0));
                }

                Long nextCursor = userIds.get(userIds.size() - 1);
                return enqueueBulkJobs(userIds, 0, requestId, adminId, type, title, message, relatedId, metadata, maxRetries)
                    .compose(current -> enqueueBroadcastJobs(
                        requestId,
                        adminId,
                        type,
                        title,
                        message,
                        relatedId,
                        metadata,
                        maxRetries,
                        nextCursor
                    ).map(next -> current.merge(next)));
            });
    }

    private Long toPositiveLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) {
            long v = number.longValue();
            return v > 0 ? v : null;
        }
        if (value instanceof String str) {
            try {
                long v = Long.parseLong(str.trim());
                return v > 0 ? v : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private record BulkEnqueueResult(int enqueued, int enqueueFailed) {
        private BulkEnqueueResult plusEnqueued() {
            return new BulkEnqueueResult(enqueued + 1, enqueueFailed);
        }

        private BulkEnqueueResult plusFailed() {
            return new BulkEnqueueResult(enqueued, enqueueFailed + 1);
        }

        private BulkEnqueueResult merge(BulkEnqueueResult other) {
            return new BulkEnqueueResult(
                enqueued + (other == null ? 0 : other.enqueued),
                enqueueFailed + (other == null ? 0 : other.enqueueFailed)
            );
        }
    }
}
