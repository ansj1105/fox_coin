package com.foxya.coin.referral;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.referral.dto.TeamInfoResponseDto;
import com.foxya.coin.referral.entities.ReferralRelation;
import com.foxya.coin.referral.entities.ReferralStats;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
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
public class ReferralRepository extends BaseRepository {
    
    private final RowMapper<ReferralRelation> relationMapper = row -> ReferralRelation.builder()
        .id(getLongColumnValue(row, "id"))
        .referrerId(getLongColumnValue(row, "referrer_id"))
        .referredId(getLongColumnValue(row, "referred_id"))
        .level(getIntegerColumnValue(row, "level"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .deletedAt(getLocalDateTimeColumnValue(row, "deleted_at"))
        .build();
    
    private final RowMapper<ReferralStats> statsMapper = row -> ReferralStats.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .directCount(getIntegerColumnValue(row, "direct_count"))
        .teamCount(getIntegerColumnValue(row, "team_count"))
        .totalReward(getBigDecimalColumnValue(row, "total_reward"))
        .todayReward(getBigDecimalColumnValue(row, "today_reward"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    /**
     * 레퍼럴 관계 생성
     */
    public Future<ReferralRelation> createReferralRelation(SqlClient client, Long referrerId, Long referredId, Integer level) {
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", referrerId);
        params.put("referred_id", referredId);
        params.put("level", level);
        params.put("status", "ACTIVE");
        
        String sql = QueryBuilder.insert("referral_relations", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(relationMapper, rows))
            .onFailure(throwable -> log.error("레퍼럴 관계 생성 실패 - referrerId: {}, referredId: {}", referrerId, referredId));
    }
    
    /**
     * 레퍼럴 관계 존재 여부 확인 (삭제되지 않은 것만)
     */
    public Future<Boolean> existsReferralRelation(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .select("referral_relations", "COUNT(*) as count")
            .where("referred_id", Op.Equal, "referred_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        return query(client, sql, Collections.singletonMap("referred_id", referredId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = getLongColumnValue(rows.iterator().next(), "count");
                    return count != null && count > 0;
                }
                return false;
            })
            .onFailure(throwable -> log.error("레퍼럴 관계 존재 여부 확인 실패: {}", referredId));
    }
    
    /**
     * 직접 추천 수 조회 (삭제되지 않은 것만, 모든 상태 포함)
     */
    public Future<Integer> getDirectReferralCount(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("referral_relations", "COUNT(*) as count")
            .where("referrer_id", Op.Equal, "referrer_id")
            .andWhere("level", Op.Equal, "level")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", userId);
        params.put("level", 1);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = getLongColumnValue(rows.iterator().next(), "count");
                    return count != null ? count.intValue() : 0;
                }
                return 0;
            })
            .onFailure(throwable -> log.error("직접 추천 수 조회 실패 - userId: {}", userId));
    }
    
    /**
     * 전체 팀원 수 조회 (삭제되지 않고 ACTIVE 상태만)
     */
    public Future<Integer> getActiveTeamCount(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("referral_relations", "COUNT(*) as count")
            .where("referrer_id", Op.Equal, "referrer_id")
            .andWhere("status", Op.Equal, "status")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", userId);
        params.put("status", "ACTIVE");
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = getLongColumnValue(rows.iterator().next(), "count");
                    return count != null ? count.intValue() : 0;
                }
                return 0;
            })
            .onFailure(throwable -> log.error("전체 팀원 수 조회 실패 - userId: {}", userId));
    }
    
    /**
     * 레퍼럴 통계 조회 또는 생성
     */
    public Future<ReferralStats> getOrCreateStats(SqlClient client, Long userId) {
        String selectSql = QueryBuilder
            .select("referral_stats_logs")
            .where("user_id", Op.Equal, "user_id")
            .build();
        
        return query(client, selectSql, Collections.singletonMap("user_id", userId))
            .compose(rows -> {
                ReferralStats stats = fetchOne(statsMapper, rows);
                if (stats != null) {
                    return Future.succeededFuture(stats);
                }
                
                // 없으면 생성
                Map<String, Object> params = new HashMap<>();
                params.put("user_id", userId);
                params.put("direct_count", 0);
                params.put("team_count", 0);
                params.put("total_reward", BigDecimal.ZERO);
                params.put("today_reward", BigDecimal.ZERO);
                
                String insertSql = QueryBuilder.insert("referral_stats_logs", params, "*");
                
                return query(client, insertSql, params)
                    .map(insertRows -> fetchOne(statsMapper, insertRows));
            })
            .onFailure(throwable -> log.error("레퍼럴 통계 조회/생성 실패 - userId: {}", userId));
    }
    
    /**
     * 레퍼럴 통계 업데이트
     */
    public Future<ReferralStats> updateStats(SqlClient client, Long userId, Integer directCount, Integer teamCount) {
        String sql = QueryBuilder
            .update("referral_stats_logs", "direct_count", "team_count", "updated_at")
            .where("user_id", Op.Equal, "user_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("direct_count", directCount);
        params.put("team_count", teamCount);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(statsMapper, rows))
            .onFailure(throwable -> log.error("레퍼럴 통계 업데이트 실패 - userId: {}", userId));
    }
    
    /**
     * 레퍼럴 관계 조회 (referred_id로, 삭제되지 않은 것만)
     */
    public Future<ReferralRelation> getReferralRelationByReferredId(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .select("referral_relations")
            .where("referred_id", Op.Equal, "referred_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        return query(client, sql, Collections.singletonMap("referred_id", referredId))
            .map(rows -> fetchOne(relationMapper, rows))
            .onFailure(throwable -> log.error("레퍼럴 관계 조회 실패 - referredId: {}", referredId));
    }
    
    /**
     * 레퍼럴 관계 조회 (referred_id로, 삭제된 것도 포함)
     */
    public Future<ReferralRelation> getReferralRelationByReferredIdIncludingDeleted(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .select("referral_relations")
            .where("referred_id", Op.Equal, "referred_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("referred_id", referredId))
            .map(rows -> fetchOne(relationMapper, rows))
            .onFailure(throwable -> log.error("레퍼럴 관계 조회 실패 (삭제 포함) - referredId: {}", referredId));
    }
    
    /**
     * 레퍼럴 관계 삭제 (Soft Delete)
     */
    public Future<Void> deleteReferralRelation(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .update("referral_relations", "deleted_at")
            .where("referred_id", Op.Equal, "referred_id")
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("referred_id", referredId);
        params.put("deleted_at", DateUtils.now());
        
        return query(client, sql, params)
            .compose(rows -> {
                if (rows.rowCount() == 0) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("등록된 레퍼럴 관계가 없습니다."));
                }
                return Future.succeededFuture((Void) null);
            })
            .onFailure(throwable -> log.error("레퍼럴 관계 삭제 실패 - referredId: {}", referredId));
    }
    
    /**
     * 레퍼럴 관계 완전 삭제 (Hard Delete)
     */
    public Future<Void> hardDeleteReferralRelation(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .delete("referral_relations")
            .where("referred_id", Op.Equal, "referred_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("referred_id", referredId))
            .compose(rows -> {
                if (rows.rowCount() == 0) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("레퍼럴 관계가 존재하지 않습니다."));
                }
                return Future.succeededFuture((Void) null);
            })
            .onFailure(throwable -> log.error("레퍼럴 관계 완전 삭제 실패 - referredId: {}", referredId));
    }
    
    /**
     * 레퍼럴 관계 존재 여부 확인 (referred_id로)
     */
    public Future<Boolean> hasReferralRelation(SqlClient client, Long referredId) {
        return getReferralRelationByReferredId(client, referredId)
            .map(relation -> relation != null);
    }
    
    /**
     * 팀 통계 정보 조회
     */
    public Future<TeamInfoResponseDto.SummaryInfo> getTeamSummary(SqlClient client, Long referrerId) {
        // totalRevenue: 팀의 체굴된 총 수익 (래퍼럴 수익) - internal_transfers에서 REFERRAL_REWARD 합계
        // todayRevenue: 팀의 금일 채굴된 총 수익 - daily_mining에서 오늘 합계
        // totalMembers: 추천인으로 등록한 총 인원 - referral_relations에서 referrer_id로 카운트
        // newMembersToday: 금일 추천인 등록한 신규 인원 - referral_relations에서 오늘 생성된 것 카운트
        
        // 복잡한 쿼리이므로 selectStringQuery 사용
        String sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' 
                    THEN it.amount ELSE 0 END), 0) as total_revenue,
                COALESCE(SUM(CASE WHEN dm.mining_date = CURRENT_DATE THEN dm.mining_amount ELSE 0 END), 0) as today_revenue,
                COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as total_members,
                COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL 
                    AND rr.created_at::date = CURRENT_DATE THEN rr.referred_id END) as new_members_today
            FROM referral_relations rr
            LEFT JOIN internal_transfers it ON it.receiver_id = rr.referred_id
                AND it.transfer_type = 'REFERRAL_REWARD'
            LEFT JOIN daily_mining dm ON dm.user_id = rr.referred_id
            WHERE rr.referrer_id = #{referrer_id}
                AND rr.status = 'ACTIVE'
                AND rr.deleted_at IS NULL
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        
        return query(client, query, Collections.singletonMap("referrer_id", referrerId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    return TeamInfoResponseDto.SummaryInfo.builder()
                        .totalRevenue(getBigDecimalColumnValue(row, "total_revenue"))
                        .todayRevenue(getBigDecimalColumnValue(row, "today_revenue"))
                        .totalMembers(getLongColumnValue(row, "total_members"))
                        .newMembersToday(getLongColumnValue(row, "new_members_today"))
                        .build();
                }
                return TeamInfoResponseDto.SummaryInfo.builder()
                    .totalRevenue(BigDecimal.ZERO)
                    .todayRevenue(BigDecimal.ZERO)
                    .totalMembers(0L)
                    .newMembersToday(0L)
                    .build();
            })
            .onFailure(throwable -> log.error("팀 통계 조회 실패 - referrerId: {}", referrerId, throwable));
    }
    
    /**
     * 팀 멤버 목록 조회
     */
    public Future<List<TeamInfoResponseDto.MemberInfo>> getTeamMembers(SqlClient client, Long referrerId, String period, Integer limit, Integer offset) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectAlias("referral_relations", "rr",
                "u.id as user_id", "u.level", "u.login_id as nickname", "rr.created_at as registered_at")
            .leftJoin("users", "u")
            .on("rr.referred_id", Op.Equal, "u.id")
            .where("rr.referrer_id", Op.Equal, "referrer_id")
            .andWhere("rr.status", Op.Equal, "status")
            .andWhere("rr.deleted_at", Op.IsNull);
        
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", referrerId);
        params.put("status", "ACTIVE");
        
        // period에 따라 날짜 필터 추가
        if (startDate != null) {
            queryBuilder.andWhere("rr.created_at", Op.GreaterThanOrEqual, "start_date");
            params.put("start_date", startDate.atStartOfDay());
        }
        
        String query = queryBuilder
            .orderBy("rr.created_at", Sort.DESC)
            .limit(limit)
            .offset(offset)
            .build();
        
        return query(client, query, params)
            .map(rows -> {
                List<TeamInfoResponseDto.MemberInfo> members = new ArrayList<>();
                for (Row row : rows) {
                    members.add(TeamInfoResponseDto.MemberInfo.builder()
                        .userId(getLongColumnValue(row, "user_id"))
                        .level(getIntegerColumnValue(row, "level"))
                        .nickname(getStringColumnValue(row, "nickname"))
                        .registeredAt(getLocalDateTimeColumnValue(row, "registered_at"))
                        .build());
                }
                return members;
            })
            .onFailure(throwable -> log.error("팀 멤버 목록 조회 실패 - referrerId: {}", referrerId, throwable));
    }
    
    /**
     * 팀 멤버 총 개수 조회
     */
    public Future<Long> getTeamMembersCount(SqlClient client, Long referrerId, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .count("referral_relations", "rr", "total")
            .where("rr.referrer_id", Op.Equal, "referrer_id")
            .andWhere("rr.status", Op.Equal, "status")
            .andWhere("rr.deleted_at", Op.IsNull);
        
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", referrerId);
        params.put("status", "ACTIVE");
        
        // period에 따라 날짜 필터 추가
        if (startDate != null) {
            queryBuilder.andWhere("rr.created_at", Op.GreaterThanOrEqual, "start_date");
            params.put("start_date", startDate.atStartOfDay());
        }
        
        String query = queryBuilder.build();
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getLong("total");
                }
                return 0L;
            })
            .onFailure(throwable -> log.error("팀 멤버 총 개수 조회 실패 - referrerId: {}", referrerId, throwable));
    }
    
    /**
     * 팀 수익 목록 조회
     */
    public Future<List<TeamInfoResponseDto.RevenueInfo>> getTeamRevenues(SqlClient client, Long referrerId, String period, Integer limit, Integer offset) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        // 복잡한 쿼리 (JOIN과 집계)이므로 selectStringQuery 사용
        StringBuilder sql = new StringBuilder("""
            SELECT 
                u.id as user_id,
                u.level,
                u.login_id as nickname,
                COALESCE(MAX(dm.mining_date), MAX(it.created_at::date)) as date,
                COALESCE(SUM(CASE WHEN dm.mining_date = CURRENT_DATE THEN dm.mining_amount ELSE 0 END), 0) as today_revenue,
                COALESCE(SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' 
                    THEN it.amount ELSE 0 END), 0) as total_revenue
            FROM referral_relations rr
            LEFT JOIN users u ON u.id = rr.referred_id
            LEFT JOIN daily_mining dm ON dm.user_id = rr.referred_id
            LEFT JOIN internal_transfers it ON it.receiver_id = rr.referred_id
                AND it.transfer_type = 'REFERRAL_REWARD'
            WHERE rr.referrer_id = #{referrer_id}
                AND rr.status = 'ACTIVE'
                AND rr.deleted_at IS NULL
            """);
        
        // period에 따라 날짜 필터 추가
        if (startDate != null) {
            sql.append(" AND (dm.mining_date >= #{start_date} OR it.created_at >= #{start_date})");
        }
        
        sql.append("""
            GROUP BY u.id, u.level, u.login_id
            HAVING (SUM(dm.mining_amount) > 0 OR SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' 
                THEN it.amount ELSE 0 END) > 0)
            ORDER BY date DESC, total_revenue DESC
            LIMIT #{limit} OFFSET #{offset}
            """);
        
        String query = QueryBuilder.selectStringQuery(sql.toString()).build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", referrerId);
        params.put("limit", limit);
        params.put("offset", offset);
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        
        return query(client, query, params)
            .map(rows -> {
                List<TeamInfoResponseDto.RevenueInfo> revenues = new ArrayList<>();
                for (Row row : rows) {
                    LocalDateTime date = null;
                    if (row.getValue("date") != null) {
                        if (row.getValue("date") instanceof LocalDate) {
                            date = ((LocalDate) row.getValue("date")).atStartOfDay();
                        } else if (row.getValue("date") instanceof LocalDateTime) {
                            date = (LocalDateTime) row.getValue("date");
                        }
                    }
                    
                    revenues.add(TeamInfoResponseDto.RevenueInfo.builder()
                        .userId(getLongColumnValue(row, "user_id"))
                        .level(getIntegerColumnValue(row, "level"))
                        .nickname(getStringColumnValue(row, "nickname"))
                        .date(date)
                        .todayRevenue(getBigDecimalColumnValue(row, "today_revenue"))
                        .totalRevenue(getBigDecimalColumnValue(row, "total_revenue"))
                        .build());
                }
                return revenues;
            })
            .onFailure(throwable -> log.error("팀 수익 목록 조회 실패 - referrerId: {}", referrerId, throwable));
    }
    
    /**
     * 기간에 따른 시작 날짜 계산
     */
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

