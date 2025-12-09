package com.foxya.coin.mining;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MiningHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<DailyLimitResponseDto>> refDailyLimit = new TypeReference<>() {};
    private final TypeReference<ApiResponse<LevelInfoResponseDto>> refLevelInfo = new TypeReference<>() {};
    
    public MiningHandlerTest() {
        super("/api/v1/mining");
    }
    
    @Nested
    @DisplayName("일일 최대 채굴량 조회 테스트")
    class GetDailyLimitTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 일일 최대 채굴량 조회")
        void successGetDailyLimit(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/daily-limit"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get daily limit response: {}", res.bodyAsJsonObject());
                    DailyLimitResponseDto response = expectSuccessAndGetResponse(res, refDailyLimit);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getCurrentLevel()).isNotNull();
                    assertThat(response.getDailyMaxMining()).isNotNull();
                    assertThat(response.getTodayMiningAmount()).isNotNull();
                    assertThat(response.getResetAt()).isNotNull();
                    assertThat(response.getIsLimitReached()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/daily-limit"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("레벨별 일일 최대 채굴량 정보 조회 테스트")
    class GetLevelInfoTest {
        
        @Test
        @Order(3)
        @DisplayName("성공 - 레벨별 일일 최대 채굴량 정보 조회")
        void successGetLevelInfo(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/level-info"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get level info response: {}", res.bodyAsJsonObject());
                    LevelInfoResponseDto response = expectSuccessAndGetResponse(res, refLevelInfo);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getLevels()).isNotNull();
                    assertThat(response.getLevels().size()).isGreaterThan(0);
                    
                    // 레벨 1부터 9까지 확인
                    assertThat(response.getLevels().get(0).getLevel()).isEqualTo(1);
                    assertThat(response.getLevels().get(0).getDailyMaxMining()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/level-info"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

