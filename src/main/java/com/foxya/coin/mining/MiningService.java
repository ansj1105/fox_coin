package com.foxya.coin.mining;

import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.bonus.BonusService;
import com.foxya.coin.bonus.entities.UserBonus;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.level.LevelService;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import com.foxya.coin.mining.dto.MiningHistoryResponseDto;
import com.foxya.coin.mining.dto.MiningInfoResponseDto;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningHistory;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
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
    private static final int MAX_AD_WATCH_COUNT = 5;
    
    public MiningService(PgPool pool, MiningRepository miningRepository, UserRepository userRepository,
                         BonusService bonusService, BonusRepository bonusRepository, WalletRepository walletRepository) {
        super(pool);
        this.miningRepository = miningRepository;
        this.userRepository = userRepository;
        this.bonusService = bonusService;
        this.bonusRepository = bonusRepository;
        this.walletRepository = walletRepository;
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
        
        return Future.all(userFuture, dailyMiningFuture, bonusEfficiencyFuture, adWatchBonusFuture, totalBalanceFuture)
            .compose(compositeFuture -> {
                com.foxya.coin.user.entities.User user = userFuture.result();
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                
                DailyMining dailyMining = dailyMiningFuture.result();
                Integer bonusEfficiency = bonusEfficiencyFuture.result();
                UserBonus adWatchBonus = adWatchBonusFuture.result();
                BigDecimal totalBalance = totalBalanceFuture.result();
                
                Integer currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                BigDecimal todayMiningAmount = dailyMining != null ? dailyMining.getMiningAmount() : BigDecimal.ZERO;
                
                // 다음 레벨 필요 경험치 계산 (LevelService의 LEVEL_EXP 배열 참조)
                BigDecimal[] levelExp = {
                    BigDecimal.ZERO,           // LV1: 0
                    BigDecimal.valueOf(1000),   // LV2: 1000
                    BigDecimal.valueOf(3000),   // LV3: 3000
                    BigDecimal.valueOf(6000),   // LV4: 6000
                    BigDecimal.valueOf(10000),  // LV5: 10000
                    BigDecimal.valueOf(15000),  // LV6: 15000
                    BigDecimal.valueOf(21000),  // LV7: 21000
                    BigDecimal.valueOf(28000),  // LV8: 28000
                    BigDecimal.valueOf(36000)   // LV9: 36000
                };
                BigDecimal nextLevelRequired = levelExp.length > currentLevel 
                    ? levelExp[currentLevel] 
                    : BigDecimal.ZERO;
                
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
                        
                        // 광고 시청 정보
                        Integer adWatchCount = adWatchBonus != null && adWatchBonus.getCurrentCount() != null 
                            ? adWatchBonus.getCurrentCount() : 0;
                        Integer maxAdWatchCount = adWatchBonus != null && adWatchBonus.getMaxCount() != null 
                            ? adWatchBonus.getMaxCount() : MAX_AD_WATCH_COUNT;
                        
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
                            .build();
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
                ).map(bonus -> true);
            });
    }
}

