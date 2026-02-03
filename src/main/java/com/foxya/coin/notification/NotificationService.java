package com.foxya.coin.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.notification.dto.NotificationListResponseDto;
import com.foxya.coin.notification.dto.UnreadCountResponseDto;
import com.foxya.coin.notification.entities.Notification;
import com.foxya.coin.notification.enums.NotificationType;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NotificationService extends BaseService {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final NotificationRepository notificationRepository;
    private final FcmService fcmService;

    public NotificationService(PgPool pool, NotificationRepository notificationRepository) {
        this(pool, notificationRepository, null);
    }

    public NotificationService(PgPool pool, NotificationRepository notificationRepository, FcmService fcmService) {
        super(pool);
        this.notificationRepository = notificationRepository;
        this.fcmService = fcmService;
    }

    /**
     * 알림 생성 (입금/출금 완료 등) + FCM 푸시(설정 시).
     * 실패해도 로그만 남기고 성공 반환(입출금 완료 트랜잭션에 영향 없도록).
     */
    public Future<Notification> createNotification(Long userId, NotificationType type, String title, String message,
                                                   Long relatedId, String metadata) {
        return notificationRepository.insert(pool, userId, type, title, message, relatedId, metadata)
            .recover(err -> {
                log.warn("알림 생성 실패(무시) - userId: {}, type: {}", userId, type, err);
                return Future.succeededFuture((Notification) null);
            })
            .compose(notification -> {
                if (notification != null && fcmService != null && fcmService.isEnabled()) {
                    Map<String, String> data = new HashMap<>();
                    if (metadata != null && !metadata.isBlank()) {
                        try {
                            JsonNode node = objectMapper.readTree(metadata);
                            node.fields().forEachRemaining(e -> data.put(e.getKey(), e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
                        } catch (Exception ignored) { }
                    }
                    data.put("type", type.name());
                    fcmService.sendToUser(userId, title, message, data).onComplete(ar -> {
                        if (ar.failed()) {
                            log.warn("FCM 푸시 실패(무시): userId={}", userId, ar.cause());
                        }
                    });
                }
                return Future.succeededFuture(notification);
            });
    }

    /**
     * 알림 목록 조회
     */
    public Future<NotificationListResponseDto> getNotifications(Long userId, Integer limit, Integer offset) {
        return Future.all(
            notificationRepository.getNotifications(pool, userId, limit, offset),
            notificationRepository.getUnreadCount(pool, userId),
            notificationRepository.getTotalCount(pool, userId)
        ).map(results -> {
            @SuppressWarnings("unchecked")
            List<Notification> notifications = (List<Notification>) results.list().get(0);
            Integer unreadCount = (Integer) results.list().get(1);
            Long total = (Long) results.list().get(2);
            
            List<NotificationListResponseDto.NotificationInfo> notificationInfos = new ArrayList<>();
            for (Notification notification : notifications) {
                JsonNode metadata = null;
                if (notification.getMetadata() != null) {
                    try {
                        metadata = objectMapper.readTree(notification.getMetadata());
                    } catch (Exception e) {
                        log.warn("Failed to parse metadata for notification {}: {}", notification.getId(), e.getMessage());
                    }
                }
                
                notificationInfos.add(NotificationListResponseDto.NotificationInfo.builder()
                    .id(notification.getId())
                    .type(notification.getType())
                    .title(notification.getTitle())
                    .message(notification.getMessage())
                    .isRead(notification.getIsRead())
                    .createdAt(notification.getCreatedAt())
                    .relatedId(notification.getRelatedId())
                    .metadata(metadata)
                    .build());
            }
            
            return NotificationListResponseDto.builder()
                .notifications(notificationInfos)
                .unreadCount(unreadCount)
                .total(total)
                .limit(limit)
                .offset(offset)
                .build();
        });
    }
    
    /**
     * 읽지 않은 알림 개수 조회
     */
    public Future<UnreadCountResponseDto> getUnreadCount(Long userId) {
        return notificationRepository.getUnreadCount(pool, userId)
            .map(count -> UnreadCountResponseDto.builder()
                .count(count)
                .build());
    }
    
    /**
     * 특정 알림을 읽음 처리
     */
    public Future<Void> markAsRead(Long notificationId, Long userId) {
        return notificationRepository.markAsRead(pool, notificationId, userId)
            .compose(success -> {
                if (!success) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("알림을 찾을 수 없습니다."));
                }
                return Future.succeededFuture();
            })
            .mapEmpty();
    }
    
    /**
     * 모든 알림을 읽음 처리
     */
    public Future<Void> markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(pool, userId)
            .mapEmpty();
    }
}


