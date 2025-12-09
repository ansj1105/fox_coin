package com.foxya.coin.bonus;

import com.foxya.coin.bonus.dto.BonusEfficiencyResponseDto;
import com.foxya.coin.bonus.entities.UserBonus;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.referral.ReferralRepository;
import com.foxya.coin.subscription.SubscriptionRepository;
import com.foxya.coin.review.ReviewRepository;
import com.foxya.coin.agency.AgencyRepository;
import com.foxya.coin.auth.SocialLinkRepository;
import com.foxya.coin.auth.PhoneVerificationRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BonusService extends BaseService {
    
    private final BonusRepository bonusRepository;
    private final ReferralRepository referralRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ReviewRepository reviewRepository;
    private final AgencyRepository agencyRepository;
    private final SocialLinkRepository socialLinkRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    
    // 보너스 타입별 효율 정의
    private static final int EFFICIENCY_SOCIAL_LINK = 10;
    private static final int EFFICIENCY_PHONE_VERIFICATION = 10;
    private static final int EFFICIENCY_AD_WATCH = 10;
    private static final int EFFICIENCY_REFERRAL = 25;
    private static final int EFFICIENCY_PREMIUM_SUBSCRIPTION = 40;
    private static final int EFFICIENCY_REVIEW = 10;
    private static final int EFFICIENCY_AGENCY = 80;
    private static final int EFFICIENCY_REFERRAL_CODE_INPUT = 15;
    
    public BonusService(PgPool pool, BonusRepository bonusRepository,
                       ReferralRepository referralRepository,
                       SubscriptionRepository subscriptionRepository,
                       ReviewRepository reviewRepository,
                       AgencyRepository agencyRepository,
                       SocialLinkRepository socialLinkRepository,
                       PhoneVerificationRepository phoneVerificationRepository) {
        super(pool);
        this.bonusRepository = bonusRepository;
        this.referralRepository = referralRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.reviewRepository = reviewRepository;
        this.agencyRepository = agencyRepository;
        this.socialLinkRepository = socialLinkRepository;
        this.phoneVerificationRepository = phoneVerificationRepository;
    }
    
    public Future<BonusEfficiencyResponseDto> getBonusEfficiency(Long userId) {
        return Future.all(
            getSocialLinkBonus(userId),
            getPhoneVerificationBonus(userId),
            getAdWatchBonus(userId),
            getReferralBonus(userId),
            getPremiumSubscriptionBonus(userId),
            getReviewBonus(userId),
            getAgencyBonus(userId),
            getReferralCodeInputBonus(userId)
        ).map(results -> {
            List<BonusEfficiencyResponseDto.BonusInfo> bonuses = new ArrayList<>();
            int totalEfficiency = 0;
            
            for (Object result : results.list()) {
                if (result != null) {
                    BonusEfficiencyResponseDto.BonusInfo bonus = (BonusEfficiencyResponseDto.BonusInfo) result;
                    bonuses.add(bonus);
                    if (bonus.getIsActive()) {
                        totalEfficiency += bonus.getEfficiency();
                    }
                }
            }
            
            return BonusEfficiencyResponseDto.builder()
                .totalEfficiency(totalEfficiency)
                .bonuses(bonuses)
                .build();
        });
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getSocialLinkBonus(Long userId) {
        return socialLinkRepository.hasSocialLink(pool, userId)
            .map(hasLink -> BonusEfficiencyResponseDto.BonusInfo.builder()
                .type("SOCIAL_LINK")
                .name("카카오, 구글, 이메일 연동")
                .efficiency(EFFICIENCY_SOCIAL_LINK)
                .isActive(hasLink)
                .isPermanent(true)
                .build());
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getPhoneVerificationBonus(Long userId) {
        return phoneVerificationRepository.isVerified(pool, userId)
            .map(isVerified -> BonusEfficiencyResponseDto.BonusInfo.builder()
                .type("PHONE_VERIFICATION")
                .name("본인인증(휴대폰)")
                .efficiency(EFFICIENCY_PHONE_VERIFICATION)
                .isActive(isVerified)
                .isPermanent(true)
                .build());
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getAdWatchBonus(Long userId) {
        return bonusRepository.getUserBonus(pool, userId, "AD_WATCH")
            .map(bonus -> {
                if (bonus == null || !bonus.getIsActive()) {
                    return BonusEfficiencyResponseDto.BonusInfo.builder()
                        .type("AD_WATCH")
                        .name("광고 시청 보너스")
                        .efficiency(EFFICIENCY_AD_WATCH)
                        .isActive(false)
                        .isPermanent(false)
                        .build();
                }
                
                boolean isExpired = bonus.getExpiresAt() != null && bonus.getExpiresAt().isBefore(LocalDateTime.now());
                boolean isActive = bonus.getIsActive() && !isExpired;
                
                return BonusEfficiencyResponseDto.BonusInfo.builder()
                    .type("AD_WATCH")
                    .name("광고 시청 보너스")
                    .efficiency(EFFICIENCY_AD_WATCH)
                    .isActive(isActive)
                    .isPermanent(false)
                    .expiresAt(bonus.getExpiresAt())
                    .currentCount(bonus.getCurrentCount())
                    .maxCount(bonus.getMaxCount())
                    .build();
            });
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getReferralBonus(Long userId) {
        return referralRepository.getDirectReferralCount(pool, userId)
            .map(count -> {
                int maxCount = 5;
                boolean isActive = count > 0 && count <= maxCount;
                
                return BonusEfficiencyResponseDto.BonusInfo.builder()
                    .type("REFERRAL")
                    .name("친구 초대 보너스")
                    .efficiency(EFFICIENCY_REFERRAL)
                    .isActive(isActive)
                    .isPermanent(true)
                    .currentCount(count)
                    .maxCount(maxCount)
                    .build();
            });
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getPremiumSubscriptionBonus(Long userId) {
        return subscriptionRepository.getActiveSubscription(pool, userId)
            .map(subscription -> {
                if (subscription == null || !subscription.getIsActive()) {
                    return BonusEfficiencyResponseDto.BonusInfo.builder()
                        .type("PREMIUM_SUBSCRIPTION")
                        .name("프리미엄 패키지 구독")
                        .efficiency(EFFICIENCY_PREMIUM_SUBSCRIPTION)
                        .isActive(false)
                        .isPermanent(false)
                        .expiresAt(null)
                        .build();
                }
                
                boolean isExpired = subscription.getExpiresAt() != null && 
                                  subscription.getExpiresAt().isBefore(LocalDateTime.now());
                boolean isActive = subscription.getIsActive() && !isExpired;
                
                return BonusEfficiencyResponseDto.BonusInfo.builder()
                    .type("PREMIUM_SUBSCRIPTION")
                    .name("프리미엄 패키지 구독")
                    .efficiency(EFFICIENCY_PREMIUM_SUBSCRIPTION)
                    .isActive(isActive)
                    .isPermanent(false)
                    .expiresAt(subscription.getExpiresAt())
                    .build();
            });
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getReviewBonus(Long userId) {
        return reviewRepository.hasReview(pool, userId)
            .map(hasReview -> BonusEfficiencyResponseDto.BonusInfo.builder()
                .type("REVIEW")
                .name("리뷰 작성")
                .efficiency(EFFICIENCY_REVIEW)
                .isActive(hasReview)
                .isPermanent(true)
                .build());
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getAgencyBonus(Long userId) {
        return agencyRepository.hasAgencyMembership(pool, userId)
            .map(hasAgency -> BonusEfficiencyResponseDto.BonusInfo.builder()
                .type("AGENCY")
                .name("에이전시 가입")
                .efficiency(EFFICIENCY_AGENCY)
                .isActive(hasAgency)
                .isPermanent(false)
                .expiresAt(null)
                .build());
    }
    
    private Future<BonusEfficiencyResponseDto.BonusInfo> getReferralCodeInputBonus(Long userId) {
        return referralRepository.hasReferralRelation(pool, userId)
            .map(hasRelation -> BonusEfficiencyResponseDto.BonusInfo.builder()
                .type("REFERRAL_CODE_INPUT")
                .name("추천인 코드 입력")
                .efficiency(EFFICIENCY_REFERRAL_CODE_INPUT)
                .isActive(hasRelation)
                .isPermanent(true)
                .build());
    }
}

