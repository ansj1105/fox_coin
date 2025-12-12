package com.foxya.coin.ranking;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.ranking.dto.CountryRankingResponseDto;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import com.foxya.coin.common.enums.CountryCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class RankingService extends BaseService {
    
    private final RankingRepository rankingRepository;
    
    public RankingService(PgPool pool, RankingRepository rankingRepository) {
        super(pool);
        this.rankingRepository = rankingRepository;
    }
    
    /**
     * 국가별 팀 랭킹 조회
     */
    public Future<CountryRankingResponseDto> getCountryRankings(Long userId, String period) {
        final String finalPeriod = (period == null || period.isEmpty()) ? "TODAY" : period;
        
        return rankingRepository.getCountryRankings(pool, finalPeriod)
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
                                .countryName(getCountryName(ranking.getCountryCode()))
                                .flag(getCountryFlag(ranking.getCountryCode()))
                                .totalMinedCoins(ranking.getTotalMinedCoins() != null ? ranking.getTotalMinedCoins() : BigDecimal.ZERO)
                                .totalMembers(ranking.getTotalMembers() != null ? ranking.getTotalMembers() : 0L)
                                .aggregation(ranking.getAggregation() != null ? ranking.getAggregation() : BigDecimal.ZERO)
                                .build();
                            rankingInfos.add(info);
                        }
                        
                        // Top 3와 나머지 분리
                        final List<CountryRankingResponseDto.CountryRankingInfo> top3 = rankingInfos.size() >= 3 
                            ? new ArrayList<>(rankingInfos.subList(0, 3))
                            : new ArrayList<>(rankingInfos);
                        final List<CountryRankingResponseDto.CountryRankingInfo> rest = rankingInfos.size() > 3 
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
                                final String finalUserCountryCode = userCountryCode;
                                return rankingRepository.getCountryRankingByCode(pool, finalUserCountryCode, finalPeriod)
                                    .map(userRanking -> {
                                        CountryRankingResponseDto.CountryRankingInfo finalMyCountry = null;
                                        if (userRanking != null) {
                                            // 전체 랭킹에서 순위 계산
                                            int userRank = calculateRank(rankings, userRanking);
                                            finalMyCountry = CountryRankingResponseDto.CountryRankingInfo.builder()
                                                .rank(userRank)
                                                .country(userRanking.getCountryCode())
                                                .countryName(getCountryName(userRanking.getCountryCode()))
                                                .flag(getCountryFlag(userRanking.getCountryCode()))
                                                .totalMinedCoins(userRanking.getTotalMinedCoins() != null ? userRanking.getTotalMinedCoins() : BigDecimal.ZERO)
                                                .totalMembers(userRanking.getTotalMembers() != null ? userRanking.getTotalMembers() : 0L)
                                                .aggregation(userRanking.getAggregation() != null ? userRanking.getAggregation() : BigDecimal.ZERO)
                                                .build();
                                        }
                                        
                                        return CountryRankingResponseDto.builder()
                                            .top3(top3)
                                            .rankings(rest)
                                            .myCountry(finalMyCountry)
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
                        
                        final CountryRankingResponseDto.CountryRankingInfo finalMyCountry = myCountry;
                        return Future.succeededFuture(CountryRankingResponseDto.builder()
                            .top3(top3)
                            .rankings(rest)
                            .myCountry(finalMyCountry)
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
    
    private String getCountryName(String countryCode) {
        CountryCode code = CountryCode.fromCode(countryCode);
        return code.getName();
    }
    
    private String getCountryFlag(String countryCode) {
        CountryCode code = CountryCode.fromCode(countryCode);
        return code.getFlag();
    }
}

