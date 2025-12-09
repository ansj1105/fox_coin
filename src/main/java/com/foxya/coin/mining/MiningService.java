package com.foxya.coin.mining;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.user.UserRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MiningService extends BaseService {
    
    private final MiningRepository miningRepository;
    private final UserRepository userRepository;
    
    public MiningService(PgPool pool, MiningRepository miningRepository, UserRepository userRepository) {
        super(pool);
        this.miningRepository = miningRepository;
        this.userRepository = userRepository;
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
}

