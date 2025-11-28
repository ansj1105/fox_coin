package com.foxya.coin.referral;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.referral.entities.ReferralRelation;
import com.foxya.coin.referral.entities.ReferralStats;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
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
     * 레퍼럴 관계 존재 여부 확인
     */
    public Future<Boolean> existsReferralRelation(SqlClient client, Long referredId) {
        String sql = QueryBuilder
            .select("referral_relations", "COUNT(*) as count")
            .where("referred_id", Op.Equal, "referred_id")
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
     * 직접 추천 수 조회 (모든 상태 포함)
     */
    public Future<Integer> getDirectReferralCount(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("referral_relations", "COUNT(*) as count")
            .where("referrer_id", Op.Equal, "referrer_id")
            .where("level", Op.Equal, "level")
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
     * 전체 팀원 수 조회 (ACTIVE 상태만)
     */
    public Future<Integer> getActiveTeamCount(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("referral_relations", "COUNT(*) as count")
            .where("referrer_id", Op.Equal, "referrer_id")
            .where("status", Op.Equal, "status")
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
}

