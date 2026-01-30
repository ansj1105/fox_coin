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
import com.foxya.coin.referral.ReferralService;
import com.foxya.coin.transfer.TransferRepository;
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
import java.util.List;

@Slf4j
public class MiningService extends BaseService {
    
    private final MiningRepository miningRepository;
    private final UserRepository userRepository;
    private final BonusService bonusService;
    private final BonusRepository bonusRepository;
    private final WalletRepository walletRepository;
    private final ReferralService referralService;
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
     * 채굴 내역 조회 (래퍼럴 수익 제외)
     */
    public Future<MiningHistoryResponseDto> getMiningHistory(Long userId, String period, Integer limit, Integer offset) {
        // period 기본값 처리
        String periodValue = RankingPeriod.fromValue(period).getValue();
        
        // limit, offset 기본값 처리
        int limitValue = limit != null && limit > 0 ? limit : 20;
        int offsetValue = offset != null && offset >= 0 ? offset : 0;
        
        // 사용자 정보 조회 (nickname용)
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                
                String nickname = user.getLoginId(); // loginId를 nickname으로 사용
                
                // 채굴 내역, 총 개수, 총 합계를 병렬로 조회
                Future<List<MiningHistory>> historyFuture = miningRepository.getMiningHistory(pool, userId, periodValue, limitValue, offsetValue);
                Future<Long> countFuture = miningRepository.getMiningHistoryCount(pool, userId, periodValue);
                Future<BigDecimal> totalAmountFuture = miningRepository.getMiningHistoryTotalAmount(pool, userId, periodValue);
                
                return Future.all(historyFuture, countFuture, totalAmountFuture)
                    .map(compositeFuture -> {
                        List<MiningHistory> history = historyFuture.result();
                        Long total = countFuture.result();
                        BigDecimal totalAmount = totalAmountFuture.result();
                        
                        // DTO 변환
                        List<MiningHistoryResponseDto.MiningHistoryItem> items = new ArrayList<>();
                        for (MiningHistory mh : history) {
                            items.add(MiningHistoryResponseDto.MiningHistoryItem.builder()
                                .id(mh.getId())
                                .level(mh.getLevel())
                                .nickname(nickname)
                                .amount(mh.getAmount())
                                .type(mh.getType())
                                .status(mh.getStatus())
                                .createdAt(mh.getCreatedAt())
                                .build());
                        }
                        
                        return MiningHistoryResponseDto.builder()
                            .items(items)
                            .total(total)
                            .totalAmount(totalAmount)
                            .limit(limitValue)
                            .offset(offsetValue)
                            .build();
                    });
            });
    }
    
    /**
     * 채굴 정보 조회
     */
    public Future<MiningInfoResponseDto> getMiningInfo(Long userId) {
        LocalDate today = LocalDate.now();
        
        // 사용자 정보 조회
        Future<com.foxya.coin.user.entities.User> userFuture = userRepository.getUserById(pool, userId);
        
        // 오늘 채굴량 조회
        Future<DailyMining> dailyMiningFuture = miningRepository.getDailyMining(pool, userId, today);
        
        // 보너스 효율 조회
        Future<Integer> bonusEfficiencyFuture = bonusService.getBonusEfficiency(userId)
            .map(response -> response.getTotalEfficiency());
        
        // 광고 시청 보너스 조회
        Future<UserBonus> adWatchBonusFuture = bonusRepository.getUserBonus(pool, userId, "AD_WATCH");
        
        // 지갑 잔액 조회 (KORI 통화 우선, 없으면 첫 번째 지갑)
        Future<BigDecimal> totalBalanceFuture = walletRepository.getWalletsByUserId(pool, userId)
            .map(wallets -> {
                if (wallets == null || wallets.isEmpty()) {
                    return BigDecimal.ZERO;
                }
                // KORI 통화 우선
                for (Wallet wallet : wallets) {
                    if ("KORI".equalsIgnoreCase(wallet.getCurrencyCode())) {
                        return wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
                    }
                }
                // KORI가 없으면 첫 번째 지갑 잔액
                return wallets.get(0).getBalance() != null ? wallets.get(0).getBalance() : BigDecimal.ZERO;
            });
        
        // 초대 보너스 배율·유효 직접 초대 수 (REFERRAL_AND_INVITE_SPEC)
        Future<BigDecimal> inviteBonusFuture = referralService.getInviteMiningBonusMultiplier(userId);
        Future<Integer> validDirectCountFuture = referralService.getValidDirectReferralCount(userId);
        
        // 오늘 부스터 영상 시청(채굴 적립) 횟수 — Tap to boost 잔여 횟수 = maxAdWatchCount - adWatchCount
        Future<Integer> todayMiningCountFuture = miningRepository.getTodayMiningCount(pool, userId);
        
        return Future.all(List.of(userFuture, dailyMiningFuture, bonusEfficiencyFuture, adWatchBonusFuture, totalBalanceFuture, inviteBonusFuture, validDirectCountFuture, todayMiningCountFuture))
            .compose(compositeFuture -> {
                com.foxya.coin.user.entities.User user = userFuture.result();
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                
                DailyMining dailyMining = dailyMiningFuture.result();
                Integer bonusEfficiency = bonusEfficiencyFuture.result();
                UserBonus adWatchBonus = adWatchBonusFuture.result();
                BigDecimal totalBalance = totalBalanceFuture.result();
                Integer todayMiningCount = todayMiningCountFuture.result() != null ? todayMiningCountFuture.result() : 0;
                
                Integer currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                BigDecimal todayMiningAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                
                // 다음 레벨 누적 필요 EXP (MINING_AND_LEVEL_SPEC: 5, 15, 35, 70, 130, 220, 350, 520)
                int[] levelExpCumulative = {5, 15, 35, 70, 130, 220, 350, 520};
                BigDecimal nextLevelRequired = currentLevel >= 9
                    ? BigDecimal.valueOf(520)
                    : BigDecimal.valueOf(levelExpCumulative[currentLevel - 1]);
                
                // 일일 최대 채굴량 조회
                return miningRepository.getMiningLevelByLevel(pool, currentLevel)
                    .map(miningLevel -> {
                        BigDecimal dailyMaxMining = miningLevel != null ? miningLevel.getDailyMaxMining() : BigDecimal.ZERO;
                        
                        // 남은 시간 계산 (다음 자정까지)
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime nextMidnight = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
                        Duration remaining = Duration.between(now, nextMidnight);
                        
                        long hours = remaining.toHours();
                        long minutes = remaining.toMinutes() % 60;
                        long seconds = remaining.getSeconds() % 60;
                        String remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                        
                        // 부스터 잔여 횟수: 오늘 채굴(영상) 적립 횟수 / 레벨별 일일 상한 (credit-video 호출 시 감소)
                        int dailyMaxVideos = miningLevel != null && miningLevel.getDailyMaxVideos() != null && miningLevel.getDailyMaxVideos() > 0
                            ? miningLevel.getDailyMaxVideos() : MAX_AD_WATCH_COUNT;
                        Integer adWatchCount = todayMiningCount;
                        Integer maxAdWatchCount = dailyMaxVideos;
                        
                        BigDecimal inviteBonusMultiplier = inviteBonusFuture.result();
                        Integer validDirectReferralCount = validDirectCountFuture.result();
                        
                        return MiningInfoResponseDto.builder()
                            .todayMiningAmount(todayMiningAmount)
                            .totalBalance(totalBalance)
                            .bonusEfficiency(bonusEfficiency != null ? bonusEfficiency : 0)
                            .remainingTime(remainingTime)
                            .isActive(true) // 사용자가 활성화되어 있다고 가정
                            .dailyMaxMining(dailyMaxMining)
                            .currentLevel(currentLevel)
                            .nextLevelRequired(nextLevelRequired)
                            .adWatchCount(adWatchCount)
                            .maxAdWatchCount(maxAdWatchCount)
                            .inviteBonusMultiplier(inviteBonusMultiplier != null ? inviteBonusMultiplier : BigDecimal.ONE)
                            .validDirectReferralCount(validDirectReferralCount != null ? validDirectReferralCount : 0)
                            .build();
                    });
            });
    }
    
    /**
     * 영상 시청 1회당 채굴 적립 (MINING_AND_LEVEL_SPEC)
     * 필수: 이메일 인증 완료, 부스터 영상 1회 시청. 레벨별 일일 영상 상한·일일 최대 채굴량 적용. 1 KORI = 1 EXP, 레벨 동기화.
     */
    public Future<BigDecimal> creditMiningForVideo(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime resetAt = LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT);
        // 1. 채굴 시작 필수: 이메일 인증 완료만 확인. 이번 호출이 곧 부스터 시청 1회이므로 선행 부스터 횟수 검사 제거.
        return emailVerificationRepository.getLatestByUserId(pool, userId)
            .compose(ev -> {
                if (ev == null || !Boolean.TRUE.equals(ev.isVerified)) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("채굴을 시작하려면 이메일 인증을 완료해 주세요."));
                }
                // 2. 이번 시청을 부스터 1회로 적립(첫 시청이면 1, 이후는 누적)
                return bonusRepository.getUserBonus(pool, userId, BOOSTER_VIDEO_BONUS_TYPE)
                    .compose(booster -> {
                        int current = booster != null && booster.getCurrentCount() != null ? booster.getCurrentCount() : 0;
                        int newCount = current + 1;
                        int maxCount = Math.max(newCount, 999);
                        return bonusRepository.createOrUpdateUserBonus(pool, userId, BOOSTER_VIDEO_BONUS_TYPE, true, null, newCount, maxCount, null);
                    })
                    .compose(v -> userRepository.getUserById(pool, userId));
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
                        BigDecimal dailyMax = miningLevel.getDailyMaxMining();
                        int dailyMaxVideos = miningLevel.getDailyMaxVideos() != null && miningLevel.getDailyMaxVideos() > 0 ? miningLevel.getDailyMaxVideos() : 5;
                        return miningRepository.getTodayMiningCount(pool, userId)
                            .compose(todayCount -> {
                                if (todayCount >= dailyMaxVideos) {
                                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("오늘 시청 가능한 영상 횟수를 모두 사용했습니다."));
                                }
                                return miningRepository.getDailyMining(pool, userId, today)
                                    .compose(dailyMining -> {
                                        BigDecimal todayAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                                        return referralService.getInviteMiningBonusMultiplier(userId)
                                            .map(multiplier -> {
                                                // 영상 1회당 채굴량 = (일일 최대 / 일일 영상 수) × 초대 보너스
                                                BigDecimal perVideoBase = dailyMax.divide(BigDecimal.valueOf(dailyMaxVideos), 18, RoundingMode.DOWN);
                                                BigDecimal rawAmount = perVideoBase.multiply(multiplier != null ? multiplier : BigDecimal.ONE).setScale(18, RoundingMode.DOWN);
                                                BigDecimal remaining = dailyMax.subtract(todayAmount);
                                                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                                                    return BigDecimal.ZERO;
                                                }
                                                return rawAmount.min(remaining);
                                            })
                                            .compose(amount -> {
                                                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                                                    return Future.succeededFuture(BigDecimal.ZERO);
                                                }
                                                return currencyRepository.getCurrencyByCodeAndChainAllowInactive(pool, "KORI", "INTERNAL")
                                                    .compose(currency -> {
                                                        if (currency == null) {
                                                            return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("KORI 통화를 찾을 수 없습니다."));
                                                        }
                                                        // 지갑 없으면 KORI(INTERNAL) 자동 생성 — getMiningInfo는 지갑 없어도 되지만 credit-video는 지갑 필수
                                                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                                                            .compose(wallet -> {
                                                                if (wallet == null) {
                                                                    String dummyAddress = "KORI_" + userId + "_" + currency.getId();
                                                                    return walletRepository.createWallet(pool, userId, currency.getId(), dummyAddress)
                                                                        .recover(throwable -> {
                                                                            if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                                                                                return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId());
                                                                            }
                                                                            return Future.failedFuture(throwable);
                                                                        });
                                                                }
                                                                return Future.succeededFuture(wallet);
                                                            })
                                                            .compose(wallet -> {
                                                                if (wallet == null) {
                                                                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("지갑을 찾을 수 없습니다."));
                                                                }
                                                                BigDecimal newTodayTotal = todayAmount.add(amount);
                                                                return pool.withTransaction(client ->
                                                                    miningRepository.createOrUpdateDailyMining(client, userId, today, newTodayTotal, resetAt)
                                                                        .compose(dm -> miningRepository.insertMiningHistory(client, userId, userLevel, amount, "BROADCAST_WATCH", "COMPLETED"))
                                                                        .compose(mh -> transferRepository.addBalance(client, wallet.getId(), amount))
                                                                        .compose(v -> userRepository.addExp(client, userId, amount))
                                                                )
                                                                    .compose(v -> referralService.grantReferralRewardForMining(userId, amount))
                                                                    .compose(v -> levelService.syncLevelFromExp(userId))
                                                                    .map(v -> amount);
                                                            });
                                                    });
                                            });
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

