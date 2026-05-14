package com.foxya.coin.level;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.level.dto.LevelGuideResponseDto;
import com.foxya.coin.level.dto.UserLevelResponseDto;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.mining.entities.MiningLevel;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationI18nUtils;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LevelService extends BaseService {

    private final UserRepository userRepository;
    private final MiningRepository miningRepository;
    private final NotificationService notificationService;

    private static final BigDecimal[] REQUIRED_EXP_BY_LEVEL = {
        BigDecimal.valueOf(0), BigDecimal.valueOf(4), BigDecimal.valueOf(9), BigDecimal.valueOf(13), BigDecimal.valueOf(18),
        BigDecimal.valueOf(24), BigDecimal.valueOf(29), BigDecimal.valueOf(35), BigDecimal.valueOf(42), BigDecimal.valueOf(48),
        BigDecimal.valueOf(55), BigDecimal.valueOf(62), BigDecimal.valueOf(70), BigDecimal.valueOf(77), BigDecimal.valueOf(85),
        BigDecimal.valueOf(94), BigDecimal.valueOf(102), BigDecimal.valueOf(111), BigDecimal.valueOf(121), BigDecimal.valueOf(130),
        BigDecimal.valueOf(140), BigDecimal.valueOf(151), BigDecimal.valueOf(161), BigDecimal.valueOf(172), BigDecimal.valueOf(184),
        BigDecimal.valueOf(195), BigDecimal.valueOf(207), BigDecimal.valueOf(220), BigDecimal.valueOf(232), BigDecimal.valueOf(245),
        BigDecimal.valueOf(260), BigDecimal.valueOf(274), BigDecimal.valueOf(288), BigDecimal.valueOf(303), BigDecimal.valueOf(318),
        BigDecimal.valueOf(334), BigDecimal.valueOf(349), BigDecimal.valueOf(365), BigDecimal.valueOf(382), BigDecimal.valueOf(398),
        BigDecimal.valueOf(416), BigDecimal.valueOf(433), BigDecimal.valueOf(450), BigDecimal.valueOf(469), BigDecimal.valueOf(487),
        BigDecimal.valueOf(506), BigDecimal.valueOf(525), BigDecimal.valueOf(544), BigDecimal.valueOf(564), BigDecimal.valueOf(584),
        BigDecimal.valueOf(605), BigDecimal.valueOf(626), BigDecimal.valueOf(647), BigDecimal.valueOf(669), BigDecimal.valueOf(691),
        BigDecimal.valueOf(714), BigDecimal.valueOf(736), BigDecimal.valueOf(760), BigDecimal.valueOf(783), BigDecimal.valueOf(807),
        BigDecimal.valueOf(832), BigDecimal.valueOf(856), BigDecimal.valueOf(881), BigDecimal.valueOf(907), BigDecimal.valueOf(932),
        BigDecimal.valueOf(958), BigDecimal.valueOf(985), BigDecimal.valueOf(1011), BigDecimal.valueOf(1038), BigDecimal.valueOf(1066),
        BigDecimal.valueOf(1093), BigDecimal.valueOf(1121), BigDecimal.valueOf(1150), BigDecimal.valueOf(1178), BigDecimal.valueOf(1207),
        BigDecimal.valueOf(1237), BigDecimal.valueOf(1266), BigDecimal.valueOf(1296), BigDecimal.valueOf(1327), BigDecimal.valueOf(1357),
        BigDecimal.valueOf(1388), BigDecimal.valueOf(1420), BigDecimal.valueOf(1451), BigDecimal.valueOf(1483), BigDecimal.valueOf(1516),
        BigDecimal.valueOf(1548), BigDecimal.valueOf(1581), BigDecimal.valueOf(1615), BigDecimal.valueOf(1648), BigDecimal.valueOf(1682),
        BigDecimal.valueOf(1717), BigDecimal.valueOf(1751), BigDecimal.valueOf(1786), BigDecimal.valueOf(1822), BigDecimal.valueOf(1857),
        BigDecimal.valueOf(1893), BigDecimal.valueOf(1930), BigDecimal.valueOf(1966), BigDecimal.valueOf(2003), BigDecimal.valueOf(2040)
    };
    private static final int MAX_LEVEL = REQUIRED_EXP_BY_LEVEL.length;
    private static final String LEVEL_UP_NOTICE_TITLE = "Level Up";
    private static final String LEVEL_UP_NOTICE_MESSAGE = "You have reached level %d.";
    private static final String LEVEL_UP_NOTICE_TITLE_KEY = "notifications.levelUp.title";
    private static final String LEVEL_UP_NOTICE_MESSAGE_KEY = "notifications.levelUp.message";

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

                BigDecimal nextLevelExp = getRequiredExpForNextLevel(currentLevel);
                BigDecimal currentLevelExp = getRequiredExpForLevel(currentLevel);

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
                for (MiningLevel miningLevel : levels) {
                    BigDecimal dailyMaxMining = miningLevel.getDailyMaxMining();
                    Integer dailyMaxAds = miningLevel.getDailyMaxAds() != null && miningLevel.getDailyMaxAds() > 0
                        ? miningLevel.getDailyMaxAds()
                        : miningLevel.getDailyMaxVideos();

                    List<String> benefits = new ArrayList<>();
                    benefits.add(String.format("Daily max mining %s KORI", dailyMaxMining.stripTrailingZeros().toPlainString()));
                    if (dailyMaxAds != null && dailyMaxAds > 0) {
                        benefits.add(String.format("Daily ads %d", dailyMaxAds));
                    }
                    if (miningLevel.getStoreProductLimit() != null) {
                        benefits.add(String.format("Offline Pay store products %d", miningLevel.getStoreProductLimit()));
                    }
                    if (miningLevel.getLevel() != null && miningLevel.getLevel() >= 2) {
                        benefits.add("Profile photo available");
                    }

                    levelInfos.add(LevelGuideResponseDto.LevelInfo.builder()
                        .level(miningLevel.getLevel())
                        .requiredExp(miningLevel.getRequiredExp())
                        .dailyMaxMining(dailyMaxMining)
                        .efficiency(miningLevel.getEfficiency())
                        .perMinuteMining(miningLevel.getPerMinuteMining())
                        .expectedDays(miningLevel.getExpectedDays())
                        .dailyMaxAds(miningLevel.getDailyMaxAds())
                        .storeProductLimit(miningLevel.getStoreProductLimit())
                        .badgeCode(miningLevel.getBadgeCode())
                        .photoUrl(miningLevel.getPhotoUrl())
                        .benefits(benefits)
                        .build());
                }

                return LevelGuideResponseDto.builder()
                    .title("Level Guide")
                    .description("Leveling up increases daily max mining amount.")
                    .levels(levelInfos)
                    .build();
            });
    }

    public Future<JsonObject> getOfflinePayStorePolicy(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    throw new com.foxya.coin.common.exceptions.NotFoundException("User not found.");
                }

                int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
                return miningRepository.getMiningLevelByLevel(pool, currentLevel)
                    .map(miningLevel -> {
                        int storeProductLimit = miningLevel != null && miningLevel.getStoreProductLimit() != null
                            ? miningLevel.getStoreProductLimit()
                            : Math.max(0, currentLevel - 1);
                        int dailyMaxAds = miningLevel != null && miningLevel.getDailyMaxAds() != null
                            ? miningLevel.getDailyMaxAds()
                            : ((currentLevel - 1) / 5) + 5;
                        return new JsonObject()
                            .put("userId", userId)
                            .put("level", currentLevel)
                            .put("storeProductLimit", storeProductLimit)
                            .put("dailyMaxAds", dailyMaxAds)
                            .put("profilePhotoEnabled", currentLevel >= 2);
                    });
            });
    }

    public static int levelFromExp(BigDecimal exp) {
        if (exp == null || exp.compareTo(BigDecimal.ZERO) < 0) return 1;
        for (int level = MAX_LEVEL; level >= 1; level--) {
            if (exp.compareTo(getRequiredExpForLevel(level)) >= 0) {
                return level;
            }
        }
        return 1;
    }

    public static BigDecimal getRequiredExpForLevel(int level) {
        int safeLevel = Math.max(1, Math.min(level, MAX_LEVEL));
        return REQUIRED_EXP_BY_LEVEL[safeLevel - 1];
    }

    public static BigDecimal getRequiredExpForNextLevel(int currentLevel) {
        if (currentLevel >= MAX_LEVEL) {
            return getRequiredExpForLevel(MAX_LEVEL);
        }
        return getRequiredExpForLevel(currentLevel + 1);
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

    /**
     * 레벨 동기화 배치:
     * - EXP 기준 계산 레벨이 users.level 보다 높은 사용자만 대상으로 보정
     * - 연속 ID cursor 방식으로 한 번의 실행에서 누락 유저를 끝까지 처리
     */
    public Future<Integer> runLevelSyncBatch(int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 2000));
        return runLevelSyncBatchFromCursor(0L, safeBatchSize, 0);
    }

    private Future<Integer> runLevelSyncBatchFromCursor(Long afterUserId, int batchSize, int accumulatedUpdated) {
        return userRepository.findUsersRequiringLevelSync(pool, afterUserId, batchSize)
            .compose(candidates -> {
                if (candidates == null || candidates.isEmpty()) {
                    return Future.succeededFuture(accumulatedUpdated);
                }

                final long[] nextCursor = {afterUserId};
                final int[] updatedCount = {accumulatedUpdated};

                Future<Void> chain = Future.succeededFuture();
                for (UserRepository.LevelSyncCandidate candidate : candidates) {
                    nextCursor[0] = candidate.getUserId();
                    chain = chain.compose(ignored -> userRepository.updateLevel(pool, candidate.getUserId(), candidate.getComputedLevel())
                        .compose(updated -> createLevelUpNotification(
                            candidate.getUserId(),
                            candidate.getCurrentLevel(),
                            candidate.getComputedLevel()))
                        .map(v -> {
                            updatedCount[0]++;
                            return (Void) null;
                        })
                        .recover(err -> {
                            log.warn("Level sync failed for userId={} (ignored)", candidate.getUserId(), err);
                            return Future.<Void>succeededFuture();
                        }));
                }

                return chain.compose(v -> runLevelSyncBatchFromCursor(nextCursor[0], batchSize, updatedCount[0]));
            });
    }

    private Future<Void> createLevelUpNotification(Long userId, int previousLevel, int newLevel) {
        if (notificationService == null) {
            return Future.<Void>succeededFuture();
        }

        JsonObject metadataVariables = new JsonObject()
            .put("previousLevel", previousLevel)
            .put("newLevel", newLevel);
        String metadata = NotificationI18nUtils.buildMetadata(
            LEVEL_UP_NOTICE_TITLE_KEY,
            LEVEL_UP_NOTICE_MESSAGE_KEY,
            metadataVariables
        );

        return notificationService.createNotificationIfAbsentByRelatedId(
                userId,
                NotificationType.LEVEL_UP,
                LEVEL_UP_NOTICE_TITLE,
                String.format(LEVEL_UP_NOTICE_MESSAGE, newLevel),
                (long) newLevel,
                metadata)
            .<Void>mapEmpty()
            .recover(err -> {
                log.warn("Level up notification failed (ignored): userId={}, level={}", userId, newLevel, err);
                return Future.<Void>succeededFuture();
            });
    }
}
