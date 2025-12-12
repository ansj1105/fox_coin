package com.foxya.coin.ranking;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.ranking.dto.CountryRankingResponseDto;
import io.vertx.core.Future;
import io.vertx.sqlclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RankingService extends BaseService {
    
    private final RankingRepository rankingRepository;
    
    // 국가 코드와 이름 매핑
    private static final Map<String, String> COUNTRY_NAMES = Map.of(
        "KR", "대한민국", "US", "미국", "JP", "일본", "CN", "중국",
        "GB", "영국", "FR", "프랑스", "DE", "독일", "IT", "이탈리아",
        "ES", "스페인", "CA", "캐나다", "AU", "호주", "BR", "브라질",
        "IN", "인도", "RU", "러시아", "MX", "멕시코", "ID", "인도네시아",
        "TH", "태국", "VN", "베트남", "PH", "필리핀", "MY", "말레이시아",
        "SG", "싱가포르", "TW", "대만", "HK", "홍콩"
    );
    
    // 국가 코드와 깃발 이모지 매핑
    private static final Map<String, String> COUNTRY_FLAGS = Map.of(
        "KR", "🇰🇷", "US", "🇺🇸", "JP", "🇯🇵", "CN", "🇨🇳",
        "GB", "🇬🇧", "FR", "🇫🇷", "DE", "🇩🇪", "IT", "🇮🇹",
        "ES", "🇪🇸", "CA", "🇨🇦", "AU", "🇦🇺", "BR", "🇧🇷",
        "IN", "🇮🇳", "RU", "🇷🇺", "MX", "🇲🇽", "ID", "🇮🇩",
        "TH", "🇹🇭", "VN", "🇻🇳", "PH", "🇵🇭", "MY", "🇲🇾",
        "SG", "🇸🇬", "TW", "🇹🇼", "HK", "🇭🇰"
    );
    
    public RankingService(PgPool pool, RankingRepository rankingRepository) {
        super(pool);
        this.rankingRepository = rankingRepository;
    }
    
    /**
     * 국가별 팀 랭킹 조회
     */
    public Future<CountryRankingResponseDto> getCountryRankings(Long userId, String period) {
        if (period == null || period.isEmpty()) {
            period = "TODAY";
        }
        
        return rankingRepository.getCountryRankings(pool, period)
            .compose(rankings -> {
                // 사용자의 국가 코드 조회
                return rankingRepository.getUserCountryCode(pool, userId)
                    .compose(userCountryCode -> {
                        // 랭킹 정보 변환
                        List<CountryRankingResponseDto.CountryRankingInfo> rankingInfos = new ArrayList<>();
                        int rank = 1;
                        
                        for (RankingRepository.CountryRanking ranking : rankings) {
                            CountryRankingResponseDto.CountryRankingInfo info = CountryRankingResponseDto.CountryRankingInfo.builder()
                                .rank(rank++)
                                .country(ranking.getCountryCode())
                                .countryName(COUNTRY_NAMES.getOrDefault(ranking.getCountryCode(), ranking.getCountryCode()))
                                .flag(COUNTRY_FLAGS.getOrDefault(ranking.getCountryCode(), "🏳️"))
                                .totalMinedCoins(ranking.getTotalMinedCoins() != null ? ranking.getTotalMinedCoins() : BigDecimal.ZERO)
                                .totalMembers(ranking.getTotalMembers() != null ? ranking.getTotalMembers() : 0L)
                                .aggregation(ranking.getAggregation() != null ? ranking.getAggregation() : BigDecimal.ZERO)
                                .build();
                            rankingInfos.add(info);
                        }
                        
                        // Top 3와 나머지 분리
                        List<CountryRankingResponseDto.CountryRankingInfo> top3 = rankingInfos.size() >= 3 
                            ? new ArrayList<>(rankingInfos.subList(0, 3))
                            : new ArrayList<>(rankingInfos);
                        List<CountryRankingResponseDto.CountryRankingInfo> rest = rankingInfos.size() > 3 
                            ? new ArrayList<>(rankingInfos.subList(3, rankingInfos.size()))
                            : new ArrayList<>();
                        
                        // 사용자 국가 정보 찾기
                        CountryRankingResponseDto.CountryRankingInfo myCountry = null;
                        if (userCountryCode != null) {
                            for (CountryRankingResponseDto.CountryRankingInfo info : rankingInfos) {
                                if (info.getCountry().equals(userCountryCode)) {
                                    myCountry = info;
                                    break;
                                }
                            }
                            
                            // 랭킹에 없으면 조회
                            if (myCountry == null) {
                                return rankingRepository.getCountryRankingByCode(pool, userCountryCode, period)
                                    .map(userRanking -> {
                                        if (userRanking != null) {
                                            // 전체 랭킹에서 순위 계산
                                            int userRank = calculateRank(rankings, userRanking);
                                            myCountry = CountryRankingResponseDto.CountryRankingInfo.builder()
                                                .rank(userRank)
                                                .country(userRanking.getCountryCode())
                                                .countryName(COUNTRY_NAMES.getOrDefault(userRanking.getCountryCode(), userRanking.getCountryCode()))
                                                .flag(COUNTRY_FLAGS.getOrDefault(userRanking.getCountryCode(), "🏳️"))
                                                .totalMinedCoins(userRanking.getTotalMinedCoins() != null ? userRanking.getTotalMinedCoins() : BigDecimal.ZERO)
                                                .totalMembers(userRanking.getTotalMembers() != null ? userRanking.getTotalMembers() : 0L)
                                                .aggregation(userRanking.getAggregation() != null ? userRanking.getAggregation() : BigDecimal.ZERO)
                                                .build();
                                        }
                                        
                                        return CountryRankingResponseDto.builder()
                                            .top3(top3)
                                            .rankings(rest)
                                            .myCountry(myCountry)
                                            .totalCount(rankingInfos.size())
                                            .build();
                                    })
                                    .otherwise(throwable -> {
                                        log.warn("사용자 국가 랭킹 조회 실패: {}", throwable.getMessage());
                                        return CountryRankingResponseDto.builder()
                                            .top3(top3)
                                            .rankings(rest)
                                            .myCountry(null)
                                            .totalCount(rankingInfos.size())
                                            .build();
                                    });
                            }
                        }
                        
                        return Future.succeededFuture(CountryRankingResponseDto.builder()
                            .top3(top3)
                            .rankings(rest)
                            .myCountry(myCountry)
                            .totalCount(rankingInfos.size())
                            .build());
                    });
            });
    }
    
    private int calculateRank(List<RankingRepository.CountryRanking> rankings, RankingRepository.CountryRanking userRanking) {
        int rank = 1;
        for (RankingRepository.CountryRanking ranking : rankings) {
            if (ranking.getCountryCode().equals(userRanking.getCountryCode())) {
                return rank;
            }
            rank++;
        }
        // 랭킹에 없으면 마지막 순위
        return rankings.size() + 1;
    }
}

