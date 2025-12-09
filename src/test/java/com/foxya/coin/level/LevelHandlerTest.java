package com.foxya.coin.level;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.level.dto.UserLevelResponseDto;
import com.foxya.coin.level.dto.LevelGuideResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LevelHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<UserLevelResponseDto>> refUserLevel = new TypeReference<>() {};
    private final TypeReference<ApiResponse<LevelGuideResponseDto>> refLevelGuide = new TypeReference<>() {};
    
    public LevelHandlerTest() {
        super("/api/v1/user");
    }
    
    @Nested
    @DisplayName("사용자 레벨 및 경험치 정보 조회 테스트")
    class GetUserLevelTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 사용자 레벨 및 경험치 정보 조회")
        void successGetUserLevel(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/level"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get user level response: {}", res.bodyAsJsonObject());
                    UserLevelResponseDto response = expectSuccessAndGetResponse(res, refUserLevel);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getCurrentLevel()).isNotNull();
                    assertThat(response.getCurrentExp()).isNotNull();
                    assertThat(response.getNextLevelExp()).isNotNull();
                    assertThat(response.getProgress()).isNotNull();
                    assertThat(response.getProgress()).isBetween(0.0, 1.0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/level"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("레벨 가이드 정보 조회 테스트")
    class GetLevelGuideTest {
        
        @Test
        @Order(3)
        @DisplayName("성공 - 레벨 가이드 정보 조회")
        void successGetLevelGuide(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/level-guide"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get level guide response: {}", res.bodyAsJsonObject());
                    LevelGuideResponseDto response = expectSuccessAndGetResponse(res, refLevelGuide);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTitle()).isNotNull();
                    assertThat(response.getDescription()).isNotNull();
                    assertThat(response.getLevels()).isNotNull();
                    assertThat(response.getLevels().size()).isGreaterThan(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/level-guide"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

