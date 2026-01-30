package com.foxya.coin.level;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.level.dto.LevelGuideResponseDto;
import com.foxya.coin.level.dto.UserLevelResponseDto;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class LevelService extends BaseService {
    
    private final UserRepository userRepository;
    private final MiningRepository miningRepository;
    
    // 레벨업 누적 필요 EXP (MINING_AND_LEVEL_SPEC: LV1→2: 5, LV2→3: 15, ... LV8→9: 520)
    private static final BigDecimal[] LEVEL_EXP = {
        BigDecimal.valueOf(5),     // LV2 도달 누적
        BigDecimal.valueOf(15),    // LV3
        BigDecimal.valueOf(35),    // LV4
        BigDecimal.valueOf(70),    // LV5
        BigDecimal.valueOf(130),   // LV6
        BigDecimal.valueOf(220),   // LV7
        BigDecimal.valueOf(350),   // LV8
        BigDecimal.valueOf(520)   // LV9
    };
    
    public LevelService(PgPool pool, UserRepository userRepository, MiningRepository miningRepository) {
        super(pool);
        this.userRepository = userRepository;
        this.miningRepository = miningRepository;
    }
    
    public Future<UserLevelResponseDto> getUserLevel(Long userId) {
        return userRepository.getUserById(pool, userId)
            .map(user -> {
                if (user == null) {
                    throw new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다.");
                }
                
                Integer currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                BigDecimal currentExp = user.getExp() != null ? user.getExp() : BigDecimal.ZERO;
                
                // 다음 레벨 누적 필요 경험치 (LEVEL_EXP[0]=5=LV2, [7]=520=LV9)
                BigDecimal nextLevelExp;
                if (currentLevel >= 9) {
                    nextLevelExp = LEVEL_EXP[7]; // 최대 레벨
                } else {
                    nextLevelExp = LEVEL_EXP[currentLevel - 1];
                }
                
                // 현재 레벨 시작 누적 경험치
                BigDecimal currentLevelExp = currentLevel > 1 ? LEVEL_EXP[currentLevel - 2] : BigDecimal.ZERO;
                
                // 진행률 계산
                BigDecimal expNeeded = nextLevelExp.subtract(currentLevelExp);
                BigDecimal expProgress = currentExp.subtract(currentLevelExp);
                double progress = expNeeded.compareTo(BigDecimal.ZERO) > 0 
                    ? expProgress.divide(expNeeded, 4, BigDecimal.ROUND_HALF_UP).doubleValue()
                    : 1.0;
                
                return UserLevelResponseDto.builder()
                    .currentLevel(currentLevel)
                    .currentExp(currentExp)
                    .nextLevelExp(nextLevelExp)
                    .progress(Math.max(0.0, Math.min(1.0, progress)))
                    .build();
            });
    }
    
    public Future<LevelGuideResponseDto> getLevelGuide() {
        return miningRepository.getAllMiningLevels(pool)
            .map(levels -> {
                List<LevelGuideResponseDto.LevelInfo> levelInfos = new ArrayList<>();
                
                for (int i = 0; i < LEVEL_EXP.length && i < levels.size(); i++) {
                    int level = i + 1;
                    BigDecimal requiredExp = LEVEL_EXP[i];
                    BigDecimal dailyMaxMining = levels.get(i).getDailyMaxMining();
                    
                    levelInfos.add(LevelGuideResponseDto.LevelInfo.builder()
                        .level(level)
                        .requiredExp(requiredExp)
                        .benefits(Arrays.asList(
                            String.format("일일 최대 채굴량 %s KORI", dailyMaxMining.stripTrailingZeros().toPlainString())
                        ))
                        .build());
                }
                
                return LevelGuideResponseDto.builder()
                    .title("레벨 가이드")
                    .description("레벨을 올리면 일일 최대 채굴량이 증가합니다.")
                    .levels(levelInfos)
                    .build();
            });
    }
    
    /**
     * 누적 EXP 기준 레벨 계산 (MINING_AND_LEVEL_SPEC)
     */
    public static int levelFromExp(BigDecimal exp) {
        if (exp == null || exp.compareTo(BigDecimal.ZERO) < 0) return 1;
        if (exp.compareTo(LEVEL_EXP[7]) >= 0) return 9;
        if (exp.compareTo(LEVEL_EXP[6]) >= 0) return 8;
        if (exp.compareTo(LEVEL_EXP[5]) >= 0) return 7;
        if (exp.compareTo(LEVEL_EXP[4]) >= 0) return 6;
        if (exp.compareTo(LEVEL_EXP[3]) >= 0) return 5;
        if (exp.compareTo(LEVEL_EXP[2]) >= 0) return 4;
        if (exp.compareTo(LEVEL_EXP[1]) >= 0) return 3;
        if (exp.compareTo(LEVEL_EXP[0]) >= 0) return 2;
        return 1;
    }
    
    /**
     * 사용자 누적 EXP에 맞춰 레벨 동기화 (채굴 EXP 반영 후 호출)
     */
    public Future<com.foxya.coin.user.entities.User> syncLevelFromExp(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) return Future.succeededFuture(null);
                BigDecimal exp = user.getExp() != null ? user.getExp() : BigDecimal.ZERO;
                int computedLevel = levelFromExp(exp);
                int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                if (computedLevel > currentLevel) {
                    return userRepository.updateLevel(pool, userId, computedLevel)
                        .map(u -> u);
                }
                return Future.succeededFuture(user);
            });
    }
}

