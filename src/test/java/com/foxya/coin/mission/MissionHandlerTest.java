package com.foxya.coin.mission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.mission.dto.MissionResponseDto;
import com.foxya.coin.mission.enums.MissionType;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MissionHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<List<MissionResponseDto>>> refMissionList = new TypeReference<>() {};
    private final TypeReference<ApiResponse<Void>> refVoid = new TypeReference<>() {};
    
    public MissionHandlerTest() {
        super("/api/v1/missions");
    }
    
    @Nested
    @DisplayName("오늘의 미션 목록 조회 테스트")
    class GetTodayMissionsTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 오늘의 미션 목록 조회")
        void successGetTodayMissions(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get today missions response: {}", res.bodyAsJsonObject());
                    List<MissionResponseDto> missions = expectSuccessAndGetResponse(res, refMissionList);
                    
                    assertThat(missions).isNotNull();
                    assertThat(missions.size()).isGreaterThanOrEqualTo(5); // 기본 5개 미션
                    
                    // 각 미션 검증
                    for (MissionResponseDto mission : missions) {
                        assertThat(mission.getId()).isNotNull();
                        assertThat(mission.getTitle()).isNotNull();
                        assertThat(mission.getDescription()).isNotNull();
                        assertThat(mission.getCurrentCount()).isNotNull();
                        assertThat(mission.getRequiredCount()).isNotNull();
                        assertThat(mission.getIsCompleted()).isNotNull();
                        assertThat(mission.getType()).isNotNull();
                        
                        // 완료 여부 검증
                        boolean expectedCompleted = mission.getCurrentCount() >= mission.getRequiredCount();
                        assertThat(mission.getIsCompleted()).isEqualTo(expectedCompleted);
                    }
                    
                    // 특정 미션 타입 확인
                    boolean hasCampaign = missions.stream()
                        .anyMatch(m -> m.getType() == MissionType.CAMPAIGN);
                    assertThat(hasCampaign).isTrue();
                    
                    boolean hasDailyCheckin = missions.stream()
                        .anyMatch(m -> m.getType() == MissionType.DAILY_CHECKIN);
                    assertThat(hasDailyCheckin).isTrue();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 미션 완료 후 목록 조회")
        void successGetTodayMissionsAfterComplete(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            // 먼저 미션 목록을 조회하여 첫 번째 미션 ID 가져오기
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res1 -> tc.verify(() -> {
                    List<MissionResponseDto> missions = expectSuccessAndGetResponse(res1, refMissionList);
                    
                    if (!missions.isEmpty()) {
                        Long missionId = missions.get(0).getId();
                        Integer initialCount = missions.get(0).getCurrentCount();
                        Integer requiredCount = missions.get(0).getRequiredCount();
                        
                        // 미션이 아직 완료되지 않은 경우에만 완료 처리
                        if (initialCount < requiredCount) {
                            // 미션 완료 처리
                            reqPost(getUrl("/" + missionId + "/complete"))
                                .bearerTokenAuthentication(accessToken)
                                .send(tc.succeeding(res2 -> tc.verify(() -> {
                                    expectSuccess(res2);
                                    
                                    // 다시 미션 목록 조회하여 카운트 증가 확인
                                    reqGet(getUrl("/"))
                                        .bearerTokenAuthentication(accessToken)
                                        .send(tc.succeeding(res3 -> tc.verify(() -> {
                                            List<MissionResponseDto> updatedMissions = expectSuccessAndGetResponse(res3, refMissionList);
                                            
                                            MissionResponseDto updatedMission = updatedMissions.stream()
                                                .filter(m -> m.getId().equals(missionId))
                                                .findFirst()
                                                .orElse(null);
                                            
                                            assertThat(updatedMission).isNotNull();
                                            assertThat(updatedMission.getCurrentCount()).isEqualTo(initialCount + 1);
                                            
                                            // 완료 여부 확인
                                            if (updatedMission.getCurrentCount() >= updatedMission.getRequiredCount()) {
                                                assertThat(updatedMission.getIsCompleted()).isTrue();
                                            }
                                            
                                            tc.completeNow();
                                        })));
                                })));
                        } else {
                            log.info("Mission already completed, skipping completion test");
                            tc.completeNow();
                        }
                    } else {
                        tc.completeNow();
                    }
                })));
        }
        
        @Test
        @Order(10)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("미션 완료 처리 테스트")
    class CompleteMissionTest {
        
        @Test
        @Order(20)
        @DisplayName("성공 - 미션 완료 처리")
        void successCompleteMission(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            // 먼저 미션 목록을 조회하여 완료되지 않은 미션 찾기
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res1 -> tc.verify(() -> {
                    List<MissionResponseDto> missions = expectSuccessAndGetResponse(res1, refMissionList);
                    
                    // 완료되지 않은 미션 찾기
                    MissionResponseDto incompleteMission = missions.stream()
                        .filter(m -> !m.getIsCompleted())
                        .findFirst()
                        .orElse(null);
                    
                    if (incompleteMission != null) {
                        Long missionId = incompleteMission.getId();
                        Integer initialCount = incompleteMission.getCurrentCount();
                        
                        // 미션 완료 처리
                        reqPost(getUrl("/" + missionId + "/complete"))
                            .bearerTokenAuthentication(accessToken)
                            .send(tc.succeeding(res2 -> tc.verify(() -> {
                                expectSuccess(res2);
                                
                                // 다시 미션 목록 조회하여 카운트 증가 확인
                                reqGet(getUrl("/"))
                                    .bearerTokenAuthentication(accessToken)
                                    .send(tc.succeeding(res3 -> tc.verify(() -> {
                                        List<MissionResponseDto> updatedMissions = expectSuccessAndGetResponse(res3, refMissionList);
                                        
                                        MissionResponseDto updatedMission = updatedMissions.stream()
                                            .filter(m -> m.getId().equals(missionId))
                                            .findFirst()
                                            .orElse(null);
                                        
                                        assertThat(updatedMission).isNotNull();
                                        assertThat(updatedMission.getCurrentCount()).isEqualTo(initialCount + 1);
                                        
                                        tc.completeNow();
                                    })));
                            })));
                    } else {
                        log.info("All missions are completed, skipping completion test");
                        tc.completeNow();
                    }
                })));
        }
        
        @Test
        @Order(21)
        @DisplayName("실패 - 이미 완료된 미션")
        void failAlreadyCompleted(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            // 먼저 미션 목록을 조회하여 완료된 미션 찾기
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res1 -> tc.verify(() -> {
                    List<MissionResponseDto> missions = expectSuccessAndGetResponse(res1, refMissionList);
                    
                    // 완료된 미션 찾기
                    MissionResponseDto completedMission = missions.stream()
                        .filter(MissionResponseDto::getIsCompleted)
                        .findFirst()
                        .orElse(null);
                    
                    if (completedMission != null) {
                        Long missionId = completedMission.getId();
                        
                        // 이미 완료된 미션을 다시 완료 처리 시도
                        reqPost(getUrl("/" + missionId + "/complete"))
                            .bearerTokenAuthentication(accessToken)
                            .send(tc.succeeding(res2 -> tc.verify(() -> {
                                expectError(res2, 400);
                                
                                // 에러 메시지 확인
                                String errorMessage = res2.bodyAsJsonObject().getString("message");
                                assertThat(errorMessage).contains("완료할 수 없습니다");
                                
                                tc.completeNow();
                            })));
                    } else {
                        // 완료된 미션이 없으면 테스트 스킵
                        log.info("No completed missions found, skipping test");
                        tc.completeNow();
                    }
                })));
        }
        
        @Test
        @Order(22)
        @DisplayName("실패 - 존재하지 않는 미션")
        void failMissionNotFound(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqPost(getUrl("/999999/complete"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(23)
        @DisplayName("실패 - 인증 없이 완료 처리")
        void failNoAuth(VertxTestContext tc) {
            reqPost(getUrl("/1/complete"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

