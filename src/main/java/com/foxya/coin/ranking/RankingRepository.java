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
     * 국가별 팀 랭킹 조회
     * @param period 기간 (ALL, TODAY, WEEK, MONTH, YEAR)
     */
    public Future<List<CountryRanking>> getCountryRankings(SqlClient client, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            WITH country_stats AS (
                SELECT 
                    COALESCE(u.country_code, 'UNKNOWN') as country_code,
                    COUNT(DISTINCT u.id) as total_members,
                    COALESCE(SUM(dm.mining_amount), 0) as total_mined_coins
                FROM users u
                LEFT JOIN referral_relations rr ON rr.referred_id = u.id
                LEFT JOIN daily_mining dm ON dm.user_id = u.id
                    AND (dm.mining_date >= #{start_date} OR #{start_date} IS NULL)
                WHERE u.status = 'ACTIVE'
                    AND (rr.id IS NOT NULL OR u.referral_code IS NOT NULL)
                GROUP BY u.country_code
            )
            SELECT 
                country_code,
                total_members,
                total_mined_coins,
                (total_mined_coins + (total_members * 20)) as aggregation
            FROM country_stats
            ORDER BY aggregation DESC, total_mined_coins DESC
            LIMIT 50
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("start_date", startDate);
        
        return query(client, query, params)
            .map(rows -> {
                List<CountryRanking> rankings = new ArrayList<>();
                for (Row row : rows) {
                    CountryRanking ranking = CountryRanking.builder()
                        .countryCode(getStringColumnValue(row, "country_code"))
                        .totalMembers(getLongColumnValue(row, "total_members"))
                        .totalMinedCoins(getBigDecimalColumnValue(row, "total_mined_coins"))
                        .aggregation(getBigDecimalColumnValue(row, "aggregation"))
                        .build();
                    rankings.add(ranking);
                }
                return rankings;
            })
            .onFailure(throwable -> log.error("국가별 랭킹 조회 실패: {}", throwable.getMessage()));
    }
    
    /**
     * 특정 국가의 랭킹 정보 조회
     */
    public Future<CountryRanking> getCountryRankingByCode(SqlClient client, String countryCode, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            WITH country_stats AS (
                SELECT 
                    COALESCE(u.country_code, 'UNKNOWN') as country_code,
                    COUNT(DISTINCT u.id) as total_members,
                    COALESCE(SUM(dm.mining_amount), 0) as total_mined_coins
                FROM users u
                LEFT JOIN referral_relations rr ON rr.referred_id = u.id
                LEFT JOIN daily_mining dm ON dm.user_id = u.id
                    AND (dm.mining_date >= #{start_date} OR #{start_date} IS NULL)
                WHERE u.status = 'ACTIVE'
                    AND (rr.id IS NOT NULL OR u.referral_code IS NOT NULL)
                    AND COALESCE(u.country_code, 'UNKNOWN') = #{country_code}
                GROUP BY u.country_code
            )
            SELECT 
                country_code,
                total_members,
                total_mined_coins,
                (total_mined_coins + (total_members * 20)) as aggregation
            FROM country_stats
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("country_code", countryCode);
        params.put("start_date", startDate);
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    return CountryRanking.builder()
                        .countryCode(getStringColumnValue(row, "country_code"))
                        .totalMembers(getLongColumnValue(row, "total_members"))
                        .totalMinedCoins(getBigDecimalColumnValue(row, "total_mined_coins"))
                        .aggregation(getBigDecimalColumnValue(row, "aggregation"))
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
        StringBuilder cteSql = new StringBuilder("WITH user_stats AS (")
            .append(" SELECT ")
            .append(" u.id as user_id,")
            .append(" u.login_id as nickname,")
            .append(" u.level,")
            .append(" COALESCE(u.country_code, 'UNKNOWN') as country_code,")
            .append(" COALESCE(SUM(dm.mining_amount), 0) as mining_amount,");
        
        // referral_reward 계산 (start_date가 null이 아닐 때만 날짜 조건 추가)
        if (startDate != null) {
            cteSql.append(" COALESCE(SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' ")
                .append(" AND it.created_at >= #{start_date} ")
                .append(" THEN it.amount ELSE 0 END), 0) as referral_reward,");
        } else {
            cteSql.append(" COALESCE(SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' ")
                .append(" THEN it.amount ELSE 0 END), 0) as referral_reward,");
        }
        
        cteSql.append(" COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as team_count")
            .append(" FROM users u")
            .append(" LEFT JOIN daily_mining dm ON dm.user_id = u.id");
        
        // daily_mining 날짜 조건 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            cteSql.append(" AND dm.mining_date >= #{start_date}");
        }
        
        cteSql.append(" LEFT JOIN internal_transfers it ON it.receiver_id = u.id")
            .append(" AND it.transfer_type = 'REFERRAL_REWARD'");
        
        // internal_transfers 날짜 조건 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            cteSql.append(" AND it.created_at >= #{start_date}");
        }
        
        cteSql.append(" LEFT JOIN referral_relations rr ON rr.referrer_id = u.id")
            .append(" WHERE u.status = 'ACTIVE'");
        
        // scope에 따라 국가 필터 추가
        if ("REGIONAL".equals(scope) && countryCode != null) {
            cteSql.append(" AND COALESCE(u.country_code, 'UNKNOWN') = #{country_code}");
        }
        
        cteSql.append(" GROUP BY u.id, u.login_id, u.level, u.country_code")
            .append(" )");
        
        // 외부 SELECT는 QueryBuilder 사용 (CTE를 FROM 절에서 사용)
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectStringQuery("SELECT user_id, nickname, level, country_code, (mining_amount + referral_reward) as total_amount, team_count, ((mining_amount + referral_reward) + (team_count * 20)) as aggregation FROM user_stats")
            .where("(mining_amount + referral_reward) > 0 OR team_count > 0")
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
                        .aggregation(getBigDecimalColumnValue(row, "aggregation"))
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
        StringBuilder cteSql = new StringBuilder("WITH user_stats AS (")
            .append(" SELECT ")
            .append(" u.id as user_id,")
            .append(" u.login_id as nickname,")
            .append(" u.level,")
            .append(" COALESCE(u.country_code, 'UNKNOWN') as country_code,")
            .append(" COALESCE(SUM(dm.mining_amount), 0) as mining_amount,");
        
        // referral_reward 계산 (start_date가 null이 아닐 때만 날짜 조건 추가)
        if (startDate != null) {
            cteSql.append(" COALESCE(SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' ")
                .append(" AND it.created_at >= #{start_date} ")
                .append(" THEN it.amount ELSE 0 END), 0) as referral_reward,");
        } else {
            cteSql.append(" COALESCE(SUM(CASE WHEN it.transfer_type = 'REFERRAL_REWARD' AND it.status = 'COMPLETED' ")
                .append(" THEN it.amount ELSE 0 END), 0) as referral_reward,");
        }
        
        cteSql.append(" COUNT(DISTINCT CASE WHEN rr.status = 'ACTIVE' AND rr.deleted_at IS NULL THEN rr.referred_id END) as team_count")
            .append(" FROM users u")
            .append(" LEFT JOIN daily_mining dm ON dm.user_id = u.id");
        
        // daily_mining 날짜 조건 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            cteSql.append(" AND dm.mining_date >= #{start_date}");
        }
        
        cteSql.append(" LEFT JOIN internal_transfers it ON it.receiver_id = u.id")
            .append(" AND it.transfer_type = 'REFERRAL_REWARD'");
        
        // internal_transfers 날짜 조건 (start_date가 null이 아닐 때만 추가)
        if (startDate != null) {
            cteSql.append(" AND it.created_at >= #{start_date}");
        }
        
        cteSql.append(" LEFT JOIN referral_relations rr ON rr.referrer_id = u.id")
            .append(" WHERE u.id = #{user_id}")
            .append(" AND u.status = 'ACTIVE'");
        
        if ("REGIONAL".equals(scope) && countryCode != null) {
            cteSql.append(" AND COALESCE(u.country_code, 'UNKNOWN') = #{country_code}");
        }
        
        cteSql.append(" GROUP BY u.id, u.login_id, u.level, u.country_code")
            .append(" )");
        
        // 외부 SELECT는 QueryBuilder 사용
        QueryBuilder.SelectQueryBuilder queryBuilder = QueryBuilder
            .selectStringQuery("SELECT user_id, nickname, level, country_code, (mining_amount + referral_reward) as total_amount, team_count, ((mining_amount + referral_reward) + (team_count * 20)) as aggregation FROM user_stats");
        
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
                        .aggregation(getBigDecimalColumnValue(row, "aggregation"))
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

