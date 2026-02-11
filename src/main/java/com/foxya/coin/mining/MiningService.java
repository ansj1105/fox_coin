package com.foxya.coin.mining;

import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.bonus.BonusService;
import com.foxya.coin.bonus.entities.UserBonus;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.level.LevelService;
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
    private static final int MAX_AD_WATCH_COUNT = 5;
    /** 부스터 영상 보너스 타입 (채굴 시작 필수: 1회 시청) */
    private static final String BOOSTER_VIDEO_BONUS_TYPE = "BOOSTER_VIDEO";
    
    public MiningService(PgPool pool, MiningRepository miningRepository, UserRepository userRepository,
                         BonusService bonusService, BonusRepository bonusRepository, WalletRepository walletRepository,
                         ReferralService referralService, TransferRepository transferRepository, CurrencyRepository currencyRepository,
                         EmailVerificationRepository emailVerificationRepository, LevelService levelService) {
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
    }
    
    public Future<DailyLimitResponseDto> getDailyLimit(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                
                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                
                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                    .compose(miningLevel -> {
                        if (miningLevel == null) {
                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("레벨 정보를 찾을 수 없습니다."));
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
     * 채굴 내역 조회 (체굴 + 레퍼럴 수익 병합, 날짜순 정렬)
     */
    public Future<MiningHistoryResponseDto> getMiningHistory(Long userId, String period, Integer limit, Integer offset) {
        String periodValue = RankingPeriod.fromValue(period).getValue();
        int limitValue = limit != null && limit > 0 ? limit : 20;
        int offsetValue = offset != null && offset >= 0 ? offset : 0;
        LocalDate startDate = getStartDateForPeriod(periodValue);
        
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                String nickname = user.getLoginId();
                
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
     * 채굴 정보 조회.
     * 호출 시 진행 중인 채굴 세션을 먼저 정산(settle)한 뒤, 활성 세션의 종료 시각(miningUntil)과 오늘 시작한 세션 수(adWatchCount)를 반환.
     */
    public Future<MiningInfoResponseDto> getMiningInfo(Long userId) {
        LocalDate today = LocalDate.now();
        
        return settleActiveMiningSession(userId)
            .compose(v -> {
                // 사용자 정보 조회
                Future<com.foxya.coin.user.entities.User> userFuture = userRepository.getUserById(pool, userId);
                Future<DailyMining> dailyMiningFuture = miningRepository.getDailyMining(pool, userId, today);
                // 보너스 효율: 친구 초대(유효 직접 초대 수) 티어만 사용. -old: bonusService.getBonusEfficiency(userId) (8-type: 소셜연동, 본인인증, 광고시청 등)
                // Future<Integer> bonusEfficiencyFuture = bonusService.getBonusEfficiency(userId).map(response -> response.getTotalEfficiency());
                Future<UserBonus> adWatchBonusFuture = bonusRepository.getUserBonus(pool, userId, "AD_WATCH");
                // 채굴/래퍼럴 적립은 KORI(INTERNAL) 지갑에 들어가므로, INTERNAL KORI 잔액을 우선 사용
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
                Future<Integer> validDirectCountFuture = referralService.getValidDirectReferralCount(userId);
                // 오늘 시작한 채굴 세션 수 = 부스터 사용 횟수 (Tap to boost 잔여 = maxAdWatchCount - adWatchCount)
                Future<Integer> sessionsStartedTodayFuture = miningRepository.countSessionsStartedToday(pool, userId, today);
                Future<MiningSession> activeSessionFuture = miningRepository.getActiveMiningSession(pool, userId);
                
                return Future.all(List.of(userFuture, dailyMiningFuture, adWatchBonusFuture, totalBalanceFuture, inviteBonusFuture, validDirectCountFuture, sessionsStartedTodayFuture, activeSessionFuture))
                    .compose(compositeFuture -> {
                        com.foxya.coin.user.entities.User user = userFuture.result();
                        if (user == null) {
                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                        }
                        DailyMining dailyMining = dailyMiningFuture.result();
                        BigDecimal inviteMultiplier = inviteBonusFuture.result();
                        // 보너스 효율(%) = 친구 초대 티어만. (multiplier - 1) * 100. 예: 1.06 → 6%
                        Integer bonusEfficiency = inviteMultiplier != null
                            ? inviteMultiplier.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).intValue()
                            : 0;
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
                                // miningUntil: 활성 채굴 세션이 있고 종료 시각이 미래이면 해당 시각(ISO-8601). 이 시각까지 부스터 버튼 비활성화.
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
     * 진행 중인 채굴 세션을 "지금 시각"까지 정산 (시간당 채굴량 비례 적립).
     * getMiningInfo / credit-video 호출 시 호출.
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
                                        // KORI(INTERNAL) 지갑 없으면 자동 생성 (채굴·에어드랍과 동일)
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
                                                    return miningRepository.updateMiningSessionLastSettled(pool, session.getId(), settleEnd)
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
                                                ).compose(v -> levelService.syncLevelFromExp(userId).map(x -> (Void) null))
                                                .compose(v -> completeSessionIfEnded(userId, session, settleEnd));
                                            });
                                    });
                            });
                    });
            });
    }

    /**
     * 세션 종료 시점(settleEnd >= session.ends_at)이면 mining_history 삽입, 레퍼럴 보상, 레벨 동기화 수행.
     * amountToCredit=0(한도 도달)이어도 세션 종료 시 호출됨.
     */
    private Future<Void> completeSessionIfEnded(Long userId, MiningSession session, LocalDateTime settleEnd) {
        boolean sessionCompleted = !settleEnd.isBefore(session.getEndsAt());
        if (!sessionCompleted) return Future.succeededFuture();
        return userRepository.getUserById(pool, userId)
            .compose(u -> {
                Integer ul = (u != null && u.getLevel() != null) ? u.getLevel() : 1;
                return miningRepository.insertMiningHistory(pool, userId, ul, session.getRatePerHour(), "BROADCAST_WATCH", "COMPLETED");
            })
            .compose(mh -> referralService.grantReferralRewardForMining(userId, session.getRatePerHour()))
            .compose(x -> levelService.syncLevelFromExp(userId))
            .map(x -> (Void) null);
    }

    /**
     * 해당 유저의 정산 대기 세션이 있으면 지금 시각까지 정산 후, 종료된 세션은 mining_history·internal_transfers 반영.
     * API 조회 없이 배치에서 호출할 때 사용.
     */
    public Future<Void> settlePendingSessionForUser(Long userId) {
        return settleActiveMiningSession(userId);
    }

    /**
     * 정산 대기 세션이 있는 모든 유저에 대해 settle 수행 (mining_history, internal_transfers 반영).
     * Vert.x setPeriodic(1시간 등)에서 주기 호출하여, 앱 미접속 유저의 채굴 완료도 DB에 반영.
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
     * 영상 시청 1회 = 1시간 채굴 세션 시작 (MINING_AND_LEVEL_SPEC).
     * 즉시 전량 적립 없음. 세션 생성 후 1시간에 걸쳐 시간당 채굴량만큼 settle로 적립. 다음 영상은 1시간 후에만 시청 가능.
     */
    public Future<BigDecimal> creditMiningForVideo(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        // 1. 먼저 진행 중인 세션 있으면 지금까지 분량 settle
        return settleActiveMiningSession(userId)
            .compose(v -> checkDailyMiningCap(userId, today))
            .compose(optCapped -> {
                if (optCapped != null) return Future.succeededFuture(optCapped);
                return doCreditMiningForVideoAfterCapCheck(userId, today, now);
            });
    }

    /**
     * 일일 최대 채굴량 도달 시 Optional.of(BigDecimal.ZERO), 아니면 null.
     */
    private Future<BigDecimal> checkDailyMiningCap(Long userId, LocalDate today) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) return Future.succeededFuture(null);
                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                    .compose(miningLevel -> miningRepository.getDailyMining(pool, userId, today)
                        .map(dailyMining -> {
                            BigDecimal todayAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                            BigDecimal dailyMax = miningLevel != null ? miningLevel.getDailyMaxMining() : BigDecimal.ZERO;
                            return todayAmount.compareTo(dailyMax) >= 0 ? BigDecimal.ZERO : null;
                        }));
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
                // 2. 1시간이 안 지났으면 다음 영상 시청 불가
                if (activeSession != null && activeSession.getEndsAt().isAfter(now)) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("1시간이 지난 후에 다음 영상을 시청할 수 있습니다."));
                }
                return userRepository.getUserById(pool, userId);
            })
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                Integer userLevel = user.getLevel() != null ? user.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, userLevel)
                    .compose(miningLevel -> {
                        if (miningLevel == null) {
                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("레벨 정보를 찾을 수 없습니다."));
                        }
                        int dailyMaxVideos = miningLevel.getDailyMaxVideos() != null && miningLevel.getDailyMaxVideos() > 0 ? miningLevel.getDailyMaxVideos() : 5;
                        return miningRepository.countSessionsStartedToday(pool, userId, today)
                            .compose(sessionsToday -> {
                                if (sessionsToday >= dailyMaxVideos) {
                                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("오늘 시청 가능한 영상 횟수를 모두 사용했습니다."));
                                }
                                BigDecimal dailyMax = miningLevel.getDailyMaxMining();
                                BigDecimal perVideoBase = dailyMax.divide(BigDecimal.valueOf(dailyMaxVideos), 18, RoundingMode.DOWN);
                                // 채굴 rate: 친구 초대 티어 배율만 적용. -old: perVideoBase * (1 + bonusEfficiency/100) * inviteMultiplier (BonusService 8-type 포함)
                                // return referralService.getInviteMiningBonusMultiplier(userId).compose(mult -> bonusService.getBonusEfficiency(userId).map(...));
                                return referralService.getInviteMiningBonusMultiplier(userId)
                                    .map(multiplier -> perVideoBase.multiply(multiplier != null ? multiplier : BigDecimal.ONE).setScale(18, RoundingMode.DOWN))
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
     * 광고 시청 처리
     */
    public Future<Boolean> watchAd(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24); // 24시간 후 만료
        
        return bonusRepository.getUserBonus(pool, userId, "AD_WATCH")
            .compose(existingBonus -> {
                Integer currentCount = existingBonus != null && existingBonus.getCurrentCount() != null 
                    ? existingBonus.getCurrentCount() : 0;
                Integer maxCount = existingBonus != null && existingBonus.getMaxCount() != null 
                    ? existingBonus.getMaxCount() : MAX_AD_WATCH_COUNT;
                
                // 최대 횟수 체크
                if (currentCount >= maxCount) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("일일 최대 광고 시청 횟수를 초과했습니다."));
                }
                
                // current_count 증가 및 is_active 설정
                Integer newCount = currentCount + 1;
                // 광고 시청 후 보너스 활성화 (24시간 동안 유효)
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
                        // 부스터 영상 1회 시청 처리 (채굴 시작 필수 조건)
                        return bonusRepository.createOrUpdateUserBonus(
                            pool, userId, BOOSTER_VIDEO_BONUS_TYPE, true, null, 1, 1, null
                        ).map(b -> true);
                    });
            });
    }
}
