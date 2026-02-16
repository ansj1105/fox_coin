package com.foxya.coin.mission;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.mission.dto.MissionResponseDto;
import com.foxya.coin.mission.entities.Mission;
import com.foxya.coin.mission.entities.UserMission;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MissionService extends BaseService {

    private final MissionRepository missionRepository;
    private final NotificationService notificationService;

    public MissionService(PgPool pool, MissionRepository missionRepository) {
        this(pool, missionRepository, null);
    }

    public MissionService(PgPool pool, MissionRepository missionRepository, NotificationService notificationService) {
        super(pool);
        this.missionRepository = missionRepository;
        this.notificationService = notificationService;
    }

    public Future<List<MissionResponseDto>> getTodayMissions(Long userId) {
        LocalDate today = LocalDate.now();

        return Future.all(
            missionRepository.getActiveMissions(pool),
            missionRepository.getUserMissionsForToday(pool, userId, today)
        ).map(results -> {
            @SuppressWarnings("unchecked")
            List<Mission> missions = (List<Mission>) results.list().get(0);
            @SuppressWarnings("unchecked")
            List<UserMission> userMissions = (List<UserMission>) results.list().get(1);

            Map<Long, UserMission> userMissionMap = userMissions.stream()
                .collect(Collectors.toMap(UserMission::getMissionId, um -> um));

            List<MissionResponseDto> response = new ArrayList<>();
            for (Mission mission : missions) {
                UserMission userMission = userMissionMap.get(mission.getId());
                Integer currentCount = userMission != null ? userMission.getCurrentCount() : 0;
                boolean isCompleted = currentCount >= mission.getRequiredCount();

                response.add(MissionResponseDto.builder()
                    .id(mission.getId())
                    .title(mission.getTitle())
                    .description(mission.getDescription())
                    .currentCount(currentCount)
                    .requiredCount(mission.getRequiredCount())
                    .isCompleted(isCompleted)
                    .type(mission.getType())
                    .build());
            }

            return response;
        });
    }

    public Future<Void> completeMission(Long userId, Long missionId) {
        LocalDate today = LocalDate.now();

        return missionRepository.getActiveMissions(pool)
            .compose(missions -> {
                Mission mission = missions.stream()
                    .filter(m -> m.getId().equals(missionId))
                    .findFirst()
                    .orElse(null);

                if (mission == null) {
                    return Future.failedFuture(new NotFoundException("Mission not found."));
                }

                return missionRepository.getUserMission(pool, userId, missionId, today)
                    .compose(userMission -> {
                        Integer currentCount = userMission != null ? userMission.getCurrentCount() : 0;
                        if (currentCount >= mission.getRequiredCount()) {
                            return Future.failedFuture(new BadRequestException("Mission is already completed."));
                        }

                        return missionRepository.incrementMissionCount(pool, userId, missionId, today)
                            .compose(success -> {
                                if (!success) {
                                    return Future.failedFuture(new BadRequestException("Mission completion failed."));
                                }
                                return createMissionCompletedNotificationIfNeeded(userId, today);
                            })
                            .mapEmpty();
                    });
            });
    }

    private Future<Void> createMissionCompletedNotificationIfNeeded(Long userId, LocalDate today) {
        if (notificationService == null) {
            return Future.<Void>succeededFuture();
        }

        return Future.all(
                missionRepository.getActiveMissions(pool),
                missionRepository.getUserMissionsForToday(pool, userId, today)
            )
            .compose(results -> {
                @SuppressWarnings("unchecked")
                List<Mission> missions = (List<Mission>) results.list().get(0);
                @SuppressWarnings("unchecked")
                List<UserMission> userMissions = (List<UserMission>) results.list().get(1);

                if (missions == null || missions.isEmpty()) {
                    return Future.<Void>succeededFuture();
                }

                Map<Long, Integer> countByMissionId = userMissions.stream()
                    .collect(Collectors.toMap(UserMission::getMissionId, um -> um.getCurrentCount() != null ? um.getCurrentCount() : 0));

                boolean allCompleted = missions.stream()
                    .allMatch(m -> countByMissionId.getOrDefault(m.getId(), 0) >= m.getRequiredCount());

                if (!allCompleted) {
                    return Future.<Void>succeededFuture();
                }

                long dailyKey = today.toEpochDay();
                JsonObject metadata = new JsonObject()
                    .put("missionDate", today.toString())
                    .put("completedMissionCount", missions.size());

                return notificationService.createNotificationIfAbsentByRelatedId(
                        userId,
                        NotificationType.MISSION_COMPLETED,
                        "Daily Missions Completed",
                        "You have completed all daily missions.",
                        dailyKey,
                        metadata.encode())
                    .compose(v -> Future.<Void>succeededFuture());
            })
            .recover(err -> {
                log.warn("Mission completion notification failed (ignored): userId={}, date={}", userId, today, err);
                return Future.<Void>succeededFuture();
            });
    }
}