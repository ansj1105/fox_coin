package com.foxya.coin.notification;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.notification.entities.Notification;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NotificationRepository extends BaseRepository {
    
    private final RowMapper<Notification> notificationMapper = row -> {
        Notification.NotificationBuilder builder = Notification.builder()
            .id(getLongColumnValue(row, "id"))
            .userId(getLongColumnValue(row, "user_id"))
            .type(NotificationType.valueOf(getStringColumnValue(row, "type")))
            .title(getStringColumnValue(row, "title"))
            .message(getStringColumnValue(row, "message"))
            .isRead(getBooleanColumnValue(row, "is_read"))
            .relatedId(getLongColumnValue(row, "related_id"))
            .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
            .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"));
        
        // metadata JSONB 처리
        JsonObject metadataJson = getJsonObjectColumnValue(row, "metadata");
        if (metadataJson != null) {
            builder.metadata(metadataJson.encode());
        }
        
        return builder.build();
    };
    
    /**
     * 알림 생성 (입금/출금 완료 등). 추후 FCM 푸시 시 metadata 활용.
     */
    public Future<Notification> insert(SqlClient client, Long userId, NotificationType type, String title, String message,
                                       Long relatedId, String metadata) {
        LocalDateTime now = DateUtils.now();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("type", type.name());
        params.put("title", title);
        params.put("message", message);
        params.put("is_read", false);
        params.put("related_id", relatedId);
        params.put("metadata", metadata);
        params.put("created_at", now);
        params.put("updated_at", now);
        String sql = QueryBuilder.insert("notifications", params,
            "id, user_id, type, title, message, is_read, related_id, metadata, created_at, updated_at");
        return query(client, sql, params)
            .map(rows -> fetchOne(notificationMapper, rows))
            .onFailure(e -> log.error("알림 생성 실패 - userId: {}, type: {}", userId, type, e));
    }

    /**
     * 사용자의 알림 목록 조회
     */
    public Future<Boolean> existsByUserTypeAndRelatedId(SqlClient client, Long userId, NotificationType type, Long relatedId) {
        if (relatedId == null) {
            return Future.succeededFuture(false);
        }

        String sql = QueryBuilder
            .count("notifications")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("type", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "type")
            .andWhere("related_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "related_id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("type", type.name());
        params.put("related_id", relatedId);

        return query(client, sql, params)
            .map(rows -> rows.iterator().hasNext() && rows.iterator().next().getLong("count") > 0L);
    }

    public Future<Boolean> existsByUserTypeAndCreatedDate(SqlClient client, Long userId, NotificationType type, LocalDate localDate) {
        String sql = """
            SELECT COUNT(*) AS count
            FROM notifications
            WHERE user_id = #{user_id}
              AND type = #{type}
              AND created_at::date = #{local_date}
              AND deleted_at IS NULL
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("type", type.name());
        params.put("local_date", localDate);

        return query(client, sql, params)
            .map(rows -> rows.iterator().hasNext() && rows.iterator().next().getLong("count") > 0L);
    }

    public Future<List<Notification>> getNotifications(SqlClient client, Long userId, Integer limit, Integer offset) {
        String sql = QueryBuilder
            .select("notifications", "id", "user_id", "type", "title", "message", "is_read", "related_id", "metadata", "created_at", "updated_at")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .orderBy("created_at", Sort.DESC)
            .limitRefactoring()
            .offsetRefactoring()
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("limit", limit);
        params.put("offset", offset);
        
        return query(client, sql, params)
            .map(rows -> {
                List<Notification> notifications = new ArrayList<>();
                for (Row row : rows) {
                    notifications.add(notificationMapper.map(row));
                }
                return notifications;
            });
    }
    
    /**
     * 사용자의 읽지 않은 알림 개수 조회
     */
    public Future<Integer> getUnreadCount(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .count("notifications")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("is_read", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "is_read")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("is_read", false);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getInteger("count");
                }
                return 0;
            });
    }
    
    /**
     * 사용자의 전체 알림 개수 조회
     */
    public Future<Long> getTotalCount(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .count("notifications")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getLong("count");
                }
                return 0L;
            });
    }
    
    /**
     * 특정 알림을 읽음 처리
     */
    public Future<Boolean> markAsRead(SqlClient client, Long notificationId, Long userId) {
        String sql = QueryBuilder
            .update("notifications", "is_read", "updated_at")
            .where("id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "id")
            .andWhere("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("is_read", true);
        params.put("updated_at", java.time.LocalDateTime.now());
        params.put("id", notificationId);
        params.put("user_id", userId);
        
        return query(client, sql, params)
            .map(this::success);
    }
    
    /**
     * 사용자의 모든 알림을 읽음 처리
     */
    public Future<Boolean> markAllAsRead(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("notifications", "is_read", "updated_at")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("is_read", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "is_read_condition")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("is_read", true);
        params.put("updated_at", java.time.LocalDateTime.now());
        params.put("user_id", userId);
        params.put("is_read_condition", false);
        
        return query(client, sql, params)
            .map(this::successAll);
    }
    
    /**
     * 특정 알림 Soft Delete
     */
    public Future<Boolean> softDeleteNotificationById(SqlClient client, Long userId, Long notificationId) {
        String sql = QueryBuilder
            .update("notifications", "deleted_at", "updated_at")
            .where("id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "id")
            .andWhere("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("id", notificationId);
        params.put("user_id", userId);
        params.put("deleted_at", java.time.LocalDateTime.now());
        params.put("updated_at", java.time.LocalDateTime.now());

        return query(client, sql, params)
            .map(this::success);
    }

    /**
     * 사용자의 모든 알림 Soft Delete
     */
    public Future<Void> softDeleteNotificationsByUserId(SqlClient client, Long userId) {
        String sql = """
            UPDATE notifications
               SET deleted_at = #{deleted_at},
                   updated_at = #{updated_at}
             WHERE user_id = #{user_id}
               AND deleted_at IS NULL
               AND NOT (
                   type = 'NOTICE'
                   AND (
                       LOWER(COALESCE(metadata->>'important', 'false')) = 'true'
                       OR LOWER(COALESCE(metadata->>'isImportant', 'false')) = 'true'
                       OR LOWER(COALESCE(metadata->>'noticeImportant', 'false')) = 'true'
                       OR LOWER(COALESCE(metadata->>'is_important', 'false')) = 'true'
                       OR UPPER(COALESCE(metadata->>'priority', '')) = 'IMPORTANT'
                       OR LOWER(COALESCE(title, '')) LIKE '%[important]%'
                       OR COALESCE(title, '') LIKE '%중요%'
                   )
               )
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", java.time.LocalDateTime.now());
        params.put("updated_at", java.time.LocalDateTime.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Notifications soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("알림 Soft Delete 실패 - userId: {}", userId, throwable));
    }
}
