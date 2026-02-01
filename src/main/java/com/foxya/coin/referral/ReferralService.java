package com.foxya.coin.referral;

import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.common.enums.ReferralTeamTab;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.referral.dto.CurrentReferralCodeDto;
import com.foxya.coin.referral.dto.InviteTierItemDto;
import com.foxya.coin.referral.dto.InviteTiersResponseDto;
import com.foxya.coin.referral.dto.ReferralStatsDto;
import com.foxya.coin.referral.dto.TeamInfoResponseDto;
import com.foxya.coin.transfer.TransferService;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ReferralService extends BaseService {
    
    /** 초대 인원별 채굴 보너스 배율 (영상 1회당): 1명 +3%, 5명 +6%, 10명 +10%, 20명 +14%, 50명 +18%, 100명 +22% */
    private static final double[] INVITE_BONUS_MULTIPLIERS = { 1.03, 1.06, 1.10, 1.14, 1.18, 1.22 };
    private static final int[] INVITE_TIER_THRESHOLDS = { 1, 5, 10, 20, 50, 100 };
    
    /** 하부 유저 수 구간별 래퍼럴 수익률: 1~4명 3%, 5명 5%, 10명 7%, 20명 9%, 50명 11%, 100명 13% */
    private static final double[] REFERRAL_REVENUE_RATES = { 0.03, 0.05, 0.07, 0.09, 0.11, 0.13 };
    private static final int[] REVENUE_TIER_THRESHOLDS = { 4, 5, 10, 20, 50, 100 };
    
    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final TransferService transferService;
    
    public ReferralService(PgPool pool, ReferralRepository referralRepository, UserRepository userRepository,
                           EmailVerificationRepository emailVerificationRepository, TransferService transferService) {
        super(pool);
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.transferService = transferService;
    }
    
    /**
     * 레퍼럴 코드 등록 (안전장치: 이메일 인증 필수, 동일 IP/기기 중복 초대 무효, 성공 시 추천인 EXP +1)
     */
    public Future<Void> registerReferralCode(Long userId, String referralCode, String clientIp, String deviceId) {
        // 1. 이미 레퍼럴 관계가 있는지 확인
        return referralRepository.existsReferralRelation(pool, userId)
            .compose(exists -> {
                if (exists) {
                    return Future.failedFuture(new BadRequestException("이미 레퍼럴 코드가 등록되어 있습니다."));
                }
                return userRepository.getUserByReferralCode(pool, referralCode);
            })
            .compose(referrer -> {
                if (referrer == null) {
                    return Future.failedFuture(new BadRequestException("유효하지 않은 레퍼럴 코드입니다."));
                }
                if (referrer.getId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("자신의 레퍼럴 코드는 등록할 수 없습니다."));
                }
                // 2. 하루 유저: 이메일 인증 필수
                return emailVerificationRepository.getLatestByUserId(pool, userId)
                    .compose(ev -> {
                        if (ev == null || !Boolean.TRUE.equals(ev.isVerified)) {
                            return Future.failedFuture(new BadRequestException("이메일 인증을 완료한 후 레퍼럴 코드를 등록할 수 있습니다."));
                        }
                        return referralRepository.hasReferrerAnyReferredWithSameIpOrDevice(pool, referrer.getId(), clientIp, deviceId);
                    })
                    .compose(duplicate -> {
                        if (Boolean.TRUE.equals(duplicate)) {
                            return Future.failedFuture(new BadRequestException("동일 IP 또는 기기로 이미 초대된 계정이 있어 이 초대는 무효 처리됩니다."));
                        }
                        return referralRepository.createReferralRelation(pool, referrer.getId(), userId, 1);
                    });
            })
            .compose(relation -> {
                // 3. 추천인 EXP +1 (초대 1명당 1 EXP)
                return userRepository.addExp(pool, relation.getReferrerId(), BigDecimal.ONE)
                    .compose(u -> updateReferrerStats(relation.getReferrerId()));
            })
            .mapEmpty();
    }
    
    /**
     * 유효 직접 초대 수 (이메일 인증+채굴 기록 있는 referred 수). 채굴 보너스 %·수익률 구간용.
     */
    public Future<Integer> getValidDirectReferralCount(Long referrerUserId) {
        return referralRepository.getValidDirectReferralCount(pool, referrerUserId);
    }

    /**
     * 친구 초대 → 채굴 속도 보너스 티어 목록 + 현재 유저의 유효 직접 초대 수.
     * GET /api/v1/referrals/invite-tiers 응답용.
     */
    public Future<InviteTiersResponseDto> getInviteTiers(Long referrerUserId) {
        List<InviteTierItemDto> tiers = new ArrayList<>();
        for (int i = 0; i < INVITE_TIER_THRESHOLDS.length; i++) {
            int bonusPercent = (int) Math.round((INVITE_BONUS_MULTIPLIERS[i] - 1.0) * 100);
            tiers.add(InviteTierItemDto.builder()
                .inviteCount(INVITE_TIER_THRESHOLDS[i])
                .bonusPercent(bonusPercent)
                .build());
        }
        return getValidDirectReferralCount(referrerUserId)
            .map(count -> InviteTiersResponseDto.builder()
                .tiers(tiers)
                .validDirectReferralCount(count != null ? count : 0)
                .build());
    }
    
    /**
     * 초대 인원 수에 따른 채굴 보너스 배율 (1.03 ~ 1.22). 영상 1회당 채굴량에 곱할 값.
     */
    public Future<BigDecimal> getInviteMiningBonusMultiplier(Long referrerUserId) {
        return referralRepository.getValidDirectReferralCount(pool, referrerUserId)
            .map(count -> {
                for (int i = INVITE_TIER_THRESHOLDS.length - 1; i >= 0; i--) {
                    if (count >= INVITE_TIER_THRESHOLDS[i]) {
                        return BigDecimal.valueOf(INVITE_BONUS_MULTIPLIERS[i]);
                    }
                }
                return BigDecimal.ONE;
            });
    }
    
    /**
     * 하부 유저 수 구간별 래퍼럴 수익률 (0.03 ~ 0.13)
     */
    public static BigDecimal getReferralRevenueRateByTeamCount(int validTeamCount) {
        for (int i = REVENUE_TIER_THRESHOLDS.length - 1; i >= 0; i--) {
            if (validTeamCount >= REVENUE_TIER_THRESHOLDS[i]) {
                return BigDecimal.valueOf(REFERRAL_REVENUE_RATES[i]);
            }
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * 하부 유저가 채굴 완료 시 추천인에게 래퍼럴 수익 지급 (하부 채굴 KORI × 수익률)
     */
    public Future<Void> grantReferralRewardForMining(Long referredUserId, BigDecimal minedAmountKori) {
        if (minedAmountKori == null || minedAmountKori.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.succeededFuture();
        }
        return referralRepository.getReferralRelationByReferredId(pool, referredUserId)
            .compose(relation -> {
                if (relation == null) {
                    return Future.succeededFuture();
                }
                Long referrerId = relation.getReferrerId();
                return referralRepository.getValidDirectReferralCount(pool, referrerId)
                    .map(count -> getReferralRevenueRateByTeamCount(count))
                    .compose(rate -> {
                        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                            return Future.succeededFuture();
                        }
                        BigDecimal rewardAmount = minedAmountKori.multiply(rate).setScale(18, RoundingMode.DOWN);
                        if (rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
                            return Future.succeededFuture();
                        }
                        return transferService.createReferralRewardTransfer(referrerId, rewardAmount, "REFERRAL_REWARD")
                            .compose(created -> referralRepository.getOrCreateStats(pool, referrerId)
                                .compose(stats -> referralRepository.incrementReward(pool, referrerId, rewardAmount)))
                            .mapEmpty();
                    });
            });
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
     * 현재 로그인한 사용자를 추천한 사람의 추천인 코드 조회
     */
    public Future<CurrentReferralCodeDto> getCurrentReferralCode(Long userId) {
        // 1. 레퍼럴 관계 조회 (삭제되지 않은 것만)
        return referralRepository.getReferralRelationByReferredId(pool, userId)
            .compose(relation -> {
                if (relation == null) {
                    // 추천인이 없는 경우 referralCode = null
                    return Future.succeededFuture(CurrentReferralCodeDto.builder()
                        .referralCode(null)
                        .build());
                }
                
                Long referrerId = relation.getReferrerId();
                
                // 2. 추천인의 사용자 정보 조회 후 추천인 코드 반환
                return userRepository.getUserById(pool, referrerId)
                    .map(referrer -> CurrentReferralCodeDto.builder()
                        .referralCode(referrer != null ? referrer.getReferralCode() : null)
                        .build());
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
        Future<TeamInfoResponseDto.SummaryInfo> summaryFuture = referralRepository.getTeamSummary(pool, referrerId)
            .map(summary -> {
                // 선택된 period에 따른 수익 설정
                BigDecimal periodRevenue = switch (finalPeriod) {
                    case "ALL" -> summary.getTotalRevenue();
                    case "TODAY" -> summary.getTodayRevenue();
                    case "WEEK" -> summary.getWeekRevenue();
                    case "MONTH" -> summary.getMonthRevenue();
                    case "YEAR" -> summary.getYearRevenue();
                    default -> summary.getTodayRevenue();
                };
                
                // periodRevenue 설정
                summary.setPeriodRevenue(periodRevenue);
                return summary;
            });
        
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

