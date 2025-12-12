package com.foxya.coin.mining;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class MiningRepository extends BaseRepository {
    
    private static final RowMapper<MiningLevel> MINING_LEVEL_MAPPER = row -> MiningLevel.builder()
        .id(row.getInteger("id"))
        .level(row.getInteger("level"))
        .dailyMaxMining(row.getBigDecimal("daily_max_mining"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    private static final RowMapper<DailyMining> DAILY_MINING_MAPPER = row -> DailyMining.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .miningDate(row.getLocalDate("mining_date"))
        .miningAmount(row.getBigDecimal("mining_amount"))
        .resetAt(row.getLocalDateTime("reset_at"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    public Future<MiningLevel> getMiningLevelByLevel(SqlClient client, Integer level) {
        String sql = QueryBuilder
            .select("mining_levels", "id", "level", "daily_max_mining", "created_at", "updated_at")
            .where("level", Op.Equal, "level")
            .build();
        
        return query(client, sql, Collections.singletonMap("level", level))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return MINING_LEVEL_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    public Future<List<MiningLevel>> getAllMiningLevels(SqlClient client) {
        String sql = QueryBuilder
            .select("mining_levels", "id", "level", "daily_max_mining", "created_at", "updated_at")
            .orderBy("level", Sort.ASC)
            .build();
        
        return query(client, sql)
            .map(rows -> {
                List<MiningLevel> levels = new ArrayList<>();
                for (Row row : rows) {
                    levels.add(MINING_LEVEL_MAPPER.map(row));
                }
                return levels;
            });
    }
    
    public Future<DailyMining> getDailyMining(SqlClient client, Long userId, LocalDate date) {
        String sql = QueryBuilder
            .select("daily_mining", "id", "user_id", "mining_date", "mining_amount", "reset_at", "created_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("mining_date", Op.Equal, "date")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("date", date);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return DAILY_MINING_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    public Future<DailyMining> createOrUpdateDailyMining(SqlClient client, Long userId, LocalDate date, 
                                                          BigDecimal amount, LocalDateTime resetAt) {
        // ON CONFLICT는 PostgreSQL 특화 기능으로 QueryBuilder에서 직접 지원하지 않으므로 selectStringQuery 사용
        String sql = """
            INSERT INTO daily_mining (user_id, mining_date, mining_amount, reset_at)
            VALUES (#{userId}, #{date}, #{amount}, #{resetAt})
            ON CONFLICT (user_id, mining_date)
            DO UPDATE SET
                mining_amount = EXCLUDED.mining_amount,
                reset_at = EXCLUDED.reset_at,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id, user_id, mining_date, mining_amount, reset_at, created_at, updated_at
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("date", date);
        params.put("amount", amount);
        params.put("resetAt", resetAt);
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return DAILY_MINING_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

