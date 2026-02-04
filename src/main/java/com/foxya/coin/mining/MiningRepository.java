package com.foxya.coin.mining;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningHistory;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.mining.entities.MiningSession;
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
        .dailyMaxVideos(row.getInteger("daily_max_videos"))
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
        .type(row.getString("type"))
        .status(row.getString("status"))
        .createdAt(row.getLocalDateTime("created_at"))
        .build();

    private static final RowMapper<MiningSession> MINING_SESSION_MAPPER = row -> MiningSession.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .startedAt(row.getLocalDateTime("started_at"))
        .endsAt(row.getLocalDateTime("ends_at"))
        .ratePerHour(row.getBigDecimal("rate_per_hour"))
        .lastSettledAt(row.getLocalDateTime("last_settled_at"))
        .createdAt(row.getLocalDateTime("created_at"))
        .build();
    
    public Future<MiningLevel> getMiningLevelByLevel(SqlClient client, Integer level) {
        String sql = QueryBuilder
            .select("mining_levels", "id", "level", "daily_max_mining", "daily_max_videos", "created_at", "updated_at")
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
            .select("mining_levels", "id", "level", "daily_max_mining", "daily_max_videos", "created_at", "updated_at")
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
            .andWhere("deleted_at", Op.IsNull)
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
        String sql = QueryBuilder
            .insert("daily_mining", "user_id", "mining_date", "mining_amount", "reset_at")
            .onConflict("user_id, mining_date")
            .doUpdateCustom("mining_amount = EXCLUDED.mining_amount, reset_at = EXCLUDED.reset_at, updated_at = CURRENT_TIMESTAMP")
            .returningColumns("id, user_id, mining_date, mining_amount, reset_at, created_at, updated_at")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("mining_date", date);
        params.put("mining_amount", amount);
        params.put("reset_at", resetAt);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return DAILY_MINING_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    /**
     * 채굴 내역 1건 추가 (1시간 채굴 세션 완료 시 1건, amount = 해당 세션의 1시간 채굴량)
     */
    public Future<MiningHistory> insertMiningHistory(SqlClient client, Long userId, Integer level, BigDecimal amount, String type, String status) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("level", level != null ? level : 1);
        params.put("amount", amount);
        params.put("type", type != null ? type : "BROADCAST_WATCH");
        params.put("status", status != null ? status : "COMPLETED");
        String sql = QueryBuilder.insert("mining_history", params, "id, user_id, level, amount, type, status, created_at");
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return MINING_HISTORY_MAPPER.map(rows.iterator().next());
                }
                return null;
            })
            .onFailure(throwable -> log.error("채굴 내역 추가 실패 - userId: {}", userId, throwable));
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
        
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectAlias("mining_history", "mh", 
                "mh.id", "mh.user_id", "mh.level", "mh.amount", "mh.type", "mh.status", "mh.created_at")
            .where("mh.user_id", Op.Equal, "userId")
            .andWhere("mh.status", Op.Equal, "status")
            .andWhere("mh.deleted_at", Op.IsNull);
        
        // 날짜 조건 추가 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            queryBuilder = queryBuilder.andWhere("mh.created_at", Op.GreaterThanOrEqual, "start_date");
        }
        
        String sql = queryBuilder
            .orderBy("mh.created_at", Sort.DESC)
            .limit(limit)
            .offset(offset)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("status", "COMPLETED");
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        
        return query(client, sql, params)
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
        
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .count("mining_history", "mh", "total")
            .where("mh.user_id", Op.Equal, "userId")
            .andWhere("mh.status", Op.Equal, "status")
            .andWhere("mh.deleted_at", Op.IsNull);
        
        // 날짜 조건 추가 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            queryBuilder = queryBuilder.andWhere("mh.created_at", Op.GreaterThanOrEqual, "start_date");
        }
        
        String query = queryBuilder.build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("status", "COMPLETED");
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        
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
     * 오늘 채굴 내역 건수 (mining_history 기준). 레거시: 일일 영상 상한/잔여 횟수는 countSessionsStartedToday(mining_sessions) 사용.
     */
    public Future<Integer> getTodayMiningCount(SqlClient client, Long userId) {
        LocalDate today = LocalDate.now();
        String sql = QueryBuilder
            .count("mining_history", "mh", "total")
            .where("mh.user_id", Op.Equal, "userId")
            .andWhere("mh.status", Op.Equal, "status")
            .andWhere("mh.deleted_at", Op.IsNull)
            .andWhere("mh.created_at", Op.GreaterThanOrEqual, "today_start")
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("status", "COMPLETED");
        params.put("today_start", today.atStartOfDay());
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long total = rows.iterator().next().getLong("total");
                    return total != null ? total.intValue() : 0;
                }
                return 0;
            })
            .onFailure(throwable -> log.error("오늘 채굴 횟수 조회 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 채굴 내역 총 합계 조회
     */
    public Future<BigDecimal> getMiningHistoryTotalAmount(SqlClient client, Long userId, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectAlias("mining_history", "mh", "COALESCE(SUM(mh.amount), 0) as total_amount")
            .where("mh.user_id", Op.Equal, "userId")
            .andWhere("mh.status", Op.Equal, "status")
            .andWhere("mh.deleted_at", Op.IsNull);
        
        // 날짜 조건 추가 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            queryBuilder = queryBuilder.andWhere("mh.created_at", Op.GreaterThanOrEqual, "start_date");
        }
        
        String query = queryBuilder.build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("status", "COMPLETED");
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        
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
    
    /**
     * 사용자의 모든 일일 채굴 Soft Delete
     */
    public Future<Void> softDeleteDailyMiningByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("daily_mining", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deleted_at", java.time.LocalDateTime.now());
        params.put("updated_at", java.time.LocalDateTime.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Daily mining soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("일일 채굴 Soft Delete 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 사용자의 모든 채굴 내역 Soft Delete
     */
    public Future<Void> softDeleteMiningHistoryByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("mining_history", "deleted_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deleted_at", java.time.LocalDateTime.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Mining history soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("채굴 내역 Soft Delete 실패 - userId: {}", userId, throwable));
    }

    // ========== mining_sessions (1시간 채굴 세션) ==========

    /** 현재 진행 중인 채굴 세션 (ends_at > now) */
    public Future<MiningSession> getActiveMiningSession(SqlClient client, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String sql = QueryBuilder
            .select("mining_sessions")
            .where("user_id", Op.Equal, "userId")
            .andWhere("ends_at", Op.GreaterThan, "now")
            .orderBy("ends_at", Sort.DESC)
            .limit(1)
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("now", now);
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return MINING_SESSION_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }

    /**
     * 정산 대기 세션이 있는 user_id 목록 (last_settled_at < ends_at).
     * 배치에서 미정산 채굴을 mining_history·internal_transfers에 반영할 때 사용.
     */
    public Future<List<Long>> getDistinctUserIdsWithPendingSettlement(SqlClient client) {
        String sql = "SELECT DISTINCT user_id FROM mining_sessions WHERE last_settled_at < ends_at ORDER BY user_id";
        return query(client, sql, Collections.emptyMap())
            .map(rows -> {
                List<Long> userIds = new ArrayList<>();
                rows.forEach(row -> userIds.add(row.getLong("user_id")));
                return userIds;
            });
    }

    /** 정산 대기 세션 (last_settled_at < ends_at) — 진행 중이거나 방금 종료된 세션 포함. settle 후 mining_history 적재에 사용 */
    public Future<MiningSession> getSettlementPendingMiningSession(SqlClient client, Long userId) {
        String sql = "SELECT id, user_id, started_at, ends_at, rate_per_hour, last_settled_at, created_at " +
            "FROM mining_sessions WHERE user_id = #{userId} AND last_settled_at < ends_at " +
            "ORDER BY ends_at DESC LIMIT 1";
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return MINING_SESSION_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }

    /** 채굴 세션 생성 (영상 1회 시청 시) */
    public Future<MiningSession> createMiningSession(SqlClient client, Long userId, LocalDateTime startedAt,
                                                     LocalDateTime endsAt, BigDecimal ratePerHour, LocalDateTime lastSettledAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("started_at", startedAt);
        params.put("ends_at", endsAt);
        params.put("rate_per_hour", ratePerHour);
        params.put("last_settled_at", lastSettledAt);
        String sql = QueryBuilder.insert("mining_sessions", params, "id, user_id, started_at, ends_at, rate_per_hour, last_settled_at, created_at");
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return MINING_SESSION_MAPPER.map(rows.iterator().next());
                }
                return null;
            })
            .onFailure(throwable -> log.error("채굴 세션 생성 실패 - userId: {}", userId, throwable));
    }

    /** 세션의 last_settled_at 업데이트 (settle 후) */
    public Future<Integer> updateMiningSessionLastSettled(SqlClient client, Long sessionId, LocalDateTime lastSettledAt) {
        String sql = QueryBuilder
            .update("mining_sessions", "last_settled_at")
            .where("id", Op.Equal, "id")
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("id", sessionId);
        params.put("last_settled_at", lastSettledAt);
        return query(client, sql, params)
            .map(rows -> rows.size())
            .onFailure(throwable -> log.error("채굴 세션 last_settled_at 업데이트 실패 - sessionId: {}", sessionId, throwable));
    }

    /** 오늘 시작한 채굴 세션 수 (일일 영상 상한 체크용) */
    public Future<Integer> countSessionsStartedToday(SqlClient client, Long userId, LocalDate today) {
        String sql = QueryBuilder
            .count("mining_sessions", "ms", "total")
            .where("ms.user_id", Op.Equal, "userId")
            .andWhere("ms.started_at", Op.GreaterThanOrEqual, "today_start")
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("today_start", today.atStartOfDay());
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long total = rows.iterator().next().getLong("total");
                    return total != null ? total.intValue() : 0;
                }
                return 0;
            })
            .onFailure(throwable -> log.error("오늘 채굴 세션 수 조회 실패 - userId: {}", userId, throwable));
    }
}

