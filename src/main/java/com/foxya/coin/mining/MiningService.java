package com.foxya.coin.mining;

import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.bonus.BonusService;
import com.foxya.coin.bonus.entities.UserBonus;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.level.LevelService;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import com.foxya.coin.mining.dto.MiningHistoryResponseDto;
import com.foxya.coin.mining.dto.MiningInfoResponseDto;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningHistory;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.mining.entities.MiningSession;
import com.foxya.coin.referral.ReferralService;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.WalletRepository;
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
    private static final int MAX_AD_WATCH_COUNT = 5;
    /** Normalized comment. */
    private static final String BOOSTER_VIDEO_BONUS_TYPE = "BOOSTER_VIDEO";
    
    public MiningService(PgPool pool, MiningRepository miningRepository, UserRepository userRepository,
                         BonusService bonusService, BonusRepository bonusRepository, WalletRepository walletRepository,
                         ReferralService referralService, TransferRepository transferRepository, CurrencyRepository currencyRepository,
                         EmailVerificationRepository emailVerificationRepository, LevelService levelService,
                         NotificationService notificationService) {
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
                            .map(dailyMining -> {
                                BigDecimal todayAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                                BigDecimal maxMining = miningLevel.getDailyMaxMining();
                                boolean isLimitReached = todayAmount.compareTo(maxMining) >= 0;
                                
                                return DailyLimitResponseDto.builder()
                                    .currentLevel(userLevel)
                                    .dailyMaxMining(maxMining)
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
        
        return settleActiveMiningSession(userId)
            .compose(v -> {
                // Normalized comment.
                Future<com.foxya.coin.user.entities.User> userFuture = userRepository.getUserById(pool, userId);
                Future<DailyMining> dailyMiningFuture = miningRepository.getDailyMining(pool, userId, today);
                // 보너스 효율: 초대 효율 + 미션 효율 합산. (미션 효율은 BonusService 기준, 초대는 invite multiplier)
                // Future<Integer> bonusEfficiencyFuture = bonusService.getBonusEfficiency(userId).map(response -> response.getTotalEfficiency());
                Future<UserBonus> adWatchBonusFuture = bonusRepository.getUserBonus(pool, userId, "AD_WATCH");
                // Normalized comment.
                Future<BigDecimal> totalBalanceFuture = walletRepository.getWalletsByUserId(pool, userId)
                    .map(wallets -> {
                        if (wallets == null || wallets.isEmpty()) return BigDecimal.ZERO;
                        Wallet internalKori = null;
                        Wallet anyKori = null;
                        for (Wallet wallet : wallets) {
                            if (!"KORI".equalsIgnoreCase(wallet.getCurrencyCode())) continue;
                            anyKori = wallet;
                            if ("INTERNAL".equalsIgnoreCase(wallet.getNetwork())) {
                                internalKori = wallet;
                                break;
                            }
                        }
                        Wallet target = internalKori != null ? internalKori : anyKori;
                        return target != null && target.getBalance() != null ? target.getBalance() : BigDecimal.ZERO;
                    });
                Future<BigDecimal> inviteBonusFuture = referralService.getInviteMiningBonusMultiplier(userId);
                Future<Integer> missionEfficiencyFuture = Future.succeededFuture(0);
                Future<Integer> validDirectCountFuture = referralService.getValidDirectReferralCount(userId);
                // Normalized comment.
                Future<Integer> sessionsStartedTodayFuture = miningRepository.countSessionsStartedToday(pool, userId, today);
                Future<MiningSession> activeSessionFuture = miningRepository.getActiveMiningSession(pool, userId);
                
                return Future.all(List.of(userFuture, dailyMiningFuture, adWatchBonusFuture, totalBalanceFuture, inviteBonusFuture, missionEfficiencyFuture, validDirectCountFuture, sessionsStartedTodayFuture, activeSessionFuture))
                    .compose(compositeFuture -> {
                        com.foxya.coin.user.entities.User user = userFuture.result();
                        if (user == null) {
                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                        }
                        DailyMining dailyMining = dailyMiningFuture.result();
                        BigDecimal inviteMultiplier = inviteBonusFuture.result();
                        Integer inviteEfficiency = inviteMultiplier != null
                            ? inviteMultiplier.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue()
                            : 0;
                        Integer missionEfficiency = missionEfficiencyFuture.result() != null ? missionEfficiencyFuture.result() : 0;
                        Integer bonusEfficiency = inviteEfficiency + missionEfficiency;
                        BigDecimal totalBalance = totalBalanceFuture.result();
                        int sessionsToday = sessionsStartedTodayFuture.result() != null ? sessionsStartedTodayFuture.result() : 0;
                        MiningSession activeSession = activeSessionFuture.result();
                        Integer currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                        BigDecimal todayMiningAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                        int[] levelExpCumulative = {5, 15, 35, 70, 130, 220, 350, 520};
                        BigDecimal nextLevelRequired = currentLevel >= 9
                            ? BigDecimal.valueOf(520)
                            : BigDecimal.valueOf(levelExpCumulative[currentLevel - 1]);
                        
                        return miningRepository.getMiningLevelByLevel(pool, currentLevel)
                            .map(miningLevel -> {
                                BigDecimal dailyMaxMining = miningLevel != null ? miningLevel.getDailyMaxMining() : BigDecimal.ZERO;
                                LocalDateTime now = LocalDateTime.now();
                                LocalDateTime nextMidnight = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
                                Duration remaining = Duration.between(now, nextMidnight);
                                long hours = remaining.toHours();
                                long minutes = remaining.toMinutes() % 60;
                                long seconds = remaining.getSeconds() % 60;
                                String remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                                int dailyMaxVideos = miningLevel != null && miningLevel.getDailyMaxVideos() != null && miningLevel.getDailyMaxVideos() > 0
                                    ? miningLevel.getDailyMaxVideos() : MAX_AD_WATCH_COUNT;
                                // Normalized comment.
                                String miningUntil = (activeSession != null && activeSession.getEndsAt() != null && activeSession.getEndsAt().isAfter(now))
                                    ? activeSession.getEndsAt().toString()
                                    : null;
                                BigDecimal ratePerHour = (activeSession != null && activeSession.getRatePerHour() != null)
                                    ? activeSession.getRatePerHour()
                                    : BigDecimal.ZERO;
                                BigDecimal inviteBonusMultiplier = inviteBonusFuture.result();
                                Integer validDirectReferralCount = validDirectCountFuture.result();
                                return MiningInfoResponseDto.builder()
                                    .todayMiningAmount(todayMiningAmount)
                                    .totalBalance(totalBalance)
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
                                    .inviteBonusMultiplier(inviteBonusMultiplier != null ? inviteBonusMultiplier : BigDecimal.ONE)
                                    .validDirectReferralCount(validDirectReferralCount != null ? validDirectReferralCount : 0)
                                    .warning(toRestrictionFlag(user.getIsWarning()))
                                    .miningSuspended(toRestrictionFlag(user.getIsMiningSuspended()))
                                    .accountBlocked(toRestrictionFlag(user.getIsAccountBlocked()))
                                    .build();
                            });
                    });
            });
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
                                                return pool.withTransaction(client ->
                                                    transferRepository.addBalance(client, wallet.getId(), amountToCredit)
                                                        .compose(v -> miningRepository.createOrUpdateDailyMining(client, userId, today, newTodayTotal, resetAt))
                                                        .compose(v -> userRepository.addExp(client, userId, amountToCredit))
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
                                                .compose(v -> completeSessionIfEnded(userId, session, settleEnd));
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
        return userRepository.getUserById(pool, userId)
            .compose(u -> {
                Integer ul = (u != null && u.getLevel() != null) ? u.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, ul)
                    .compose(miningLevel -> {
                        Integer efficiency = null;
                        if (miningLevel != null
                            && miningLevel.getDailyMaxMining() != null
                            && miningLevel.getDailyMaxMining().compareTo(BigDecimal.ZERO) > 0
                            && miningLevel.getDailyMaxVideos() != null
                            && miningLevel.getDailyMaxVideos() > 0
                            && session.getRatePerHour() != null) {
                            BigDecimal perVideoBase = miningLevel.getDailyMaxMining()
                                .divide(BigDecimal.valueOf(miningLevel.getDailyMaxVideos()), 18, RoundingMode.DOWN);
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
                        return miningRepository.insertMiningHistory(pool, userId, ul, session.getRatePerHour(), efficiency, "BROADCAST_WATCH", "COMPLETED");
                    });
            })
            .compose(mh -> referralService.grantReferralRewardForMining(userId, session.getRatePerHour()))
            .compose(x -> levelService.syncLevelFromExp(userId))
            .map(x -> (Void) null);
    }

    /**
      * Normalized comment.
      * Normalized comment.
     */
    public Future<Void> settlePendingSessionForUser(Long userId) {
        return settleActiveMiningSession(userId);
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
                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                    .compose(miningLevel -> {
                        if (miningLevel == null) {
                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("Resource not found."));
                        }
                        int dailyMaxVideos = miningLevel.getDailyMaxVideos() != null && miningLevel.getDailyMaxVideos() > 0 ? miningLevel.getDailyMaxVideos() : 5;
                        return miningRepository.countSessionsStartedToday(pool, userId, today)
                            .compose(sessionsToday -> {
                                if (sessionsToday >= dailyMaxVideos) {
                                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("오늘 시청 가능한 영상 횟수를 모두 사용했습니다."));
                                }
                                BigDecimal dailyMax = miningLevel.getDailyMaxMining();
                                BigDecimal perVideoBase = dailyMax.divide(BigDecimal.valueOf(dailyMaxVideos), 18, RoundingMode.DOWN);
                                // 채굴 rate: (초대 효율 + 미션 효율) 합산 후 perVideoBase에 적용
                                // return referralService.getInviteMiningBonusMultiplier(userId).compose(mult -> bonusService.getBonusEfficiency(userId).map(...));
                                return referralService.getInviteMiningBonusMultiplier(userId)
                                    .compose(multiplier -> Future.succeededFuture(0)
                                        .map(missionEfficiency -> {
                                            int inviteEfficiency = multiplier != null
                                                ? multiplier.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue()
                                                : 0;
                                            int missionEff = missionEfficiency != null ? missionEfficiency : 0;
                                            int totalEfficiency = inviteEfficiency + missionEff;
                                            BigDecimal efficiencyMultiplier = BigDecimal.ONE.add(
                                                BigDecimal.valueOf(totalEfficiency)
                                                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                                            );
                                            return perVideoBase.multiply(efficiencyMultiplier).setScale(18, RoundingMode.DOWN);
                                        }))
                                    .compose(ratePerHour -> {
                                        LocalDateTime endsAt = now.plusHours(1);
                                        return pool.withTransaction(client ->
                                            miningRepository.createMiningSession(client, userId, now, endsAt, ratePerHour, now)
                                        ).map(created -> ratePerHour);
                                    });
                            });
                    });
            });
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

