package com.foxya.coin.subscription;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.subscription.dto.SubscriptionPlanResponseDto;
import com.foxya.coin.subscription.dto.SubscriptionStatusResponseDto;
import com.foxya.coin.subscription.entities.Subscription;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SubscriptionService extends BaseService {

    public static final String PACKAGE_TYPE_VIP = "VIP";
    public static final String PLAN_VIP_PASS_7D = "VIP_PASS_7D";
    public static final String PLAN_VIP_1M = "VIP_1M";
    public static final String PLAN_VIP_3M = "VIP_3M";
    public static final String PLAN_VIP_6M = "VIP_6M";
    public static final String PLAN_VIP_12M = "VIP_12M";

    private static final int BONUS_FREE = 0;
    private static final int BONUS_VIP_PASS_7D = 5;
    private static final int BONUS_VIP_DEFAULT = 45;

    private static final Map<String, PlanDef> PLAN_DEFS = new LinkedHashMap<>();

    static {
        addPlan(new PlanDef(
            PLAN_VIP_PASS_7D,
            PACKAGE_TYPE_VIP,
            "7일 VIP 패스",
            7,
            5500,
            "4.99",
            BONUS_VIP_PASS_7D,
            true,
            true,
            true,
            true,
            true
        ));
        addPlan(new PlanDef(
            PLAN_VIP_1M,
            PACKAGE_TYPE_VIP,
            "1개월 VIP 구독",
            30,
            11000,
            "9.99",
            BONUS_VIP_DEFAULT,
            true,
            true,
            true,
            true,
            true
        ));
        addPlan(new PlanDef(
            PLAN_VIP_3M,
            PACKAGE_TYPE_VIP,
            "3개월 VIP 구독",
            90,
            33000,
            "29.99",
            BONUS_VIP_DEFAULT,
            true,
            true,
            true,
            true,
            true
        ));
        addPlan(new PlanDef(
            PLAN_VIP_6M,
            PACKAGE_TYPE_VIP,
            "6개월 VIP 구독",
            180,
            66000,
            "59.99",
            BONUS_VIP_DEFAULT,
            true,
            true,
            true,
            true,
            true
        ));
        addPlan(new PlanDef(
            PLAN_VIP_12M,
            PACKAGE_TYPE_VIP,
            "12개월 VIP 구독",
            365,
            110000,
            "99.99",
            BONUS_VIP_DEFAULT,
            true,
            true,
            true,
            true,
            true
        ));
    }

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(PgPool pool, SubscriptionRepository subscriptionRepository) {
        super(pool);
        this.subscriptionRepository = subscriptionRepository;
    }

    public Future<SubscriptionStatusResponseDto> getSubscriptionStatus(Long userId) {
        return subscriptionRepository.getActiveSubscription(pool, userId)
            .map(subscription -> {
                if (subscription == null || !Boolean.TRUE.equals(subscription.getIsActive()) || isExpired(subscription)) {
                    return emptyStatus();
                }

                PlanDef planDef = resolvePlanDef(subscription);
                return SubscriptionStatusResponseDto.builder()
                    .isSubscribed(true)
                    .expiresAt(subscription.getExpiresAt())
                    .packageType(planDef.packageType)
                    .planCode(planDef.planCode)
                    .miningEfficiencyBonusPercent(planDef.miningEfficiencyBonusPercent)
                    .adFree(planDef.adFree)
                    .autoBoostMining(planDef.autoBoostMining)
                    .referralReregisterUnlimited(planDef.referralReregisterUnlimited)
                    .fullMiningHistoryAccess(planDef.fullMiningHistoryAccess)
                    .profileImageUnlock(planDef.profileImageUnlock)
                    .build();
            });
    }

    public Future<Subscription> subscribe(Long userId, String packageType, Integer months) {
        return subscribe(userId, null, packageType, months, null);
    }

    public Future<Subscription> subscribe(Long userId, String planCode, String packageType, Integer months, Integer days) {
        PlanDef explicitPlan = planCode != null ? PLAN_DEFS.get(planCode) : null;
        PlanDef inferredPlan = explicitPlan != null ? explicitPlan : inferPlan(packageType, months, days);

        String storedType;
        LocalDateTime expiresAt;

        if (inferredPlan != null) {
            storedType = inferredPlan.planCode;
            expiresAt = LocalDateTime.now().plusDays(inferredPlan.durationDays);
        } else {
            storedType = (packageType != null && !packageType.isBlank()) ? packageType : PACKAGE_TYPE_VIP;
            if (days != null && days > 0) {
                expiresAt = LocalDateTime.now().plusDays(days);
            } else if (months != null && months > 0) {
                expiresAt = LocalDateTime.now().plusMonths(months);
            } else {
                expiresAt = null;
            }
        }

        return subscriptionRepository.createSubscription(pool, userId, storedType, expiresAt);
    }

    public Future<SubscriptionPlanResponseDto> getPlans() {
        List<SubscriptionPlanResponseDto.PlanItem> plans = new ArrayList<>();
        for (PlanDef planDef : PLAN_DEFS.values()) {
            plans.add(SubscriptionPlanResponseDto.PlanItem.builder()
                .planCode(planDef.planCode)
                .packageType(planDef.packageType)
                .displayName(planDef.displayName)
                .durationDays(planDef.durationDays)
                .priceKrw(planDef.priceKrw)
                .priceUsd(planDef.priceUsd)
                .miningEfficiencyBonusPercent(planDef.miningEfficiencyBonusPercent)
                .adFree(planDef.adFree)
                .autoBoostMining(planDef.autoBoostMining)
                .referralReregisterUnlimited(planDef.referralReregisterUnlimited)
                .fullMiningHistoryAccess(planDef.fullMiningHistoryAccess)
                .profileImageUnlock(planDef.profileImageUnlock)
                .build());
        }
        return Future.succeededFuture(SubscriptionPlanResponseDto.builder().plans(plans).build());
    }

    private static void addPlan(PlanDef planDef) {
        PLAN_DEFS.put(planDef.planCode, planDef);
    }

    private boolean isExpired(Subscription subscription) {
        return subscription.getExpiresAt() != null && subscription.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private SubscriptionStatusResponseDto emptyStatus() {
        return SubscriptionStatusResponseDto.builder()
            .isSubscribed(false)
            .expiresAt(null)
            .packageType(null)
            .planCode(null)
            .miningEfficiencyBonusPercent(BONUS_FREE)
            .adFree(false)
            .autoBoostMining(false)
            .referralReregisterUnlimited(false)
            .fullMiningHistoryAccess(false)
            .profileImageUnlock(false)
            .build();
    }

    private PlanDef inferPlan(String packageType, Integer months, Integer days) {
        if (days != null && days == 7) {
            return PLAN_DEFS.get(PLAN_VIP_PASS_7D);
        }
        if (months != null) {
            return switch (months) {
                case 1 -> PLAN_DEFS.get(PLAN_VIP_1M);
                case 3 -> PLAN_DEFS.get(PLAN_VIP_3M);
                case 6 -> PLAN_DEFS.get(PLAN_VIP_6M);
                case 12 -> PLAN_DEFS.get(PLAN_VIP_12M);
                default -> null;
            };
        }
        if (PACKAGE_TYPE_VIP.equalsIgnoreCase(packageType)) {
            return PLAN_DEFS.get(PLAN_VIP_1M);
        }
        return null;
    }

    private PlanDef resolvePlanDef(Subscription subscription) {
        String storedType = subscription.getPackageType();
        if (storedType != null) {
            PlanDef direct = PLAN_DEFS.get(storedType);
            if (direct != null) {
                return direct;
            }
        }

        if (PACKAGE_TYPE_VIP.equalsIgnoreCase(storedType)) {
            long durationDays = inferDurationDays(subscription);
            if (durationDays <= 8) return PLAN_DEFS.get(PLAN_VIP_PASS_7D);
            if (durationDays <= 35) return PLAN_DEFS.get(PLAN_VIP_1M);
            if (durationDays <= 100) return PLAN_DEFS.get(PLAN_VIP_3M);
            if (durationDays <= 200) return PLAN_DEFS.get(PLAN_VIP_6M);
            return PLAN_DEFS.get(PLAN_VIP_12M);
        }

        return new PlanDef(
            storedType,
            storedType,
            storedType,
            null,
            null,
            null,
            BONUS_FREE,
            false,
            false,
            false,
            false,
            false
        );
    }

    private long inferDurationDays(Subscription subscription) {
        if (subscription.getStartedAt() == null || subscription.getExpiresAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(subscription.getStartedAt(), subscription.getExpiresAt()).toDays());
    }

    private static class PlanDef {
        private final String planCode;
        private final String packageType;
        private final String displayName;
        private final Integer durationDays;
        private final Integer priceKrw;
        private final String priceUsd;
        private final Integer miningEfficiencyBonusPercent;
        private final Boolean adFree;
        private final Boolean autoBoostMining;
        private final Boolean referralReregisterUnlimited;
        private final Boolean fullMiningHistoryAccess;
        private final Boolean profileImageUnlock;

        private PlanDef(String planCode, String packageType, String displayName, Integer durationDays,
                        Integer priceKrw, String priceUsd, Integer miningEfficiencyBonusPercent, Boolean adFree,
                        Boolean autoBoostMining, Boolean referralReregisterUnlimited, Boolean fullMiningHistoryAccess,
                        Boolean profileImageUnlock) {
            this.planCode = planCode;
            this.packageType = packageType;
            this.displayName = displayName;
            this.durationDays = durationDays;
            this.priceKrw = priceKrw;
            this.priceUsd = priceUsd;
            this.miningEfficiencyBonusPercent = miningEfficiencyBonusPercent;
            this.adFree = adFree;
            this.autoBoostMining = autoBoostMining;
            this.referralReregisterUnlimited = referralReregisterUnlimited;
            this.fullMiningHistoryAccess = fullMiningHistoryAccess;
            this.profileImageUnlock = profileImageUnlock;
        }
    }
}
