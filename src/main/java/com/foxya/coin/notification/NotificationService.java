package com.foxya.coin.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.notification.dto.NotificationListResponseDto;
import com.foxya.coin.notification.dto.UnreadCountResponseDto;
import com.foxya.coin.notification.entities.Notification;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationMessageLocalizer;
import com.foxya.coin.retry.RetryQueuePublisher;
import com.foxya.coin.user.UserRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NotificationService extends BaseService {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final NotificationRepository notificationRepository;
    private final FcmService fcmService;
    private final UserRepository userRepository;
    private final RetryQueuePublisher retryQueuePublisher;

    public NotificationService(PgPool pool, NotificationRepository notificationRepository) {
        this(pool, notificationRepository, null, null, null);
    }

    public NotificationService(PgPool pool, NotificationRepository notificationRepository, FcmService fcmService) {
        this(pool, notificationRepository, fcmService, null, null);
    }

    public NotificationService(PgPool pool, NotificationRepository notificationRepository, FcmService fcmService, UserRepository userRepository) {
        this(pool, notificationRepository, fcmService, userRepository, null);
    }

    public NotificationService(PgPool pool, NotificationRepository notificationRepository, FcmService fcmService, UserRepository userRepository, RetryQueuePublisher retryQueuePublisher) {
        super(pool);
        this.notificationRepository = notificationRepository;
        this.fcmService = fcmService;
        this.userRepository = userRepository;
        this.retryQueuePublisher = retryQueuePublisher;
    }

    /**
     * 알림 생성 (입금/출금 완료 등) + FCM 푸시(설정 시).
     * 실패해도 로그만 남기고 성공 반환(입출금 완료 트랜잭션에 영향 없도록).
     */
    public Future<Notification> createNotification(Long userId, NotificationType type, String title, String message,
                                                   Long relatedId, String metadata) {
        return createNotificationInternal(userId, type, title, message, relatedId, metadata, true);
    }

    /**
     * 다건 워커 전용: DB 저장 실패를 삼키지 않고 실패 반환(재시도/DLQ 분기용).
     */
    public Future<Notification> createNotificationStrict(Long userId, NotificationType type, String title, String message,
                                                         Long relatedId, String metadata) {
        return createNotificationInternal(userId, type, title, message, relatedId, metadata, false);
    }

    public RetryQueuePublisher getRetryQueuePublisher() {
        return retryQueuePublisher;
    }

    private Future<Notification> createNotificationInternal(Long userId, NotificationType type, String title, String message,
                                                            Long relatedId, String metadata, boolean swallowInsertFailure) {
        JsonNode metadataNode = parseMetadata(metadata);
        return resolveLocalizedText(userId, title, message, metadataNode)
            .compose(resolved -> {
                Future<Notification> insertFuture = notificationRepository
                    .insert(pool, userId, type, resolved.getTitle(), resolved.getMessage(), relatedId, metadata);
                if (swallowInsertFailure) {
                    insertFuture = insertFuture.recover(err -> {
                        log.warn("알림 생성 실패(무시) - userId: {}, type: {}", userId, type, err);
                        return Future.succeededFuture((Notification) null);
                    });
                }
                return insertFuture.compose(notification -> {
                    if (notification != null && fcmService != null && fcmService.isEnabled()) {
                        Map<String, String> data = toFcmData(metadataNode);
                        data.put("type", type.name());
                        fcmService.sendToUser(userId, resolved.getTitle(), resolved.getMessage(), data).onComplete(ar -> {
                            if (ar.failed()) {
                                log.warn("FCM 푸시 실패(무시): userId={}", userId, ar.cause());
                            }
                        });
                    }
                    return Future.succeededFuture(notification);
                });
            });
    }

    /**
     * 알림 목록 조회
     */
    public Future<Notification> createNotificationIfAbsentByRelatedId(Long userId, NotificationType type, String title,
                                                                      String message, Long relatedId, String metadata) {
        if (relatedId == null) {
            return createNotification(userId, type, title, message, null, metadata);
        }
        return notificationRepository.existsByUserTypeAndRelatedId(pool, userId, type, relatedId)
            .compose(exists -> {
                if (exists) {
                    return Future.succeededFuture((Notification) null);
                }
                return createNotification(userId, type, title, message, relatedId, metadata);
            });
    }

    public Future<Notification> createNotificationIfAbsentByDate(Long userId, NotificationType type, String title,
                                                                 String message, LocalDate localDate, Long relatedId,
                                                                 String metadata) {
        return notificationRepository.existsByUserTypeAndCreatedDate(pool, userId, type, localDate)
            .compose(exists -> {
                if (exists) {
                    return Future.succeededFuture((Notification) null);
                }
                return createNotification(userId, type, title, message, relatedId, metadata);
            });
    }

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

    /**
     * 특정 알림 삭제 (soft delete)
     */
    public Future<Void> deleteNotification(Long notificationId, Long userId) {
        return notificationRepository.softDeleteNotificationById(pool, userId, notificationId)
            .compose(success -> {
                if (!success) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("알림을 찾을 수 없습니다."));
                }
                return Future.succeededFuture();
            })
            .mapEmpty();
    }

    /**
     * 현재 사용자의 알림 전체 삭제 (알림센터 목록 비우기, soft delete)
     */
    public Future<Void> deleteAllForUser(Long userId) {
        return notificationRepository.softDeleteNotificationsByUserId(pool, userId);
    }

    private JsonNode parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(metadata);
        } catch (Exception e) {
            log.warn("Failed to parse notification metadata: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> toFcmData(JsonNode metadataNode) {
        Map<String, String> data = new HashMap<>();
        if (metadataNode != null && metadataNode.isObject()) {
            metadataNode.fields().forEachRemaining(e ->
                data.put(e.getKey(), e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString())
            );
        }
        return data;
    }

    private Future<ResolvedText> resolveLocalizedText(Long userId, String fallbackTitle, String fallbackMessage, JsonNode metadataNode) {
        String titleKey = getTextValue(metadataNode, "titleKey");
        String messageKey = getTextValue(metadataNode, "messageKey");
        boolean hasI18nKeys = (titleKey != null && !titleKey.isBlank()) || (messageKey != null && !messageKey.isBlank());

        if (!hasI18nKeys || userRepository == null || userId == null) {
            return Future.succeededFuture(new ResolvedText(fallbackTitle, fallbackMessage));
        }

        return userRepository.getUserById(pool, userId)
            .map(user -> {
                String countryCode = user != null ? user.getCountryCode() : null;
                java.util.Locale locale = NotificationMessageLocalizer.resolveLocale(countryCode);
                String localizedTitle = NotificationMessageLocalizer.resolve(titleKey, locale, metadataNode, fallbackTitle);
                String localizedMessage = NotificationMessageLocalizer.resolve(messageKey, locale, metadataNode, fallbackMessage);
                return new ResolvedText(localizedTitle, localizedMessage);
            })
            .recover(err -> {
                log.warn("Failed to resolve localized notification text (fallback): userId={}", userId, err);
                return Future.succeededFuture(new ResolvedText(fallbackTitle, fallbackMessage));
            });
    }

    private String getTextValue(JsonNode metadataNode, String fieldName) {
        if (metadataNode == null || !metadataNode.isObject()) {
            return null;
        }
        JsonNode value = metadataNode.get(fieldName);
        return (value != null && value.isTextual()) ? value.asText() : null;
    }

    private static final class ResolvedText {
        private final String title;
        private final String message;

        private ResolvedText(String title, String message) {
            this.title = title;
            this.message = message;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }
    }
}
