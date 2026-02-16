package com.foxya.coin.level;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.level.dto.LevelGuideResponseDto;
import com.foxya.coin.level.dto.UserLevelResponseDto;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
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
    private final NotificationService notificationService;

    private static final BigDecimal[] LEVEL_EXP = {
        BigDecimal.valueOf(5),
        BigDecimal.valueOf(15),
        BigDecimal.valueOf(35),
        BigDecimal.valueOf(70),
        BigDecimal.valueOf(130),
        BigDecimal.valueOf(220),
        BigDecimal.valueOf(350),
        BigDecimal.valueOf(520)
    };

    public LevelService(PgPool pool, UserRepository userRepository, MiningRepository miningRepository) {
        this(pool, userRepository, miningRepository, null);
    }

    public LevelService(PgPool pool, UserRepository userRepository, MiningRepository miningRepository,
                        NotificationService notificationService) {
        super(pool);
        this.userRepository = userRepository;
        this.miningRepository = miningRepository;
        this.notificationService = notificationService;
    }

    public Future<UserLevelResponseDto> getUserLevel(Long userId) {
        return userRepository.getUserById(pool, userId)
            .map(user -> {
                if (user == null) {
                    throw new com.foxya.coin.common.exceptions.NotFoundException("User not found.");
                }

                Integer currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                BigDecimal currentExp = user.getExp() != null ? user.getExp() : BigDecimal.ZERO;

                BigDecimal nextLevelExp = currentLevel >= 9 ? LEVEL_EXP[7] : LEVEL_EXP[currentLevel - 1];
                BigDecimal currentLevelExp = currentLevel > 1 ? LEVEL_EXP[currentLevel - 2] : BigDecimal.ZERO;

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
                int maxLevels = Math.min(9, levels.size());
                for (int i = 0; i < maxLevels; i++) {
                    int level = i + 1;
                    BigDecimal requiredExp = (i == 0) ? BigDecimal.ZERO : LEVEL_EXP[i - 1];
                    BigDecimal dailyMaxMining = levels.get(i).getDailyMaxMining();
                    Integer dailyMaxVideos = levels.get(i).getDailyMaxVideos();

                    String benefits = String.format("Daily max mining %s KORI", dailyMaxMining.stripTrailingZeros().toPlainString());
                    if (dailyMaxVideos != null && dailyMaxVideos > 0) {
                        benefits += String.format(", daily videos %d", dailyMaxVideos);
                    }

                    levelInfos.add(LevelGuideResponseDto.LevelInfo.builder()
                        .level(level)
                        .requiredExp(requiredExp)
                        .benefits(Arrays.asList(benefits))
                        .build());
                }

                return LevelGuideResponseDto.builder()
                    .title("Level Guide")
                    .description("Leveling up increases daily max mining amount.")
                    .levels(levelInfos)
                    .build();
            });
    }

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

    public Future<User> syncLevelFromExp(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) return Future.succeededFuture(null);

                BigDecimal exp = user.getExp() != null ? user.getExp() : BigDecimal.ZERO;
                int computedLevel = levelFromExp(exp);
                int currentLevel = user.getLevel() != null ? user.getLevel() : 1;

                if (computedLevel > currentLevel) {
                    return userRepository.updateLevel(pool, userId, computedLevel)
                        .compose(updated -> createLevelUpNotification(userId, currentLevel, computedLevel)
                            .map(v -> updated != null ? updated : user));
                }
                return Future.succeededFuture(user);
            });
    }

    private Future<Void> createLevelUpNotification(Long userId, int previousLevel, int newLevel) {
        if (notificationService == null) {
            return Future.<Void>succeededFuture();
        }

        JsonObject metadata = new JsonObject()
            .put("previousLevel", previousLevel)
            .put("newLevel", newLevel);

        return notificationService.createNotificationIfAbsentByRelatedId(
                userId,
                NotificationType.LEVEL_UP,
                "\uB808\uBCA8 \uC0C1\uC2B9",
                newLevel + "\uB808\uBCA8\uB85C \uC0C1\uC2B9\uD588\uC2B5\uB2C8\uB2E4.",
                (long) newLevel,
                metadata.encode())
            .map(v -> (Void) null)
            .recover(err -> {
                log.warn("Level up notification failed (ignored): userId={}, level={}", userId, newLevel, err);
                return Future.<Void>succeededFuture();
            });
    }
}

