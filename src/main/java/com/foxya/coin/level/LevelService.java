package com.foxya.coin.level;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.level.dto.LevelGuideResponseDto;
import com.foxya.coin.level.dto.UserLevelResponseDto;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.user.UserRepository;
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
    
    // 레벨별 필요 경험치 (레벨 1은 0, 레벨 2는 1000, ...)
    private static final BigDecimal[] LEVEL_EXP = {
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
                
                // 다음 레벨 필요 경험치 계산
                BigDecimal nextLevelExp;
                if (currentLevel >= 9) {
                    nextLevelExp = LEVEL_EXP[8]; // 최대 레벨
                } else {
                    nextLevelExp = LEVEL_EXP[currentLevel];
                }
                
                // 현재 레벨 기준 경험치
                BigDecimal currentLevelExp = currentLevel > 1 ? LEVEL_EXP[currentLevel - 1] : BigDecimal.ZERO;
                
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
                            String.format("일일 최대 채굴량 %.0f KRC", dailyMaxMining)
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
}

