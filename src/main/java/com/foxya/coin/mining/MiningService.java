package com.foxya.coin.mining;

import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.bonus.BonusService;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.level.LevelService;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationI18nUtils;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import com.foxya.coin.mining.dto.MiningHistoryResponseDto;
import com.foxya.coin.mining.dto.MiningInfoResponseDto;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningHistory;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.mining.entities.MiningSession;
import com.foxya.coin.referral.ReferralService;
import com.foxya.coin.subscription.SubscriptionService;
import com.foxya.coin.subscription.dto.SubscriptionStatusResponseDto;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.WalletClientViewUtils;
import com.foxya.coin.wallet.OfflinePayCollateralReserveClient;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class MiningService extends BaseService {
    private static final int MAX_SETTLEMENT_SESSIONS_PER_USER_RUN = 100;
    private static final LocalDate MINING_DECAY_START_DATE = LocalDate.of(2026, 1, 1);
    private static final String PARTNER_FOXYYA_BOOSTER_TYPE = "PARTNER_FOXYYA";
    private static final BigDecimal MAX_REFERRAL_MULTIPLIER = BigDecimal.valueOf(1.22);
    private static final BigDecimal MAX_VIP_MULTIPLIER = BigDecimal.valueOf(1.25);
    
    private final MiningRepository miningRepository;
    private final UserRepository userRepository;
    private final BonusService bonusService;
    private final BonusRepository bonusRepository;
    private final WalletRepository walletRepository;
    private final ReferralService referralService;

    private static Boolean toRestrictionFlag(Integer value) {
        return value != null && value == 1;
    }
    private final TransferRepository transferRepository;
    private final CurrencyRepository currencyRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final LevelService levelService;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;
    private final OfflinePayCollateralReserveClient offlinePayCollateralReserveClient;
    private static final int MAX_AD_WATCH_COUNT = 5;
    /** Normalized comment. */
    private static final String BOOSTER_VIDEO_BONUS_TYPE = "BOOSTER_VIDEO";
    private static final String MINING_SESSION_ENDED_TITLE = "부스트 적용 종료";
    private static final String MINING_SESSION_ENDED_MESSAGE = "부스트 적용이 끝났습니다. 채굴이 다시 가능합니다.";
    private static final String MINING_SESSION_ENDED_TITLE_KEY = "notifications.miningSessionEnded.title";
    private static final String MINING_SESSION_ENDED_MESSAGE_KEY = "notifications.miningSessionEnded.message";
    
    public MiningService(PgPool pool, MiningRepository miningRepository, UserRepository userRepository,
                         BonusService bonusService, BonusRepository bonusRepository, WalletRepository walletRepository,
                         ReferralService referralService, TransferRepository transferRepository, CurrencyRepository currencyRepository,
                         EmailVerificationRepository emailVerificationRepository, LevelService levelService,
                         NotificationService notificationService, SubscriptionService subscriptionService,
                         OfflinePayCollateralReserveClient offlinePayCollateralReserveClient) {
        super(pool);
        this.miningRepository = miningRepository;
        this.userRepository = userRepository;
        this.bonusService = bonusService;
        this.bonusRepository = bonusRepository;
        this.walletRepository = walletRepository;
        this.referralService = referralService;
        this.transferRepository = transferRepository;
        this.currencyRepository = currencyRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.levelService = levelService;
        this.notificationService = notificationService;
        this.subscriptionService = subscriptionService;
        this.offlinePayCollateralReserveClient = offlinePayCollateralReserveClient;
    }
    
    public Future<DailyLimitResponseDto> getDailyLimit(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                }
                
                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                
                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                    .compose(miningLevel -> {
                        if (miningLevel == null) {
                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                        }
                        
                        LocalDate today = LocalDate.now();
                        LocalDateTime resetAt = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
                        
                        return miningRepository.getDailyMining(pool, userId, today)
                            .recover(throwable -> {
                                log.error("Failed to read daily mining snapshot. userId={}, date={}", userId, today, throwable);
                                return Future.succeededFuture(null);
                            })
                            .map(dailyMining -> {
                                BigDecimal todayAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                                BigDecimal maxMining = miningLevel.getDailyMaxMining();
                                boolean isLimitReached = todayAmount.compareTo(maxMining) >= 0;
                                
                                return DailyLimitResponseDto.builder()
                                    .currentLevel(userLevel)
                                    .dailyMaxMining(maxMining)
                                    .efficiency(miningLevel.getEfficiency())
                                    .requiredExp(miningLevel.getRequiredExp())
                                    .perMinuteMining(miningLevel.getPerMinuteMining())
                                    .expectedDays(miningLevel.getExpectedDays())
                                    .dailyMaxAds(resolveDailyMaxAds(miningLevel))
                                    .storeProductLimit(miningLevel.getStoreProductLimit())
                                    .badgeCode(miningLevel.getBadgeCode())
                                    .photoUrl(miningLevel.getPhotoUrl())
                                    .todayMiningAmount(todayAmount)
                                    .resetAt(resetAt)
                                    .isLimitReached(isLimitReached)
                                    .build();
                            });
                    });
            });
    }
    
    public Future<LevelInfoResponseDto> getLevelInfo() {
        return miningRepository.getAllMiningLevels(pool)
            .map(levels -> {
                List<LevelInfoResponseDto.LevelInfo> levelInfos = new ArrayList<>();
                for (MiningLevel level : levels) {
                    levelInfos.add(LevelInfoResponseDto.LevelInfo.builder()
                        .level(level.getLevel())
                        .dailyMaxMining(level.getDailyMaxMining())
                        .efficiency(level.getEfficiency())
                        .requiredExp(level.getRequiredExp())
                        .perMinuteMining(level.getPerMinuteMining())
                        .expectedDays(level.getExpectedDays())
                        .dailyMaxAds(level.getDailyMaxAds())
                        .storeProductLimit(level.getStoreProductLimit())
                        .badgeCode(level.getBadgeCode())
                        .photoUrl(level.getPhotoUrl())
                        .build());
                }
                
                return LevelInfoResponseDto.builder()
                    .levels(levelInfos)
                    .build();
            });
    }
    
    /**
      * Normalized comment.
     */
    public Future<MiningHistoryResponseDto> getMiningHistory(Long userId, String period, Integer limit, Integer offset) {
        String periodValue = RankingPeriod.fromValue(period).getValue();
        int limitValue = limit != null && limit > 0 ? limit : 20;
        int offsetValue = offset != null && offset >= 0 ? offset : 0;
        LocalDate startDate = getStartDateForPeriod(periodValue);
        
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                }
                final String nickname = (user.getNickname() != null && !user.getNickname().isBlank()) ? user.getNickname() : user.getLoginId();
                
                int fetchSize = limitValue + offsetValue;
                Future<List<MiningHistory>> miningListFuture = miningRepository.getMiningHistory(pool, userId, periodValue, fetchSize, 0);
                Future<Long> miningCountFuture = miningRepository.getMiningHistoryCount(pool, userId, periodValue);
                Future<BigDecimal> totalMinedFuture = miningRepository.getMiningHistoryTotalAmount(pool, userId, periodValue);
                Future<List<InternalTransfer>> referralListFuture = transferRepository.getReferralRewardTransfersByReceiver(pool, userId, startDate, fetchSize, 0);
                Future<Long> referralCountFuture = transferRepository.getReferralRewardCountByReceiver(pool, userId, startDate);
                Future<BigDecimal> totalReferralFuture = transferRepository.getReferralRewardTotalAmountByReceiver(pool, userId, startDate);
                
                return Future.all(miningListFuture, miningCountFuture, totalMinedFuture, referralListFuture, referralCountFuture, totalReferralFuture)
                    .map(composite -> {
                        List<MiningHistory> miningList = miningListFuture.result() != null ? miningListFuture.result() : List.of();
                        Long miningCount = miningCountFuture.result();
                        BigDecimal totalMinedAmount = totalMinedFuture.result() != null ? totalMinedFuture.result() : BigDecimal.ZERO;
                        List<InternalTransfer> referralList = referralListFuture.result() != null ? referralListFuture.result() : List.of();
                        Long referralCount = referralCountFuture.result();
                        BigDecimal totalReferralAmount = totalReferralFuture.result() != null ? totalReferralFuture.result() : BigDecimal.ZERO;
                        
                        List<MiningHistoryResponseDto.MiningHistoryItem> miningItems = new ArrayList<>();
                        for (MiningHistory mh : miningList) {
                            miningItems.add(MiningHistoryResponseDto.MiningHistoryItem.builder()
                                .id(mh.getId())
                                .level(mh.getLevel())
                                .nickname(nickname)
                                .amount(mh.getAmount())
                                .efficiency(mh.getEfficiency())
                                .type(mh.getType())
                                .status(mh.getStatus())
                                .createdAt(mh.getCreatedAt())
                                .build());
                        }
                        List<MiningHistoryResponseDto.MiningHistoryItem> referralItems = new ArrayList<>();
                        for (InternalTransfer it : referralList) {
                            referralItems.add(MiningHistoryResponseDto.MiningHistoryItem.builder()
                                .id(-(it.getId() != null ? it.getId() : 0L))
                                .level(null)
                                .nickname(nickname)
                                .amount(it.getAmount())
                                .type(InternalTransfer.TYPE_REFERRAL_REWARD)
                                .status(it.getStatus())
                                .createdAt(it.getCreatedAt())
                                .build());
                        }
                        List<MiningHistoryResponseDto.MiningHistoryItem> merged = new ArrayList<>();
                        merged.addAll(miningItems);
                        merged.addAll(referralItems);
                        merged.sort(Comparator.comparing(MiningHistoryResponseDto.MiningHistoryItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
                        int from = Math.min(offsetValue, merged.size());
                        int to = Math.min(offsetValue + limitValue, merged.size());
                        List<MiningHistoryResponseDto.MiningHistoryItem> page = merged.subList(from, to);
                        
                        BigDecimal totalAmount = totalMinedAmount.add(totalReferralAmount);
                        return MiningHistoryResponseDto.builder()
                            .items(page)
                            .total((miningCount != null ? miningCount : 0L) + (referralCount != null ? referralCount : 0L))
                            .totalAmount(totalAmount)
                            .totalMinedAmount(totalMinedAmount)
                            .totalReferralAmount(totalReferralAmount)
                            .limit(limitValue)
                            .offset(offsetValue)
                            .build();
                    });
            });
    }
    
    private static LocalDate getStartDateForPeriod(String period) {
        if (period == null || "ALL".equals(period)) return null;
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
      * Normalized comment.
      * Normalized comment.
     */
    public Future<MiningInfoResponseDto> getMiningInfo(Long userId) {
        LocalDate today = LocalDate.now();
        
        // Keep /info read-only. Pending session settlement is handled by the write paths
        // and the background batch to avoid turning a frequently-polled read endpoint
        // into a balance-mutating request.
        Future<com.foxya.coin.user.entities.User> userFuture = userRepository.getUserById(pool, userId);
        Future<DailyMining> dailyMiningFuture = miningRepository.getDailyMining(pool, userId, today)
            .recover(throwable -> {
                log.error("Failed to read daily mining snapshot for mining info. userId={}, date={}", userId, today, throwable);
                return Future.succeededFuture(null);
            });
        Future<BigDecimal> totalBalanceFuture = walletRepository.getWalletsByUserId(pool, userId)
            .map(wallets -> WalletClientViewUtils.sumLogicalBalanceByCurrencyCode(wallets, "KORI"))
            .recover(throwable -> {
                log.error("Failed to read wallet balance for mining info. userId={}", userId, throwable);
                return Future.succeededFuture(BigDecimal.ZERO);
            });
        Future<BigDecimal> collateralLockedFuture = offlinePayCollateralReserveClient == null
            ? Future.succeededFuture(BigDecimal.ZERO)
            : offlinePayCollateralReserveClient.getLockedAmount(userId, "KORI");
        Future<Integer> missionEfficiencyFuture = Future.succeededFuture(0);
        Future<Integer> validDirectCountFuture = referralService.getValidDirectReferralCount(userId)
            .recover(throwable -> {
                log.error("Failed to read valid direct referral count for mining info. userId={}", userId, throwable);
                return Future.succeededFuture(0);
            });
        Future<SubscriptionStatusResponseDto> subscriptionStatusFuture = subscriptionService.getSubscriptionStatus(userId)
            .recover(throwable -> {
                log.error("Failed to read subscription status for mining info. userId={}", userId, throwable);
                return Future.succeededFuture(null);
            });
        Future<Integer> sessionsStartedTodayFuture = miningRepository.countSessionsStartedToday(pool, userId, today)
            .recover(throwable -> {
                log.error("Failed to count sessions started today for mining info. userId={}, date={}", userId, today, throwable);
                return Future.succeededFuture(0);
            });
        Future<MiningSession> activeSessionFuture = miningRepository.getActiveMiningSession(pool, userId)
            .recover(throwable -> {
                log.error("Failed to read active mining session for mining info. userId={}", userId, throwable);
                return Future.succeededFuture(null);
            });
        Future<MiningSession> settlementPendingSessionFuture = miningRepository.getSettlementPendingMiningSession(pool, userId)
            .recover(throwable -> {
                log.error("Failed to read settlement pending session for mining info. userId={}", userId, throwable);
                return Future.succeededFuture(null);
            });

        return Future.all(List.of(
            userFuture,
            dailyMiningFuture,
            totalBalanceFuture,
            collateralLockedFuture,
            missionEfficiencyFuture,
            validDirectCountFuture,
            subscriptionStatusFuture,
            sessionsStartedTodayFuture,
            activeSessionFuture,
            settlementPendingSessionFuture
        ))
            .compose(compositeFuture -> {
                com.foxya.coin.user.entities.User user = userFuture.result();
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                }
                DailyMining dailyMining = dailyMiningFuture.result();
                Integer validDirectReferralCount = validDirectCountFuture.result();
                BigDecimal inviteMultiplier = referralService.resolveInviteMiningBonusMultiplier(validDirectReferralCount);
                Integer inviteEfficiency = inviteMultiplier != null
                    ? inviteMultiplier.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue()
                    : 0;
                Integer missionEfficiency = missionEfficiencyFuture.result() != null ? missionEfficiencyFuture.result() : 0;
                SubscriptionStatusResponseDto subscriptionStatus = subscriptionStatusFuture.result();
                int vipBonusEfficiency = resolveVipBonusEfficiency(subscriptionStatus);
                Integer bonusEfficiency = inviteEfficiency + missionEfficiency + vipBonusEfficiency;
                BigDecimal totalBalance = totalBalanceFuture.result()
                    .subtract(collateralLockedFuture.result() == null ? BigDecimal.ZERO : collateralLockedFuture.result())
                    .max(BigDecimal.ZERO);
                int sessionsToday = sessionsStartedTodayFuture.result() != null ? sessionsStartedTodayFuture.result() : 0;
                MiningSession activeSession = activeSessionFuture.result();
                MiningSession settlementPendingSession = settlementPendingSessionFuture.result();
                Integer currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                BigDecimal todayMiningAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                BigDecimal nextLevelRequired = LevelService.getRequiredExpForNextLevel(currentLevel);

                return miningRepository.getMiningLevelByLevel(pool, currentLevel)
                    .map(miningLevel -> {
                        BigDecimal dailyMaxMining = miningLevel != null ? miningLevel.getDailyMaxMining() : BigDecimal.ZERO;
                        BigDecimal projectedPendingAmount = calculatePendingDisplayAmount(
                            settlementPendingSession,
                            LocalDateTime.now(),
                            todayMiningAmount,
                            dailyMaxMining
                        );
                        BigDecimal displayTodayMiningAmount = todayMiningAmount.add(projectedPendingAmount);
                        BigDecimal displayTotalBalance = totalBalance.add(projectedPendingAmount);
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime nextMidnight = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
                        Duration remaining = Duration.between(now, nextMidnight);
                        long hours = remaining.toHours();
                        long minutes = remaining.toMinutes() % 60;
                        long seconds = remaining.getSeconds() % 60;
                        String remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                        int dailyMaxVideos = resolveDailyMaxAds(miningLevel);
                        String miningUntil = (activeSession != null && activeSession.getEndsAt() != null && activeSession.getEndsAt().isAfter(now))
                            ? activeSession.getEndsAt().toString()
                            : null;
                        BigDecimal ratePerHour = (activeSession != null && activeSession.getRatePerHour() != null)
                            ? activeSession.getRatePerHour()
                            : BigDecimal.ZERO;
                        return MiningInfoResponseDto.builder()
                            .todayMiningAmount(displayTodayMiningAmount)
                            .totalBalance(displayTotalBalance)
                            .bonusEfficiency(bonusEfficiency != null ? bonusEfficiency : 0)
                            .inviteEfficiency(inviteEfficiency)
                            .missionEfficiency(missionEfficiency)
                            .remainingTime(remainingTime)
                            .isActive(true)
                            .dailyMaxMining(dailyMaxMining)
                            .currentLevel(currentLevel)
                            .nextLevelRequired(nextLevelRequired)
                            .adWatchCount(sessionsToday)
                            .maxAdWatchCount(dailyMaxVideos)
                            .miningUntil(miningUntil)
                            .ratePerHour(ratePerHour)
                            .inviteBonusMultiplier(inviteMultiplier != null ? inviteMultiplier : BigDecimal.ONE)
                            .validDirectReferralCount(validDirectReferralCount != null ? validDirectReferralCount : 0)
                            .warning(toRestrictionFlag(user.getIsWarning()))
                            .miningSuspended(toRestrictionFlag(user.getIsMiningSuspended()))
                            .accountBlocked(toRestrictionFlag(user.getIsAccountBlocked()))
                            .subscribed(isSubscribed(subscriptionStatus))
                            .adFree(isAdFree(subscriptionStatus))
                            .autoBoostMining(isAutoBoostMining(subscriptionStatus))
                            .vipMiningEfficiencyBonusPercent(vipBonusEfficiency)
                            .fullMiningHistoryAccess(hasFullMiningHistoryAccess(subscriptionStatus))
                            .build();
                    });
            });
    }

    private BigDecimal calculatePendingDisplayAmount(
        MiningSession settlementPendingSession,
        LocalDateTime now,
        BigDecimal todayMiningAmount,
        BigDecimal dailyMaxMining
    ) {
        if (settlementPendingSession == null
            || settlementPendingSession.getRatePerHour() == null
            || settlementPendingSession.getLastSettledAt() == null
            || settlementPendingSession.getEndsAt() == null) {
            return BigDecimal.ZERO;
        }

        LocalDateTime settleEnd = now.isBefore(settlementPendingSession.getEndsAt())
            ? now
            : settlementPendingSession.getEndsAt();
        long elapsedSeconds = Duration.between(settlementPendingSession.getLastSettledAt(), settleEnd).getSeconds();
        if (elapsedSeconds <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal rawPendingAmount = settlementPendingSession.getRatePerHour()
            .multiply(BigDecimal.valueOf(elapsedSeconds))
            .divide(BigDecimal.valueOf(3600), 18, RoundingMode.DOWN);
        if (rawPendingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal safeTodayMiningAmount = todayMiningAmount != null ? todayMiningAmount : BigDecimal.ZERO;
        BigDecimal safeDailyMaxMining = dailyMaxMining != null ? dailyMaxMining : BigDecimal.ZERO;
        BigDecimal headroom = safeDailyMaxMining.subtract(safeTodayMiningAmount).max(BigDecimal.ZERO);
        return rawPendingAmount.min(headroom);
    }

    private int resolveDailyMaxAds(MiningLevel miningLevel) {
        if (miningLevel == null) {
            return MAX_AD_WATCH_COUNT;
        }
        if (miningLevel.getDailyMaxAds() != null && miningLevel.getDailyMaxAds() > 0) {
            return miningLevel.getDailyMaxAds();
        }
        if (miningLevel.getDailyMaxVideos() != null && miningLevel.getDailyMaxVideos() > 0) {
            return miningLevel.getDailyMaxVideos();
        }
        return MAX_AD_WATCH_COUNT;
    }
    
    /**
      * Normalized comment.
      * Normalized comment.
     */
    private Future<Void> settleActiveMiningSession(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        return miningRepository.getSettlementPendingMiningSession(pool, userId)
            .compose(session -> {
                if (session == null) return Future.succeededFuture();
                LocalDateTime settleEnd = now.isBefore(session.getEndsAt()) ? now : session.getEndsAt();
                long elapsedSeconds = Duration.between(session.getLastSettledAt(), settleEnd).getSeconds();
                if (elapsedSeconds <= 0) {
                    return miningRepository.updateMiningSessionLastSettled(pool, session.getId(), settleEnd)
                        .compose(v -> completeSessionIfEnded(userId, session, settleEnd));
                }
                BigDecimal amount = session.getRatePerHour()
                    .multiply(BigDecimal.valueOf(elapsedSeconds))
                    .divide(BigDecimal.valueOf(3600), 18, RoundingMode.DOWN);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    return miningRepository.updateMiningSessionLastSettled(pool, session.getId(), settleEnd)
                        .compose(v -> completeSessionIfEnded(userId, session, settleEnd));
                }
                return userRepository.getUserById(pool, userId)
                    .compose(user -> {
                        Integer userLevel = user != null && user.getLevel() != null ? user.getLevel() : 1;
                        return miningRepository.getMiningLevelByLevel(pool, userLevel);
                    })
                    .compose(miningLevel -> {
                        BigDecimal dailyMaxMining = miningLevel != null ? miningLevel.getDailyMaxMining() : BigDecimal.ZERO;
                        return currencyRepository.getCurrencyByCodeAndChainAllowInactive(pool, "KORI", "INTERNAL")
                            .compose(currency -> {
                                if (currency == null) return Future.succeededFuture();
                                return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                                    .compose(wallet -> {
                                        // Normalized comment.
                                        if (wallet == null) {
                                            String dummyAddress = "KORI_INTERNAL_" + userId + "_" + currency.getId();
                                            return walletRepository.createWallet(pool, userId, currency.getId(), dummyAddress)
                                                .recover(throwable -> {
                                                    if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                                                        return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId());
                                                    }
                                                    return io.vertx.core.Future.failedFuture(throwable);
                                                })
                                                .compose(created -> created != null ? Future.succeededFuture(created) : transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId()));
                                        }
                                        return Future.succeededFuture(wallet);
                                    })
                                    .compose(wallet -> {
                                        if (wallet == null) return Future.succeededFuture();
                                        return miningRepository.getDailyMining(pool, userId, today)
                                            .compose(dailyMining -> {
                                                BigDecimal currentCreditedAmount = session.getCreditedAmount() != null
                                                    ? session.getCreditedAmount()
                                                    : BigDecimal.ZERO;
                                                BigDecimal todayAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                                                BigDecimal headroom = dailyMaxMining.subtract(todayAmount).max(BigDecimal.ZERO);
                                                BigDecimal amountToCredit = amount.min(headroom);
                                                if (amountToCredit.compareTo(BigDecimal.ZERO) <= 0) {
                                                    Future<Void> notifyFuture = todayAmount.compareTo(dailyMaxMining) >= 0
                                                        ? this.notifyMiningLimitReachedOnce(userId, today, dailyMaxMining, todayAmount)
                                                        : Future.succeededFuture();
                                                    return notifyFuture.compose(n -> miningRepository.updateMiningSessionLastSettled(pool, session.getId(), settleEnd))
                                                        .compose(v -> completeSessionIfEnded(userId, session, settleEnd));
                                                }
                                                LocalDateTime resetAt = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
                                                BigDecimal newTodayTotal = todayAmount.add(amountToCredit);
                                                BigDecimal newCreditedAmount = currentCreditedAmount.add(amountToCredit);
                                                return pool.withTransaction(client ->
                                                    transferRepository.addBalance(client, wallet.getId(), amountToCredit)
                                                        .compose(v -> miningRepository.createOrUpdateDailyMining(client, userId, today, newTodayTotal, resetAt))
                                                        .compose(v -> userRepository.addExp(client, userId, amountToCredit))
                                                        .compose(v -> miningRepository.addMiningSessionCreditedAmount(client, session.getId(), amountToCredit))
                                                        .compose(v -> miningRepository.updateMiningSessionLastSettled(client, session.getId(), settleEnd))
                                                        .map(v -> (Void) null)
                                                ).compose(v -> {
                                                    boolean reachedNow = todayAmount.compareTo(dailyMaxMining) < 0
                                                        && newTodayTotal.compareTo(dailyMaxMining) >= 0;
                                                    if (reachedNow) {
                                                        return this.notifyMiningLimitReachedOnce(userId, today, dailyMaxMining, newTodayTotal);
                                                    }
                                                    return Future.succeededFuture();
                                                })
                                                .compose(v -> levelService.syncLevelFromExp(userId).map(x -> (Void) null))
                                                .compose(v -> completeSessionIfEnded(userId, withCreditedAmount(session, newCreditedAmount), settleEnd));
                                            });
                                    });
                            });
                    });
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
     */
    private Future<Void> completeSessionIfEnded(Long userId, MiningSession session, LocalDateTime settleEnd) {
        boolean sessionCompleted = !settleEnd.isBefore(session.getEndsAt());
        if (!sessionCompleted) return Future.succeededFuture();
        BigDecimal creditedAmount = session.getCreditedAmount() != null ? session.getCreditedAmount() : BigDecimal.ZERO;
        return userRepository.getUserById(pool, userId)
            .compose(u -> {
                Integer ul = (u != null && u.getLevel() != null) ? u.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, ul)
                    .compose(miningLevel -> {
                        Integer efficiency = null;
                        if (miningLevel != null
                            && miningLevel.getDailyMaxMining() != null
                            && miningLevel.getDailyMaxMining().compareTo(BigDecimal.ZERO) > 0
                            && session.getRatePerHour() != null) {
                            int dailyMaxVideos = resolveDailyMaxAds(miningLevel);
                            BigDecimal perVideoBase = miningLevel.getDailyMaxMining()
                                .divide(BigDecimal.valueOf(dailyMaxVideos), 18, RoundingMode.DOWN);
                            if (perVideoBase.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal ratio = session.getRatePerHour()
                                    .divide(perVideoBase, 6, RoundingMode.HALF_UP);
                                efficiency = ratio.subtract(BigDecimal.ONE)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(0, RoundingMode.HALF_UP)
                                    .intValue();
                                if (efficiency < 0) efficiency = 0;
                            }
                        }
                        if (creditedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                            return Future.succeededFuture();
                        }
                        return miningRepository.insertMiningHistory(pool, userId, ul, creditedAmount, efficiency, "BROADCAST_WATCH", "COMPLETED")
                            .map(x -> (Void) null);
                    });
            })
            .compose(v -> referralService.grantReferralRewardForMining(userId, creditedAmount))
            .compose(x -> levelService.syncLevelFromExp(userId))
            .compose(x -> createMiningSessionEndedNotification(userId, session))
            .compose(x -> maybeStartAutoBoostSession(userId))
            .map(x -> (Void) null);
    }

    private MiningSession withCreditedAmount(MiningSession session, BigDecimal creditedAmount) {
        return MiningSession.builder()
            .id(session.getId())
            .userId(session.getUserId())
            .startedAt(session.getStartedAt())
            .endsAt(session.getEndsAt())
            .ratePerHour(session.getRatePerHour())
            .lastSettledAt(session.getLastSettledAt())
            .creditedAmount(creditedAmount)
            .createdAt(session.getCreatedAt())
            .build();
    }

    private Future<Void> createMiningSessionEndedNotification(Long userId, MiningSession session) {
        if (notificationService == null || userId == null || session == null || session.getId() == null) {
            return Future.succeededFuture();
        }
        JsonObject variables = new JsonObject()
            .put("sessionId", session.getId())
            .put("endedAt", session.getEndsAt() != null ? session.getEndsAt().toString() : null);
        String metadata = NotificationI18nUtils.buildMetadata(
            MINING_SESSION_ENDED_TITLE_KEY,
            MINING_SESSION_ENDED_MESSAGE_KEY,
            variables
        );
        return notificationService.createNotificationIfAbsentByRelatedId(
                userId,
                NotificationType.MINING_SESSION_ENDED,
                MINING_SESSION_ENDED_TITLE,
                MINING_SESSION_ENDED_MESSAGE,
                session.getId(),
                metadata
            )
            .map(v -> (Void) null)
            .recover(err -> {
                log.warn("Mining session ended notification failed (ignored): userId={}, sessionId={}", userId, session.getId(), err);
                return Future.succeededFuture((Void) null);
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
     */
    public Future<Void> settlePendingSessionForUser(Long userId) {
        return settlePendingSessionForUser(userId, 0);
    }

    private Future<Void> settlePendingSessionForUser(Long userId, int settledCount) {
        if (settledCount >= MAX_SETTLEMENT_SESSIONS_PER_USER_RUN) {
            log.warn("Mining settlement safety cap reached - userId: {}, settledCount: {}", userId, settledCount);
            return Future.succeededFuture();
        }
        return miningRepository.getSettlementPendingMiningSession(pool, userId)
            .compose(session -> {
                if (session == null) {
                    return Future.succeededFuture();
                }
                return settleActiveMiningSession(userId)
                    .compose(v -> settlePendingSessionForUser(userId, settledCount + 1));
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
     */
    public Future<Integer> runSettlementBatch() {
        return miningRepository.getDistinctUserIdsWithPendingSettlement(pool)
            .compose(userIds -> {
                if (userIds == null || userIds.isEmpty()) {
                    return Future.succeededFuture(0);
                }
                log.info("Mining settlement batch: {} users with pending sessions", userIds.size());
                return settleUserIdsSequentially(userIds, 0);
            });
    }

    private Future<Integer> settleUserIdsSequentially(List<Long> userIds, int index) {
        if (index >= userIds.size()) {
            return Future.succeededFuture(userIds.size());
        }
        Long userId = userIds.get(index);
        return settlePendingSessionForUser(userId)
            .recover(throwable -> {
                log.warn("Mining settlement batch failed for userId={}, continuing", userId, throwable);
                return Future.succeededFuture();
            })
            .compose(v -> settleUserIdsSequentially(userIds, index + 1));
    }
    
    /**
      * Normalized comment.
      * Normalized comment.
     */
    public Future<BigDecimal> creditMiningForVideo(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        // Normalized comment.
        return settleActiveMiningSession(userId)
            .compose(v -> checkDailyMiningCap(userId, today))
            .compose(optCapped -> {
                if (optCapped != null) return Future.succeededFuture(optCapped);
                return doCreditMiningForVideoAfterCapCheck(userId, today, now);
            });
    }

    /**
      * Normalized comment.
     */
    private Future<BigDecimal> checkDailyMiningCap(Long userId, LocalDate today) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) return Future.succeededFuture(null);
                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                    .compose(miningLevel -> miningRepository.getDailyMining(pool, userId, today)
                        .compose(dailyMining -> {
                            BigDecimal todayAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                            BigDecimal dailyMax = miningLevel != null ? miningLevel.getDailyMaxMining() : BigDecimal.ZERO;
                            if (todayAmount.compareTo(dailyMax) >= 0) {
                                return this.notifyMiningLimitReachedOnce(userId, today, dailyMax, todayAmount)
                                    .map(v -> BigDecimal.ZERO);
                            }
                            return Future.succeededFuture((BigDecimal) null);
                        }));
            });
    }

    private Future<Void> notifyMiningLimitReachedOnce(Long userId, LocalDate localDate, BigDecimal dailyMaxMining, BigDecimal todayMiningAmount) {
        if (notificationService == null || todayMiningAmount == null || dailyMaxMining == null) {
            return Future.succeededFuture();
        }
        if (todayMiningAmount.compareTo(dailyMaxMining) < 0) {
            return Future.succeededFuture();
        }
        String title = "\uAE08\uC77C \uCC44\uAD74\uB7C9 \uB2EC\uC131";
        String message = "\uAE08\uC77C \uCD5C\uB300 \uCC44\uAD74\uB7C9\uC744 \uBAA8\uB450 \uCC44\uAD74\uD558\uC168\uC2B5\uB2C8\uB2E4.";
        JsonObject metadata = new JsonObject()
            .put("localDate", localDate.toString())
            .put("dailyMaxMining", dailyMaxMining.toPlainString())
            .put("todayMiningAmount", todayMiningAmount.toPlainString());
        return notificationService.createNotificationIfAbsentByDate(
                userId, NotificationType.MINING_LIMIT_REACHED, title, message, localDate, localDate.toEpochDay(), metadata.encode())
            .map(v -> (Void) null)
            .recover(err -> {
                log.warn("Mining limit notification failed (ignored) - userId: {}, date: {}", userId, localDate, err);
                return Future.succeededFuture();
            });
    }

    private Future<BigDecimal> doCreditMiningForVideoAfterCapCheck(Long userId, LocalDate today, LocalDateTime now) {
        return bonusRepository.getUserBonus(pool, userId, BOOSTER_VIDEO_BONUS_TYPE)
            .compose(booster -> {
                int current = booster != null && booster.getCurrentCount() != null ? booster.getCurrentCount() : 0;
                int newCount = current + 1;
                int maxCount = Math.max(newCount, 999);
                return bonusRepository.createOrUpdateUserBonus(pool, userId, BOOSTER_VIDEO_BONUS_TYPE, true, null, newCount, maxCount, null);
            })
            .compose(x -> miningRepository.getActiveMiningSession(pool, userId))
            .compose(activeSession -> {
                // Normalized comment.
                if (activeSession != null && activeSession.getEndsAt().isAfter(now)) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("1시간이 지난 후에 다음 영상을 시청할 수 있습니다."));
                }
                return userRepository.getUserById(pool, userId);
            })
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                }
                return subscriptionService.getSubscriptionStatus(userId)
                    .compose(subscriptionStatus -> {
                        int vipBonusEfficiency = resolveVipBonusEfficiency(subscriptionStatus);
                        Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                        return miningRepository.getMiningLevelByLevel(pool, userLevel)
                            .compose(miningLevel -> {
                                if (miningLevel == null) {
                                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                                }
                                int dailyMaxVideos = resolveDailyMaxAds(miningLevel);
                                return miningRepository.countSessionsStartedToday(pool, userId, today)
                                    .compose(sessionsToday -> {
                                        if (sessionsToday >= dailyMaxVideos) {
                                            return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("오늘 시청 가능한 영상 횟수를 모두 사용했습니다."));
                                        }
                                        BigDecimal dailyMax = miningLevel.getDailyMaxMining();
                                        BigDecimal perVideoBase = dailyMax.divide(BigDecimal.valueOf(dailyMaxVideos), 18, RoundingMode.DOWN);
                                        return calculateRatePerHour(userId, perVideoBase, vipBonusEfficiency)
                                            .compose(ratePerHour -> {
                                                LocalDateTime endsAt = now.plusHours(1);
                                                return pool.withTransaction(client ->
                                                    miningRepository.createMiningSession(client, userId, now, endsAt, ratePerHour, now)
                                                ).map(created -> ratePerHour);
                                            });
                                    });
                            });
                    });
            });
    }

    private Future<Void> maybeStartAutoBoostSession(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        return subscriptionService.getSubscriptionStatus(userId)
            .compose(subscriptionStatus -> {
                if (!isAutoBoostMining(subscriptionStatus)) {
                    return Future.succeededFuture();
                }
                return miningRepository.getActiveMiningSession(pool, userId)
                    .compose(activeSession -> {
                        if (activeSession != null && activeSession.getEndsAt() != null && activeSession.getEndsAt().isAfter(now)) {
                            return Future.succeededFuture();
                        }
                        return userRepository.getUserById(pool, userId)
                            .compose(user -> {
                                if (user == null) return Future.succeededFuture();
                                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                                    .compose(miningLevel -> {
                                        if (miningLevel == null || miningLevel.getDailyMaxMining() == null || miningLevel.getDailyMaxMining().compareTo(BigDecimal.ZERO) <= 0) {
                                            return Future.succeededFuture();
                                        }
                                        int dailyMaxVideos = resolveDailyMaxAds(miningLevel);
                                        return Future.all(
                                            miningRepository.countSessionsStartedToday(pool, userId, today),
                                            miningRepository.getDailyMining(pool, userId, today)
                                        ).compose(tuple -> {
                                            Integer sessionsToday = tuple.resultAt(0);
                                            DailyMining dailyMining = tuple.resultAt(1);
                                            int usedSessions = sessionsToday != null ? sessionsToday : 0;
                                            if (usedSessions >= dailyMaxVideos) {
                                                return Future.succeededFuture();
                                            }
                                            BigDecimal todayAmount = dailyMining != null && dailyMining.getMiningAmount() != null
                                                ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                                            if (todayAmount.compareTo(miningLevel.getDailyMaxMining()) >= 0) {
                                                return Future.succeededFuture();
                                            }

                                            int vipBonusEfficiency = resolveVipBonusEfficiency(subscriptionStatus);
                                            BigDecimal perVideoBase = miningLevel.getDailyMaxMining()
                                                .divide(BigDecimal.valueOf(dailyMaxVideos), 18, RoundingMode.DOWN);
                                            return calculateRatePerHour(userId, perVideoBase, vipBonusEfficiency)
                                                .compose(ratePerHour -> {
                                                    LocalDateTime startsAt = LocalDateTime.now();
                                                    LocalDateTime endsAt = startsAt.plusHours(1);
                                                    return pool.withTransaction(client ->
                                                        miningRepository.createMiningSession(client, userId, startsAt, endsAt, ratePerHour, startsAt)
                                                            .map(x -> (Void) null)
                                                    );
                                                });
                                        });
                                    });
                            });
                    });
            });
    }

    private Future<BigDecimal> calculateRatePerHour(Long userId, BigDecimal perVideoBase, int vipBonusEfficiency) {
        if (perVideoBase == null || perVideoBase.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.succeededFuture(BigDecimal.ZERO);
        }
        return referralService.getInviteMiningBonusMultiplier(userId)
            .compose(referralMultiplier -> miningRepository.getActiveMiningBoosterEfficiency(pool, PARTNER_FOXYYA_BOOSTER_TYPE)
                .map(partnerEfficiency -> {
                    BigDecimal activityMultiplier = BigDecimal.ONE;
                    BigDecimal safeReferralMultiplier = capMultiplier(
                        referralMultiplier != null ? referralMultiplier : BigDecimal.ONE,
                        BigDecimal.ONE,
                        MAX_REFERRAL_MULTIPLIER
                    );
                    BigDecimal vipMultiplier = capMultiplier(
                        percentageToMultiplier(vipBonusEfficiency),
                        BigDecimal.ONE,
                        MAX_VIP_MULTIPLIER
                    );
                    BigDecimal partnerMultiplier = percentageToMultiplier(partnerEfficiency);
                    BigDecimal periodDecayMultiplier = resolvePeriodDecayMultiplier(LocalDate.now());

                    return perVideoBase
                        .multiply(activityMultiplier)
                        .multiply(safeReferralMultiplier)
                        .multiply(vipMultiplier)
                        .multiply(periodDecayMultiplier)
                        .multiply(partnerMultiplier)
                        .setScale(18, RoundingMode.DOWN);
                }));
    }

    private BigDecimal percentageToMultiplier(Integer percent) {
        int safePercent = Math.max(percent != null ? percent : 0, 0);
        return BigDecimal.ONE.add(BigDecimal.valueOf(safePercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
    }

    private BigDecimal capMultiplier(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) return min;
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }

    private BigDecimal resolvePeriodDecayMultiplier(LocalDate date) {
        int serviceYear = Math.max(1, date.getYear() - MINING_DECAY_START_DATE.getYear() + 1);
        return switch (serviceYear) {
            case 1 -> BigDecimal.valueOf(1.00);
            case 2 -> BigDecimal.valueOf(0.80);
            case 3 -> BigDecimal.valueOf(0.65);
            case 4 -> BigDecimal.valueOf(0.50);
            case 5 -> BigDecimal.valueOf(0.40);
            case 6 -> BigDecimal.valueOf(0.35);
            case 7 -> BigDecimal.valueOf(0.15);
            default -> BigDecimal.valueOf(0.07);
        };
    }

    private int resolveVipBonusEfficiency(SubscriptionStatusResponseDto subscriptionStatus) {
        if (!isSubscribed(subscriptionStatus)) {
            return 0;
        }
        Integer bonus = subscriptionStatus.getMiningEfficiencyBonusPercent();
        return bonus != null && bonus > 0 ? bonus : 0;
    }

    private boolean isSubscribed(SubscriptionStatusResponseDto subscriptionStatus) {
        return subscriptionStatus != null && Boolean.TRUE.equals(subscriptionStatus.getIsSubscribed());
    }

    private boolean isAdFree(SubscriptionStatusResponseDto subscriptionStatus) {
        return isSubscribed(subscriptionStatus) && Boolean.TRUE.equals(subscriptionStatus.getAdFree());
    }

    private boolean isAutoBoostMining(SubscriptionStatusResponseDto subscriptionStatus) {
        return isSubscribed(subscriptionStatus) && Boolean.TRUE.equals(subscriptionStatus.getAutoBoostMining());
    }

    private boolean hasFullMiningHistoryAccess(SubscriptionStatusResponseDto subscriptionStatus) {
        return isSubscribed(subscriptionStatus) && Boolean.TRUE.equals(subscriptionStatus.getFullMiningHistoryAccess());
    }
    
    /**
      * Normalized comment.
     */
    public Future<Boolean> watchAd(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24); // 24-hour bonus window
        
        return bonusRepository.getUserBonus(pool, userId, "AD_WATCH")
            .compose(existingBonus -> {
                Integer currentCount = existingBonus != null && existingBonus.getCurrentCount() != null 
                    ? existingBonus.getCurrentCount() : 0;
                Integer maxCount = existingBonus != null && existingBonus.getMaxCount() != null 
                    ? existingBonus.getMaxCount() : MAX_AD_WATCH_COUNT;
                
                // Normalized comment.
                if (currentCount >= maxCount) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("일일 최대 광고 시청 횟수를 초과했습니다."));
                }
                
                // Normalized comment.
                Integer newCount = currentCount + 1;
                // Normalized comment.
                Boolean isActive = true;
                
                return bonusRepository.createOrUpdateUserBonus(
                    pool, 
                    userId, 
                    "AD_WATCH", 
                    isActive, 
                    expiresAt, 
                    newCount, 
                    maxCount, 
                    null
                )
                    .compose(adBonus -> {
                        // Normalized comment.
                        return bonusRepository.createOrUpdateUserBonus(
                            pool, userId, BOOSTER_VIDEO_BONUS_TYPE, true, null, 1, 1, null
                        ).map(b -> true);
                    });
            });
    }
}
