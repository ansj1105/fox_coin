package com.foxya.coin.ranking;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.CountryCode;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.common.enums.RankingScope;
import com.foxya.coin.ranking.dto.CountryRankingResponseDto;
import com.foxya.coin.ranking.dto.RankingResponseDto;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

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
    
    /**
     * 개인 랭킹 조회
     */
    public Future<RankingResponseDto> getRankings(Long userId, String scope, String period) {
        final String finalScope = RankingScope.fromValue(scope).getValue();
        final String finalPeriod = RankingPeriod.fromValue(period).getValue();
        
        // 사용자의 국가 코드 조회 (REGIONAL일 때 필요)
        return rankingRepository.getUserCountryCode(pool, userId)
            .compose(userCountryCode -> {
                String countryCode = ("REGIONAL".equals(finalScope) && userCountryCode != null) 
                    ? userCountryCode 
                    : null;
                
                // 개인 랭킹 조회
                return rankingRepository.getPersonalRankings(pool, finalScope, finalPeriod, countryCode)
                    .compose(rankings -> {
                        // 사용자 본인의 랭킹 조회
                        return rankingRepository.getPersonalRankingByUserId(pool, userId, finalScope, finalPeriod, countryCode)
                            .map(myRanking -> {
                                // 랭킹 정보 변환
                                List<RankingResponseDto.RankingInfo> rankingInfos = new ArrayList<>();
                                int rank = 1;
                                
                                for (RankingRepository.PersonalRanking ranking : rankings) {
                                    RankingResponseDto.RankingInfo info = RankingResponseDto.RankingInfo.builder()
                                        .userId(ranking.getUserId())
                                        .rank(rank++)
                                        .nickname(ranking.getNickname())
                                        .profileImage(null) // TODO: 프로필 이미지 추가 시 수정
                                        .level(ranking.getLevel())
                                        .levelName("여우야") // TODO: 레벨 이름 매핑 추가 (사용자가 수정함)
                                        .totalAmount(ranking.getTotalAmount() != null ? ranking.getTotalAmount() : BigDecimal.ZERO)
                                        .teamCount(ranking.getTeamCount() != null ? ranking.getTeamCount() : 0L)
                                        .country(ranking.getCountryCode())
                                        .flag(getCountryFlag(ranking.getCountryCode()))
                                        .build();
                                    rankingInfos.add(info);
                                }
                                
                                // Top 3와 나머지 분리
                                final List<RankingResponseDto.RankingInfo> top3 = rankingInfos.size() >= 3 
                                    ? new ArrayList<>(rankingInfos.subList(0, 3))
                                    : new ArrayList<>(rankingInfos);
                                final List<RankingResponseDto.RankingInfo> rest = rankingInfos.size() > 3 
                                    ? new ArrayList<>(rankingInfos.subList(3, rankingInfos.size()))
                                    : new ArrayList<>();
                                
                                // 사용자 본인 랭킹 찾기
                                RankingResponseDto.RankingInfo myRank = null;
                                if (myRanking != null) {
                                    // 전체 랭킹에서 순위 계산
                                    int userRank = calculatePersonalRank(rankings, myRanking);
                                    
                                    myRank = RankingResponseDto.RankingInfo.builder()
                                        .userId(myRanking.getUserId())
                                        .rank(userRank)
                                        .nickname(myRanking.getNickname())
                                        .profileImage(null) // TODO: 프로필 이미지 추가 시 수정
                                        .level(myRanking.getLevel())
                                        .levelName("여우야") // TODO: 레벨 이름 매핑 추가
                                        .totalAmount(myRanking.getTotalAmount() != null ? myRanking.getTotalAmount() : BigDecimal.ZERO)
                                        .teamCount(myRanking.getTeamCount() != null ? myRanking.getTeamCount() : 0L)
                                        .country(myRanking.getCountryCode())
                                        .flag(getCountryFlag(myRanking.getCountryCode()))
                                        .build();
                                }
                                
                                // Aggregation 계산
                                BigDecimal totalAggregation = BigDecimal.ZERO;
                                if (myRanking != null && myRanking.getAggregation() != null) {
                                    totalAggregation = myRanking.getAggregation();
                                }
                                
                                return RankingResponseDto.builder()
                                    .top3(top3)
                                    .rankings(rest)
                                    .myRank(myRank)
                                    .totalCount(rankings.size())
                                    .aggregation(RankingResponseDto.AggregationInfo.builder()
                                        .formula("(체굴된 코인+래퍼럴 수익)+(팀원1x20코인)")
                                        .calculation(totalAggregation)
                                        .build())
                                    .build();
                            });
                    });
            });
    }
    
    private int calculatePersonalRank(List<RankingRepository.PersonalRanking> rankings, RankingRepository.PersonalRanking userRanking) {
        int rank = 1;
        for (RankingRepository.PersonalRanking ranking : rankings) {
            if (ranking.getUserId().equals(userRanking.getUserId())) {
                return rank;
            }
            rank++;
        }
        // 랭킹에 없으면 마지막 순위
        return rankings.size() + 1;
    }
}

