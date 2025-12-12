package com.foxya.coin.referral;

import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.common.enums.ReferralTeamTab;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.referral.dto.ReferralStatsDto;
import com.foxya.coin.referral.dto.TeamInfoResponseDto;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ReferralService extends BaseService {
    
    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    
    public ReferralService(PgPool pool, ReferralRepository referralRepository, UserRepository userRepository) {
        super(pool);
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * 레퍼럴 코드 등록
     */
    public Future<Void> registerReferralCode(Long userId, String referralCode) {
        // 1. 이미 레퍼럴 관계가 있는지 확인
        return referralRepository.existsReferralRelation(pool, userId)
            .compose(exists -> {
                if (exists) {
                    return Future.failedFuture(new BadRequestException("이미 레퍼럴 코드가 등록되어 있습니다."));
                }
                
                // 2. 레퍼럴 코드로 추천인 찾기
                return userRepository.getUserByReferralCode(pool, referralCode);
            })
            .compose(referrer -> {
                if (referrer == null) {
                    return Future.failedFuture(new BadRequestException("유효하지 않은 레퍼럴 코드입니다."));
                }
                
                if (referrer.getId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("자신의 레퍼럴 코드는 등록할 수 없습니다."));
                }
                
                // 3. 레퍼럴 관계 생성 (level 1 = 직접 추천)
                return referralRepository.createReferralRelation(pool, referrer.getId(), userId, 1);
            })
            .compose(relation -> {
                // 4. 추천인의 통계 업데이트
                return updateReferrerStats(relation.getReferrerId());
            })
            .mapEmpty();
    }
    
    /**
     * 레퍼럴 통계 조회
     */
    public Future<ReferralStatsDto> getReferralStats(Long userId) {
        return Future.all(
            referralRepository.getDirectReferralCount(pool, userId),
            referralRepository.getActiveTeamCount(pool, userId),
            referralRepository.getOrCreateStats(pool, userId)
        ).map(result -> {
            Integer directCount = result.resultAt(0);
            Integer activeTeamCount = result.resultAt(1);
            var stats = result.<com.foxya.coin.referral.entities.ReferralStats>resultAt(2);
            
            return ReferralStatsDto.builder()
                .userId(userId)
                .directCount(directCount)
                .activeTeamCount(activeTeamCount)
                .totalReward(stats.getTotalReward())
                .todayReward(stats.getTodayReward())
                .build();
        });
    }
    
    /**
     * 레퍼럴 관계 삭제 (Soft Delete)
     */
    public Future<Void> deleteReferralRelation(Long userId) {
        // 1. 레퍼럴 관계 조회
        return referralRepository.getReferralRelationByReferredId(pool, userId)
            .compose(relation -> {
                if (relation == null) {
                    return Future.failedFuture(new BadRequestException("등록된 레퍼럴 관계가 없습니다."));
                }
                
                Long referrerId = relation.getReferrerId();
                
                // 2. 레퍼럴 관계 삭제 (Soft Delete)
                return referralRepository.deleteReferralRelation(pool, userId)
                    .compose(v -> {
                        // 3. 추천인의 통계 업데이트
                        return updateReferrerStats(referrerId);
                    });
            });
    }
    
    /**
     * 레퍼럴 관계 완전 삭제 (Hard Delete)
     */
    public Future<Void> hardDeleteReferralRelation(Long userId) {
        // 1. 레퍼럴 관계 조회 (삭제된 것도 포함하기 위해 직접 조회)
        return referralRepository.getReferralRelationByReferredIdIncludingDeleted(pool, userId)
            .compose(relation -> {
                if (relation == null) {
                    return Future.failedFuture(new BadRequestException("레퍼럴 관계가 존재하지 않습니다."));
                }
                
                Long referrerId = relation.getReferrerId();
                
                // 2. 레퍼럴 관계 완전 삭제 (Hard Delete)
                return referralRepository.hardDeleteReferralRelation(pool, userId)
                    .compose(v -> {
                        // 3. 추천인의 통계 업데이트
                        return updateReferrerStats(referrerId);
                    });
            });
    }
    
    /**
     * 추천인의 통계 업데이트
     */
    private Future<Void> updateReferrerStats(Long referrerId) {
        return Future.all(
            referralRepository.getDirectReferralCount(pool, referrerId),
            referralRepository.getActiveTeamCount(pool, referrerId)
        ).compose(result -> {
            Integer directCount = result.resultAt(0);
            Integer activeTeamCount = result.resultAt(1);
            
            return referralRepository.updateStats(pool, referrerId, directCount, activeTeamCount);
        }).mapEmpty();
    }
    
    /**
     * 팀 정보 조회
     */
    public Future<TeamInfoResponseDto> getTeamInfo(Long referrerId, String tab, String period, Integer limit, Integer offset) {
        final String finalTab = ReferralTeamTab.fromValue(tab).getValue();
        final String finalPeriod = RankingPeriod.fromValue(period).getValue();
        
        // summary 조회
        Future<TeamInfoResponseDto.SummaryInfo> summaryFuture = referralRepository.getTeamSummary(pool, referrerId);
        
        // tab에 따라 다른 데이터 조회
        if ("MEMBERS".equals(finalTab)) {
            Future<List<TeamInfoResponseDto.MemberInfo>> membersFuture = referralRepository.getTeamMembers(pool, referrerId, finalPeriod, limit, offset);
            Future<Long> totalFuture = referralRepository.getTeamMembersCount(pool, referrerId, finalPeriod);
            
            return Future.all(summaryFuture, membersFuture, totalFuture)
                .map(results -> {
                    TeamInfoResponseDto.SummaryInfo summary = results.resultAt(0);
                    List<TeamInfoResponseDto.MemberInfo> members = results.resultAt(1);
                    Long total = results.resultAt(2);
                    
                    return TeamInfoResponseDto.builder()
                        .summary(summary)
                        .members(members != null ? members : new ArrayList<>())
                        .revenues(null)
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .build();
                });
        } else {
            // REVENUE 탭
            Future<List<TeamInfoResponseDto.RevenueInfo>> revenuesFuture = referralRepository.getTeamRevenues(pool, referrerId, finalPeriod, limit, offset);
            Future<Long> totalFuture = referralRepository.getTeamMembersCount(pool, referrerId, finalPeriod);
            
            return Future.all(summaryFuture, revenuesFuture, totalFuture)
                .map(results -> {
                    TeamInfoResponseDto.SummaryInfo summary = results.resultAt(0);
                    List<TeamInfoResponseDto.RevenueInfo> revenues = results.resultAt(1);
                    Long total = results.resultAt(2);
                    
                    return TeamInfoResponseDto.builder()
                        .summary(summary)
                        .members(null)
                        .revenues(revenues != null ? revenues : new ArrayList<>())
                        .total(total)
                        .limit(limit)
                        .offset(offset)
                        .build();
                });
        }
    }
}

