package com.foxya.coin.bonus;

import com.foxya.coin.bonus.entities.UserBonus;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BonusRepository extends BaseRepository {
    
    private static final RowMapper<UserBonus> BONUS_MAPPER = row -> UserBonus.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .bonusType(row.getString("bonus_type"))
        .isActive(row.getBoolean("is_active"))
        .expiresAt(row.getLocalDateTime("expires_at"))
        .currentCount(row.getInteger("current_count"))
        .maxCount(row.getInteger("max_count"))
        .metadata(row.getString("metadata"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    public Future<UserBonus> getUserBonus(SqlClient client, Long userId, String bonusType) {
        String sql = QueryBuilder
            .select("user_bonuses", "id", "user_id", "bonus_type", "is_active", "expires_at", "current_count", "max_count", "metadata", "created_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("bonus_type", Op.Equal, "bonusType")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("bonusType", bonusType);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return BONUS_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    public Future<UserBonus> createOrUpdateUserBonus(SqlClient client, Long userId, String bonusType, 
                                                      Boolean isActive, LocalDateTime expiresAt, 
                                                      Integer currentCount, Integer maxCount, String metadata) {
        String sql = """
            INSERT INTO user_bonuses (user_id, bonus_type, is_active, expires_at, current_count, max_count, metadata)
            VALUES (#{userId}, #{bonusType}, #{isActive}, #{expiresAt}, #{currentCount}, #{maxCount}, #{metadata})
            ON CONFLICT (user_id, bonus_type)
            DO UPDATE SET
                is_active = EXCLUDED.is_active,
                expires_at = EXCLUDED.expires_at,
                current_count = EXCLUDED.current_count,
                max_count = EXCLUDED.max_count,
                metadata = EXCLUDED.metadata,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id, user_id, bonus_type, is_active, expires_at, current_count, max_count, metadata, created_at, updated_at
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("bonusType", bonusType);
        params.put("isActive", isActive);
        params.put("expiresAt", expiresAt);
        params.put("currentCount", currentCount);
        params.put("maxCount", maxCount);
        params.put("metadata", metadata);
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return BONUS_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

