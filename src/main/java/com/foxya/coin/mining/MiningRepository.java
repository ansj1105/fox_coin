package com.foxya.coin.mining;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningHistory;
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
    
    private static final RowMapper<MiningHistory> MINING_HISTORY_MAPPER = row -> MiningHistory.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .level(row.getInteger("level"))
        .amount(row.getBigDecimal("amount"))
        .type(getStringColumnValue(row, "type"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(row.getLocalDateTime("created_at"))
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
    
    /**
     * 채굴 내역 조회 (래퍼럴 수익 제외)
     * @param client SQL 클라이언트
     * @param userId 사용자 ID
     * @param period 기간 (ALL, TODAY, WEEK, MONTH, YEAR)
     * @param limit 조회 개수
     * @param offset 시작 위치
     */
    public Future<List<MiningHistory>> getMiningHistory(SqlClient client, Long userId, String period, Integer limit, Integer offset) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            SELECT 
                mh.id,
                mh.user_id,
                mh.level,
                mh.amount,
                mh.type,
                mh.status,
                mh.created_at
            FROM mining_history mh
            WHERE mh.user_id = #{userId}
                AND mh.status = 'COMPLETED'
                AND (#{start_date} IS NULL OR mh.created_at >= #{start_date})
            ORDER BY mh.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("start_date", startDate != null ? startDate.atStartOfDay() : null);
        params.put("limit", limit);
        params.put("offset", offset);
        
        return query(client, query, params)
            .map(rows -> {
                List<MiningHistory> history = new ArrayList<>();
                for (Row row : rows) {
                    history.add(MINING_HISTORY_MAPPER.map(row));
                }
                return history;
            })
            .onFailure(throwable -> log.error("채굴 내역 조회 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 채굴 내역 총 개수 조회
     */
    public Future<Long> getMiningHistoryCount(SqlClient client, Long userId, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            SELECT COUNT(*) as total
            FROM mining_history mh
            WHERE mh.user_id = #{userId}
                AND mh.status = 'COMPLETED'
                AND (#{start_date} IS NULL OR mh.created_at >= #{start_date})
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("start_date", startDate != null ? startDate.atStartOfDay() : null);
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getLong("total");
                }
                return 0L;
            })
            .onFailure(throwable -> log.error("채굴 내역 개수 조회 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 채굴 내역 총 합계 조회
     */
    public Future<BigDecimal> getMiningHistoryTotalAmount(SqlClient client, Long userId, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            SELECT COALESCE(SUM(mh.amount), 0) as total_amount
            FROM mining_history mh
            WHERE mh.user_id = #{userId}
                AND mh.status = 'COMPLETED'
                AND (#{start_date} IS NULL OR mh.created_at >= #{start_date})
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("start_date", startDate != null ? startDate.atStartOfDay() : null);
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    BigDecimal total = rows.iterator().next().getBigDecimal("total_amount");
                    return total != null ? total : BigDecimal.ZERO;
                }
                return BigDecimal.ZERO;
            })
            .onFailure(throwable -> log.error("채굴 내역 총 합계 조회 실패 - userId: {}", userId, throwable));
    }
    
    private LocalDate getStartDateForPeriod(String period) {
        if (period == null || period.equals("ALL")) {
            return null;
        }
        
        LocalDate now = LocalDate.now();
        return switch (period) {
            case "TODAY" -> now;
            case "WEEK" -> now.minusWeeks(1);
            case "MONTH" -> now.minusMonths(1);
            case "YEAR" -> now.minusYears(1);
            default -> null;
        };
    }
}

