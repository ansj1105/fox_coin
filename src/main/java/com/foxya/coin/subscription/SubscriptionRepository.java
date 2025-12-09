package com.foxya.coin.subscription;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.subscription.entities.Subscription;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SubscriptionRepository extends BaseRepository {
    
    private static final RowMapper<Subscription> SUBSCRIPTION_MAPPER = row -> Subscription.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .packageType(row.getString("package_type"))
        .isActive(row.getBoolean("is_active"))
        .startedAt(row.getLocalDateTime("started_at"))
        .expiresAt(row.getLocalDateTime("expires_at"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    public Future<Subscription> getActiveSubscription(SqlClient client, Long userId) {
        String sql = """
            SELECT id, user_id, package_type, is_active, started_at, expires_at, created_at, updated_at
            FROM subscriptions
            WHERE user_id = #{userId} AND is_active = true
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return SUBSCRIPTION_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    public Future<Subscription> createSubscription(SqlClient client, Long userId, String packageType, 
                                                  LocalDateTime expiresAt) {
        String sql = """
            INSERT INTO subscriptions (user_id, package_type, is_active, started_at, expires_at)
            VALUES (#{userId}, #{packageType}, true, CURRENT_TIMESTAMP, #{expiresAt})
            ON CONFLICT (user_id)
            DO UPDATE SET
                package_type = EXCLUDED.package_type,
                is_active = true,
                started_at = CURRENT_TIMESTAMP,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id, user_id, package_type, is_active, started_at, expires_at, created_at, updated_at
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("packageType", packageType);
        params.put("expiresAt", expiresAt);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return SUBSCRIPTION_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

