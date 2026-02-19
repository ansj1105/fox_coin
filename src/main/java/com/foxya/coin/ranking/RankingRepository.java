package com.foxya.coin.ranking;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RankingRepository extends BaseRepository {

    /**
     * CTE user_stats 결과를 조회하는 SELECT (개인 랭킹용).
     * 랭킹 집계 = (채굴된 코인 + 레퍼럴 수익) + (팀원 수 × 20).
     */
    private static final String SQL_PERSONAL_RANKING_FROM_USER_STATS =
        "SELECT user_id, nickname, level, country_code, (mining_amount + referral_reward) as total_amount, team_count, ((mining_amount + referral_reward) + (team_count * 20)) as aggregation FROM user_stats";

    /**
     * 국가별 팀 랭킹 조회.
     * 집계 기준: 개인 랭킹과 동일 — totalMinedCoins = (채굴+레퍼럴) 합계(수익 0인 사용자 제외), aggregation = totalMinedCoins + (totalMembers × 20).
     * 표출: totalMinedCoins > 0 인 국가만 포함.
     *
     * @param period 기간 (ALL, TODAY, WEEK, MONTH, YEAR)
     */
    public Future<List<CountryRanking>> getCountryRankings(SqlClient client, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        StringBuilder cteSql = new StringBuilder("WITH mining_stats AS (")
            .append(" SELECT dm.user_id, COALESCE(SUM(dm.mining_amount), 0) as mining_amount")
            .append(" FROM daily_mining dm");
        if (startDate != null) {
            cteSql.append(" WHERE dm.mining_date >= #{start_date}");
        }
        cteSql.append(" GROUP BY dm.user_id")
            .append(" ), referral_stats AS (")
            .append(" SELECT it.receiver_id as user_id, COALESCE(SUM(it.amount), 0) as referral_reward")
            .append(" FROM internal_transfers it")
            .append(" WHERE it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' AND (it.deleted_at IS NULL)");
        if (startDate != null) {
            cteSql.append(" AND it.created_at >= #{start_date}");
        }
        cteSql.append(" GROUP BY it.receiver_id")
            .append(" ), team_stats AS (")
            .append(" SELECT rr.referrer_id as user_id,")
            .append(" COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as team_count")
            .append(" FROM referral_relations rr")
            .append(" GROUP BY rr.referrer_id")
            .append(" ), user_stats AS (")
            .append(" SELECT ")
            .append(" u.id as user_id,")
            .append(" COALESCE(u.country_code, 'UNKNOWN') as country_code,")
            .append(" COALESCE(ms.mining_amount, 0) as mining_amount,")
            .append(" COALESCE(rs.referral_reward, 0) as referral_reward")
            .append(" FROM users u")
            .append(" LEFT JOIN mining_stats ms ON ms.user_id = u.id")
            .append(" LEFT JOIN referral_stats rs ON rs.user_id = u.id")
            .append(" LEFT JOIN team_stats ts ON ts.user_id = u.id")
            .append(" WHERE u.status = 'ACTIVE' AND (ts.user_id IS NOT NULL OR u.referral_code IS NOT NULL)")
            .append(" ) , country_stats AS (")
            .append(" SELECT country_code,")
            .append(" COUNT(DISTINCT user_id) as total_members,")
            .append(" SUM(CASE WHEN (mining_amount + referral_reward) > 0 THEN (mining_amount + referral_reward) ELSE 0 END) as total_mined_coins")
            .append(" FROM user_stats")
            .append(" GROUP BY country_code")
            .append(" HAVING SUM(CASE WHEN (mining_amount + referral_reward) > 0 THEN (mining_amount + referral_reward) ELSE 0 END) > 0")
            .append(" )")
            .append(" SELECT country_code, total_members, total_mined_coins, (total_mined_coins + (total_members * 20)) as aggregation")
            .append(" FROM country_stats")
            .append(" ORDER BY aggregation DESC, total_mined_coins DESC")
            .append(" LIMIT 50");
        
        String query = QueryBuilder.selectStringQuery(cteSql.toString()).build();
        Map<String, Object> params = new HashMap<>();
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        
        return query(client, query, params)
            .map(rows -> {
                List<CountryRanking> rankings = new ArrayList<>();
                for (Row row : rows) {
                    CountryRanking ranking = CountryRanking.builder()
                        .countryCode(getStringColumnValue(row, "country_code"))
                        .totalMembers(getLongColumnValue(row, "total_members"))
                        .totalMinedCoins(getBigDecimalColumnValue(row, "total_mined_coins"))
                        .aggregation(getBigDecimalColumnValue(row, "total_mined_coins"))
                        .build();
                    rankings.add(ranking);
                }
                return rankings;
            })
            .onFailure(throwable -> log.error("국가별 랭킹 조회 실패: {}", throwable.getMessage()));
    }
    
    /**
     * 특정 국가의 랭킹 정보 조회. 집계 기준은 getCountryRankings와 동일 (채굴+레퍼럴, 수익 0 사용자 제외).
     */
    public Future<CountryRanking> getCountryRankingByCode(SqlClient client, String countryCode, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        StringBuilder cteSql = new StringBuilder("WITH mining_stats AS (")
            .append(" SELECT dm.user_id, COALESCE(SUM(dm.mining_amount), 0) as mining_amount")
            .append(" FROM daily_mining dm");
        if (startDate != null) {
            cteSql.append(" WHERE dm.mining_date >= #{start_date}");
        }
        cteSql.append(" GROUP BY dm.user_id")
            .append(" ), referral_stats AS (")
            .append(" SELECT it.receiver_id as user_id, COALESCE(SUM(it.amount), 0) as referral_reward")
            .append(" FROM internal_transfers it")
            .append(" WHERE it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' AND (it.deleted_at IS NULL)");
        if (startDate != null) {
            cteSql.append(" AND it.created_at >= #{start_date}");
        }
        cteSql.append(" GROUP BY it.receiver_id")
            .append(" ), team_stats AS (")
            .append(" SELECT rr.referrer_id as user_id,")
            .append(" COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as team_count")
            .append(" FROM referral_relations rr")
            .append(" GROUP BY rr.referrer_id")
            .append(" ), user_stats AS (")
            .append(" SELECT u.id as user_id, COALESCE(u.country_code, 'UNKNOWN') as country_code,")
            .append(" COALESCE(ms.mining_amount, 0) as mining_amount,")
            .append(" COALESCE(rs.referral_reward, 0) as referral_reward")
            .append(" FROM users u")
            .append(" LEFT JOIN mining_stats ms ON ms.user_id = u.id")
            .append(" LEFT JOIN referral_stats rs ON rs.user_id = u.id")
            .append(" LEFT JOIN team_stats ts ON ts.user_id = u.id")
            .append(" WHERE u.status = 'ACTIVE' AND (ts.user_id IS NOT NULL OR u.referral_code IS NOT NULL)")
            .append(" AND COALESCE(u.country_code, 'UNKNOWN') = #{country_code}")
            .append(" ), country_stats AS (")
            .append(" SELECT country_code, COUNT(DISTINCT user_id) as total_members,")
            .append(" SUM(CASE WHEN (mining_amount + referral_reward) > 0 THEN (mining_amount + referral_reward) ELSE 0 END) as total_mined_coins")
            .append(" FROM user_stats")
            .append(" GROUP BY country_code")
            .append(" )")
            .append(" SELECT country_code, total_members, total_mined_coins, (total_mined_coins + (total_members * 20)) as aggregation FROM country_stats");
        
        String query = QueryBuilder.selectStringQuery(cteSql.toString()).build();
        Map<String, Object> params = new HashMap<>();
        params.put("country_code", countryCode);
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    return CountryRanking.builder()
                        .countryCode(getStringColumnValue(row, "country_code"))
                        .totalMembers(getLongColumnValue(row, "total_members"))
                        .totalMinedCoins(getBigDecimalColumnValue(row, "total_mined_coins"))
                        .aggregation(getBigDecimalColumnValue(row, "total_mined_coins"))
                        .build();
                }
                return null;
            })
            .onFailure(throwable -> log.error("국가 랭킹 조회 실패 - countryCode: {}", countryCode));
    }
    
    /**
     * 사용자의 국가 코드 조회
     */
    public Future<String> getUserCountryCode(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("users", "country_code")
            .whereById()
            .build();
        
        return query(client, sql, Collections.singletonMap("id", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return getStringColumnValue(rows.iterator().next(), "country_code");
                }
                return null;
            })
            .onFailure(throwable -> log.error("사용자 국가 코드 조회 실패 - userId: {}", userId));
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
     * 개인 랭킹 조회
     * @param client SQL 클라이언트
     * @param scope 범위 (REGIONAL: 같은 국가, GLOBAL: 전체)
     * @param period 기간 (ALL, TODAY, WEEK, MONTH, YEAR)
     * @param countryCode 국가 코드 (REGIONAL일 때만 사용)
     */
    public Future<List<PersonalRanking>> getPersonalRankings(SqlClient client, String scope, String period, String countryCode) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        // WITH 절은 QueryBuilder가 직접 지원하지 않으므로 문자열로 작성
        StringBuilder cteSql = new StringBuilder("WITH mining_stats AS (")
            .append(" SELECT dm.user_id, COALESCE(SUM(dm.mining_amount), 0) as mining_amount")
            .append(" FROM daily_mining dm");
        if (startDate != null) {
            cteSql.append(" WHERE dm.mining_date >= #{start_date}");
        }
        cteSql.append(" GROUP BY dm.user_id")
            .append(" ), referral_stats AS (")
            .append(" SELECT it.receiver_id as user_id, COALESCE(SUM(it.amount), 0) as referral_reward")
            .append(" FROM internal_transfers it")
            .append(" WHERE it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' AND (it.deleted_at IS NULL)");
        if (startDate != null) {
            cteSql.append(" AND it.created_at >= #{start_date}");
        }
        cteSql.append(" GROUP BY it.receiver_id")
            .append(" ), team_stats AS (")
            .append(" SELECT rr.referrer_id as user_id,")
            .append(" COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as team_count")
            .append(" FROM referral_relations rr")
            .append(" GROUP BY rr.referrer_id")
            .append(" ), user_stats AS (")
            .append(" SELECT ")
            .append(" u.id as user_id,")
            .append(" COALESCE(NULLIF(TRIM(u.nickname), ''), '') as nickname,")
            .append(" u.level,")
            .append(" COALESCE(u.country_code, 'UNKNOWN') as country_code,")
            .append(" COALESCE(ms.mining_amount, 0) as mining_amount,")
            .append(" COALESCE(rs.referral_reward, 0) as referral_reward,")
            .append(" COALESCE(ts.team_count, 0) as team_count")
            .append(" FROM users u")
            .append(" LEFT JOIN mining_stats ms ON ms.user_id = u.id")
            .append(" LEFT JOIN referral_stats rs ON rs.user_id = u.id")
            .append(" LEFT JOIN team_stats ts ON ts.user_id = u.id")
            .append(" WHERE u.status = 'ACTIVE'");
        
        // scope에 따라 국가 필터 추가
        if ("REGIONAL".equals(scope) && countryCode != null) {
            cteSql.append(" AND COALESCE(u.country_code, 'UNKNOWN') = #{country_code}");
        }
        
        cteSql.append(" )");

        // 외부 SELECT: 표출 조건 = 수익(채굴+레퍼럴)이 0보다 큰 경우만 랭킹 집계 (수익 0인 유저는 제외)
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectStringQuery(SQL_PERSONAL_RANKING_FROM_USER_STATS)
            .where("(mining_amount + referral_reward) > 0")
            .appendQueryString("ORDER BY aggregation DESC, total_amount DESC, team_count DESC")
            .limit(50);
        
        // WITH 절을 앞에 추가
        String query = cteSql.toString() + " " + queryBuilder.build();
        
        Map<String, Object> params = new HashMap<>();
        // start_date가 null이 아닐 때만 params에 추가
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        if ("REGIONAL".equals(scope) && countryCode != null) {
            params.put("country_code", countryCode);
        }
        
        return query(client, query, params)
            .map(rows -> {
                List<PersonalRanking> rankings = new ArrayList<>();
                for (Row row : rows) {
                    PersonalRanking ranking = PersonalRanking.builder()
                        .userId(getLongColumnValue(row, "user_id"))
                        .nickname(getStringColumnValue(row, "nickname"))
                        .level(getIntegerColumnValue(row, "level"))
                        .countryCode(getStringColumnValue(row, "country_code"))
                        .totalAmount(getBigDecimalColumnValue(row, "total_amount"))
                        .teamCount(getLongColumnValue(row, "team_count"))
                        .aggregation(getBigDecimalColumnValue(row, "total_amount"))
                        .build();
                    rankings.add(ranking);
                }
                return rankings;
            })
            .onFailure(throwable -> log.error("개인 랭킹 조회 실패 - scope: {}, period: {}", scope, period, throwable));
    }
    
    /**
     * 특정 사용자의 개인 랭킹 조회
     */
    public Future<PersonalRanking> getPersonalRankingByUserId(SqlClient client, Long userId, String scope, String period, String countryCode) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        // WITH 절은 QueryBuilder가 직접 지원하지 않으므로 문자열로 작성
        StringBuilder cteSql = new StringBuilder("WITH mining_stats AS (")
            .append(" SELECT dm.user_id, COALESCE(SUM(dm.mining_amount), 0) as mining_amount")
            .append(" FROM daily_mining dm");
        if (startDate != null) {
            cteSql.append(" WHERE dm.mining_date >= #{start_date}");
        }
        cteSql.append(" GROUP BY dm.user_id")
            .append(" ), referral_stats AS (")
            .append(" SELECT it.receiver_id as user_id, COALESCE(SUM(it.amount), 0) as referral_reward")
            .append(" FROM internal_transfers it")
            .append(" WHERE it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' AND (it.deleted_at IS NULL)");
        if (startDate != null) {
            cteSql.append(" AND it.created_at >= #{start_date}");
        }
        cteSql.append(" GROUP BY it.receiver_id")
            .append(" ), team_stats AS (")
            .append(" SELECT rr.referrer_id as user_id,")
            .append(" COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as team_count")
            .append(" FROM referral_relations rr")
            .append(" GROUP BY rr.referrer_id")
            .append(" ), user_stats AS (")
            .append(" SELECT ")
            .append(" u.id as user_id,")
            .append(" COALESCE(NULLIF(TRIM(u.nickname), ''), '') as nickname,")
            .append(" u.level,")
            .append(" COALESCE(u.country_code, 'UNKNOWN') as country_code,")
            .append(" COALESCE(ms.mining_amount, 0) as mining_amount,")
            .append(" COALESCE(rs.referral_reward, 0) as referral_reward,")
            .append(" COALESCE(ts.team_count, 0) as team_count")
            .append(" FROM users u")
            .append(" LEFT JOIN mining_stats ms ON ms.user_id = u.id")
            .append(" LEFT JOIN referral_stats rs ON rs.user_id = u.id")
            .append(" LEFT JOIN team_stats ts ON ts.user_id = u.id")
            .append(" WHERE u.id = #{user_id}")
            .append(" AND u.status = 'ACTIVE'");
        
        if ("REGIONAL".equals(scope) && countryCode != null) {
            cteSql.append(" AND COALESCE(u.country_code, 'UNKNOWN') = #{country_code}");
        }
        
        cteSql.append(" )");
        
        // 외부 SELECT는 QueryBuilder 사용
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectStringQuery(SQL_PERSONAL_RANKING_FROM_USER_STATS);

        // WITH 절을 앞에 추가
        String query = cteSql.toString() + " " + queryBuilder.build();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        // start_date가 null이 아닐 때만 params에 추가
        if (startDate != null) {
            params.put("start_date", startDate.atStartOfDay());
        }
        if ("REGIONAL".equals(scope) && countryCode != null) {
            params.put("country_code", countryCode);
        }
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    return PersonalRanking.builder()
                        .userId(getLongColumnValue(row, "user_id"))
                        .nickname(getStringColumnValue(row, "nickname"))
                        .level(getIntegerColumnValue(row, "level"))
                        .countryCode(getStringColumnValue(row, "country_code"))
                        .totalAmount(getBigDecimalColumnValue(row, "total_amount"))
                        .teamCount(getLongColumnValue(row, "team_count"))
                        .aggregation(getBigDecimalColumnValue(row, "total_amount"))
                        .build();
                }
                return null;
            })
            .onFailure(throwable -> log.error("사용자 개인 랭킹 조회 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 국가별 랭킹 데이터
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class CountryRanking {
        private String countryCode;
        private Long totalMembers;
        private BigDecimal totalMinedCoins;
        private BigDecimal aggregation;
    }
    
    /**
     * 개인 랭킹 데이터
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PersonalRanking {
        private Long userId;
        private String nickname;
        private Integer level;
        private String countryCode;
        private BigDecimal totalAmount;
        private Long teamCount;
        private BigDecimal aggregation;
    }
}
