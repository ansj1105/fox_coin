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

    /** 유효 직접 초대 수: 이메일 인증 완료 + 채굴 기록 1건 이상인 referred만 카운트 (JOIN+EXISTS) */
    private static final String SQL_GET_VALID_DIRECT_REFERRAL_COUNT = """
        SELECT COUNT(DISTINCT rr.referred_id)::int as cnt
        FROM referral_relations rr
        INNER JOIN email_verifications ev ON ev.user_id = rr.referred_id AND ev.is_verified = true AND ev.deleted_at IS NULL
        WHERE rr.referrer_id = #{referrer_id}
          AND rr.level = 1
          AND rr.status = 'ACTIVE'
          AND rr.deleted_at IS NULL
          AND (
            EXISTS (SELECT 1 FROM mining_history mh WHERE mh.user_id = rr.referred_id AND mh.deleted_at IS NULL LIMIT 1)
            OR EXISTS (SELECT 1 FROM daily_mining dm WHERE dm.user_id = rr.referred_id AND dm.mining_amount > 0 AND dm.deleted_at IS NULL LIMIT 1)
          )
        """;

    /** 팀 통계: total_revenue(스칼라 서브쿼리), today/week/month/year_revenue(CASE), total_members, new_members_today */
    private static final String SQL_GET_TEAM_SUMMARY = """
        SELECT
            (SELECT COALESCE(SUM(it.amount), 0) FROM internal_transfers it
             WHERE it.receiver_id = #{referrer_id} AND it.transfer_type = 'REFERRAL_REWARD'
               AND it.status = 'COMPLETED' AND (it.deleted_at IS NULL)) as total_revenue,
            COALESCE(SUM(CASE WHEN dm.mining_date = CURRENT_DATE THEN dm.mining_amount ELSE 0 END), 0) as today_revenue,
            COALESCE(SUM(CASE WHEN dm.mining_date >= CURRENT_DATE - INTERVAL '7 days' THEN dm.mining_amount ELSE 0 END), 0) as week_revenue,
            COALESCE(SUM(CASE WHEN dm.mining_date >= CURRENT_DATE - INTERVAL '30 days' THEN dm.mining_amount ELSE 0 END), 0) as month_revenue,
            COALESCE(SUM(CASE WHEN dm.mining_date >= CURRENT_DATE - INTERVAL '1 year' THEN dm.mining_amount ELSE 0 END), 0) as year_revenue,
            COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as total_members,
            COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL
                AND rr.created_at::date = CURRENT_DATE THEN rr.referred_id END) as new_members_today
        FROM referral_relations rr
        LEFT JOIN daily_mining dm ON dm.user_id = rr.referred_id AND (dm.deleted_at IS NULL)
        WHERE rr.referrer_id = #{referrer_id}
          AND rr.status = 'ACTIVE'
          AND rr.deleted_at IS NULL
        """;

    /** 팀 수익 목록: SELECT~WHERE (기간 조건은 revenuePeriodCondition()으로 추가) */
    private static final String SQL_GET_TEAM_REVENUES_BASE = """
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
            AND it.transfer_type = 'REFERRAL_REWARD' AND (it.deleted_at IS NULL)
        WHERE rr.referrer_id = #{referrer_id}
          AND rr.status = 'ACTIVE'
          AND rr.deleted_at IS NULL
        """;

    /** 팀 수익 목록: GROUP BY ~ LIMIT/OFFSET */
    private static final String SQL_GET_TEAM_REVENUES_TAIL = """
        GROUP BY u.id, u.level, u.login_id
        HAVING (SUM(dm.mining_amount) > 0 OR SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED'
            THEN it.amount ELSE 0 END) > 0)
        ORDER BY date DESC, total_revenue DESC
        LIMIT #{limit} OFFSET #{offset}
        """;

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
     * 래퍼럴 수익 누적 (total_reward, today_reward 증가)
     */
    public Future<Void> incrementReward(SqlClient client, Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.succeededFuture();
        }
        String sql = QueryBuilder.update("referral_stats_logs")
            .setCustom("total_reward = COALESCE(total_reward, 0) + #{amount}")
            .setCustom("today_reward = COALESCE(today_reward, 0) + #{amount}")
            .setCustom("updated_at = #{updated_at}")
            .where("user_id", Op.Equal, "user_id")
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());
        return query(client, sql, params)
            .<Void>map(rows -> null)
            .onFailure(throwable -> log.error("래퍼럴 수익 누적 실패 - userId: {}", userId, throwable));
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
     * 사용자의 모든 레퍼럴 관계 Soft Delete (referrer_id 또는 referred_id가 userId인 경우)
     */
    public Future<Void> softDeleteReferralRelationsByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder.update("referral_relations", "deleted_at")
            .where("(referrer_id = #{user_id} OR referred_id = #{user_id})")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", DateUtils.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Referral relations soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("레퍼럴 관계 Soft Delete 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 해당 사용자(referred_id)가 마지막으로 레퍼럴 관계를 삭제한 시각 조회.
     * 삭제 후 재등록 제한(30일) 판단용. 삭제 이력이 없으면 null 반환.
     */
    public Future<LocalDateTime> getLastDeletedAtByReferredId(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .select("referral_relations", "deleted_at")
            .where("referred_id", Op.Equal, "referred_id")
            .andWhere("deleted_at", Op.IsNotNull)
            .appendQueryString("ORDER BY deleted_at DESC LIMIT 1")
            .build();
        return query(client, sql, Collections.singletonMap("referred_id", referredId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return getLocalDateTimeColumnValue(rows.iterator().next(), "deleted_at");
                }
                return null;
            })
            .onFailure(throwable -> log.error("레퍼럴 삭제일 조회 실패 - referredId: {}", referredId, throwable));
    }

    /**
     * 해당 사용자(referred_id)의 마지막 레퍼럴 등록 시각(created_at) 조회.
     * 재등록 1일 1회 제한 판단용. 등록 이력이 없으면 null 반환.
     */
    public Future<LocalDateTime> getLastCreatedAtByReferredId(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .select("referral_relations", "created_at")
            .where("referred_id", Op.Equal, "referred_id")
            .appendQueryString("ORDER BY created_at DESC LIMIT 1")
            .build();
        return query(client, sql, Collections.singletonMap("referred_id", referredId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return getLocalDateTimeColumnValue(rows.iterator().next(), "created_at");
                }
                return null;
            })
            .onFailure(throwable -> log.error("레퍼럴 등록일 조회 실패 - referredId: {}", referredId, throwable));
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
     * 유효 직접 초대 수: 이메일 인증 완료 + 채굴 기록 1건 이상인 referred만 카운트 (채굴 보너스 %·수익률 구간용)
     */
    public Future<Integer> getValidDirectReferralCount(SqlClient client, Long referrerId) {
        String query = QueryBuilder.selectStringQuery(SQL_GET_VALID_DIRECT_REFERRAL_COUNT).build();
        return query(client, query, Collections.singletonMap("referrer_id", referrerId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Object v = rows.iterator().next().getValue("cnt");
                    if (v instanceof Number) return ((Number) v).intValue();
                }
                return 0;
            })
            .onFailure(throwable -> log.error("유효 직접 초대 수 조회 실패 - referrerId: {}", referrerId, throwable));
    }
    
    /**
     * 동일 추천인 하에서, 이미 같은 IP 또는 같은 device_id로 등록된 피추천인이 있는지 (중복 초대 무효 판단용)
     */
    public Future<Boolean> hasReferrerAnyReferredWithSameIpOrDevice(SqlClient client, Long referrerId, String clientIp, String deviceId) {
        if ((clientIp == null || clientIp.isBlank()) && (deviceId == null || deviceId.isBlank())) {
            return Future.succeededFuture(false);
        }
        String ipOrDeviceCondition;
        Map<String, Object> params = new HashMap<>();
        params.put("referrer_id", referrerId);
        if (clientIp != null && !clientIp.isBlank() && deviceId != null && !deviceId.isBlank()) {
            ipOrDeviceCondition = "(d.last_ip = #{client_ip} OR d.device_id = #{device_id})";
            params.put("client_ip", clientIp);
            params.put("device_id", deviceId);
        } else if (clientIp != null && !clientIp.isBlank()) {
            ipOrDeviceCondition = "d.last_ip = #{client_ip}";
            params.put("client_ip", clientIp);
        } else {
            ipOrDeviceCondition = "d.device_id = #{device_id}";
            params.put("device_id", deviceId);
        }
        String sql = QueryBuilder.selectAlias("referral_relations", "rr", "1")
            .innerJoin("devices", "d")
            .on("d.user_id", Op.Equal, "rr.referred_id")
            .appendQueryString("AND d.deleted_at IS NULL")
            .where("rr.referrer_id", Op.Equal, "referrer_id")
            .andWhere("rr.deleted_at", Op.IsNull)
            .andWhere(ipOrDeviceCondition)
            .limit(1)
            .build();
        return query(client, sql, params)
            .map(rows -> rows.iterator().hasNext())
            .onFailure(throwable -> log.error("중복 IP/기기 조회 실패 - referrerId: {}", referrerId, throwable));
    }
    
    /**
     * 팀 통계 정보 조회 (totalRevenue, today/week/month/year_revenue, total_members, new_members_today)
     */
    public Future<TeamInfoResponseDto.SummaryInfo> getTeamSummary(SqlClient client, Long referrerId) {
        String query = QueryBuilder.selectStringQuery(SQL_GET_TEAM_SUMMARY).build();
        
        return query(client, query, Collections.singletonMap("referrer_id", referrerId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    return TeamInfoResponseDto.SummaryInfo.builder()
                        .totalRevenue(getBigDecimalColumnValue(row, "total_revenue"))
                        .todayRevenue(getBigDecimalColumnValue(row, "today_revenue"))
                        .weekRevenue(getBigDecimalColumnValue(row, "week_revenue"))
                        .monthRevenue(getBigDecimalColumnValue(row, "month_revenue"))
                        .yearRevenue(getBigDecimalColumnValue(row, "year_revenue"))
                        .totalMembers(getLongColumnValue(row, "total_members"))
                        .newMembersToday(getLongColumnValue(row, "new_members_today"))
                        .build();
                }
                return TeamInfoResponseDto.SummaryInfo.builder()
                    .totalRevenue(BigDecimal.ZERO)
                    .todayRevenue(BigDecimal.ZERO)
                    .weekRevenue(BigDecimal.ZERO)
                    .monthRevenue(BigDecimal.ZERO)
                    .yearRevenue(BigDecimal.ZERO)
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
        
        String sql = SQL_GET_TEAM_REVENUES_BASE + (startDate != null ? revenuePeriodCondition() : "") + SQL_GET_TEAM_REVENUES_TAIL;
        String query = QueryBuilder.selectStringQuery(sql).build();
        
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
     * 팀 수익 조회용 기간 조건 절 (getTeamRevenues에서 사용, 파라미터 start_date 필요)
     */
    private static String revenuePeriodCondition() {
        return " AND (dm.mining_date >= #{start_date} OR it.created_at >= #{start_date})";
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
