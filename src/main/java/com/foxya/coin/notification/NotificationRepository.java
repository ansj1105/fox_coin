package com.foxya.coin.notification;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.notification.entities.Notification;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

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
     * 사용자의 알림 목록 조회
     */
    public Future<List<Notification>> getNotifications(SqlClient client, Long userId, Integer limit, Integer offset) {
        String sql = QueryBuilder
            .select("notifications", "id", "user_id", "type", "title", "message", "is_read", "related_id", "metadata", "created_at", "updated_at")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
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
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("is_read", true);
        params.put("updated_at", java.time.LocalDateTime.now());
        params.put("user_id", userId);
        params.put("is_read_condition", false);
        
        return query(client, sql, params)
            .map(this::successAll);
    }
}

