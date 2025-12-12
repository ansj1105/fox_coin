package com.foxya.coin.ranking;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.PgPool;
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
    public Future<List<CountryRanking>> getCountryRankings(PgPool pool, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            WITH country_stats AS (
                SELECT 
                    COALESCE(u.country_code, 'UNKNOWN') as country_code,
                    COUNT(DISTINCT u.id) as total_members,
                    COALESCE(SUM(dm.mining_amount), 0) as total_mined_coins
                FROM users u
                LEFT JOIN referral_relations rr ON rr.referred_user_id = u.id
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
        
        return query(pool, query, params)
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
    public Future<CountryRanking> getCountryRankingByCode(PgPool pool, String countryCode, String period) {
        LocalDate startDate = getStartDateForPeriod(period);
        
        String sql = """
            WITH country_stats AS (
                SELECT 
                    COALESCE(u.country_code, 'UNKNOWN') as country_code,
                    COUNT(DISTINCT u.id) as total_members,
                    COALESCE(SUM(dm.mining_amount), 0) as total_mined_coins
                FROM users u
                LEFT JOIN referral_relations rr ON rr.referred_user_id = u.id
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
        
        return query(pool, query, params)
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
    public Future<String> getUserCountryCode(PgPool pool, Long userId) {
        String sql = QueryBuilder
            .select("users", "country_code")
            .whereById()
            .build();
        
        return query(pool, sql, Collections.singletonMap("id", userId))
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
}

