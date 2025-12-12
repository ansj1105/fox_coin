package com.foxya.coin.mining;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.RankingPeriod;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import com.foxya.coin.mining.dto.MiningHistoryResponseDto;
import com.foxya.coin.mining.entities.DailyMining;
import com.foxya.coin.mining.entities.MiningHistory;
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
}

