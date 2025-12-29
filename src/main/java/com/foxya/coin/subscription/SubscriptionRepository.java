package com.foxya.coin.subscription;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.subscription.entities.Subscription;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
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
        String sql = QueryBuilder
            .select("subscriptions", "id", "user_id", "package_type", "is_active", "started_at", "expires_at", "created_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("is_active", Op.Equal, "is_active")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("is_active", true);
        
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
        String sql = QueryBuilder
            .insert("subscriptions", "user_id", "package_type", "is_active", "started_at", "expires_at")
            .onConflict("user_id")
            .doUpdateCustom("package_type = EXCLUDED.package_type, is_active = true, started_at = CURRENT_TIMESTAMP, expires_at = EXCLUDED.expires_at, updated_at = CURRENT_TIMESTAMP")
            .returningColumns("id, user_id, package_type, is_active, started_at, expires_at, created_at, updated_at")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("package_type", packageType);
        params.put("is_active", true);
        params.put("started_at", java.time.LocalDateTime.now());
        params.put("expires_at", expiresAt);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return SUBSCRIPTION_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

