package com.foxya.coin.mission;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.mission.dto.MissionResponseDto;
import com.foxya.coin.mission.entities.Mission;
import com.foxya.coin.mission.entities.UserMission;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MissionService extends BaseService {
    
    private final MissionRepository missionRepository;
    
    public MissionService(PgPool pool, MissionRepository missionRepository) {
        super(pool);
        this.missionRepository = missionRepository;
    }
    
    /**
     * 오늘의 미션 목록 조회
     */
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
            
            // userMissions를 missionId로 매핑
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
    
    /**
     * 미션 완료 처리
     */
    public Future<Void> completeMission(Long userId, Long missionId) {
        LocalDate today = LocalDate.now();
        
        // 미션 존재 확인
        return missionRepository.getActiveMissions(pool)
            .compose(missions -> {
                Mission mission = missions.stream()
                    .filter(m -> m.getId().equals(missionId))
                    .findFirst()
                    .orElse(null);
                
                if (mission == null) {
                    return Future.failedFuture(new NotFoundException("미션을 찾을 수 없습니다."));
                }
                
                // 현재 진행 상황 확인
                return missionRepository.getUserMission(pool, userId, missionId, today)
                    .compose(userMission -> {
                        Integer currentCount = userMission != null ? userMission.getCurrentCount() : 0;
                        
                        // 이미 완료된 경우
                        if (currentCount >= mission.getRequiredCount()) {
                            return Future.failedFuture(new BadRequestException("미션을 완료할 수 없습니다."));
                        }
                        
                        // 미션 카운트 증가
                        return missionRepository.incrementMissionCount(pool, userId, missionId, today)
                            .compose(success -> {
                                if (!success) {
                                    return Future.failedFuture(new BadRequestException("미션을 완료할 수 없습니다."));
                                }
                                return Future.succeededFuture();
                            })
                            .mapEmpty();
                    });
            });
    }
}

