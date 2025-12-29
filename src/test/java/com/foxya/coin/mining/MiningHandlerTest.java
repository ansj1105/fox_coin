package com.foxya.coin.mining;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.mining.dto.DailyLimitResponseDto;
import com.foxya.coin.mining.dto.LevelInfoResponseDto;
import com.foxya.coin.mining.dto.MiningHistoryResponseDto;
import com.foxya.coin.mining.dto.MiningInfoResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MiningHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<DailyLimitResponseDto>> refDailyLimit = new TypeReference<>() {};
    private final TypeReference<ApiResponse<LevelInfoResponseDto>> refLevelInfo = new TypeReference<>() {};
    private final TypeReference<ApiResponse<MiningHistoryResponseDto>> refMiningHistory = new TypeReference<>() {};
    private final TypeReference<ApiResponse<MiningInfoResponseDto>> refMiningInfo = new TypeReference<>() {};
    
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
    
    @Nested
    @DisplayName("채굴 내역 조회 테스트")
    class GetMiningHistoryTest {
        
        @Test
        @Order(5)
        @DisplayName("성공 - 채굴 내역 조회 (ALL)")
        void successGetMiningHistoryAll(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/history?period=ALL&limit=20&offset=0"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get mining history response: {}", res.bodyAsJsonObject());
                    MiningHistoryResponseDto response = expectSuccessAndGetResponse(res, refMiningHistory);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getItems()).isNotNull();
                    assertThat(response.getTotal()).isNotNull();
                    assertThat(response.getTotalAmount()).isNotNull();
                    assertThat(response.getLimit()).isEqualTo(20);
                    assertThat(response.getOffset()).isEqualTo(0);
                    
                    if (response.getItems().size() > 0) {
                        MiningHistoryResponseDto.MiningHistoryItem item = response.getItems().get(0);
                        assertThat(item.getId()).isNotNull();
                        assertThat(item.getLevel()).isNotNull();
                        assertThat(item.getNickname()).isNotNull();
                        assertThat(item.getAmount()).isNotNull();
                        assertThat(item.getType()).isIn("BROADCAST_PROGRESS", "BROADCAST_WATCH");
                        assertThat(item.getStatus()).isEqualTo("COMPLETED");
                        assertThat(item.getCreatedAt()).isNotNull();
                    }
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(6)
        @DisplayName("성공 - 채굴 내역 조회 (TODAY)")
        void successGetMiningHistoryToday(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/history?period=TODAY"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get mining history (TODAY) response: {}", res.bodyAsJsonObject());
                    MiningHistoryResponseDto response = expectSuccessAndGetResponse(res, refMiningHistory);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getItems()).isNotNull();
                    assertThat(response.getTotal()).isNotNull();
                    assertThat(response.getTotalAmount()).isNotNull();
                    assertThat(response.getLimit()).isEqualTo(20); // 기본값
                    assertThat(response.getOffset()).isEqualTo(0); // 기본값
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(7)
        @DisplayName("성공 - 채굴 내역 조회 (WEEK)")
        void successGetMiningHistoryWeek(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/history?period=WEEK&limit=10&offset=0"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get mining history (WEEK) response: {}", res.bodyAsJsonObject());
                    MiningHistoryResponseDto response = expectSuccessAndGetResponse(res, refMiningHistory);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getItems()).isNotNull();
                    assertThat(response.getTotal()).isNotNull();
                    assertThat(response.getTotalAmount()).isNotNull();
                    assertThat(response.getLimit()).isEqualTo(10);
                    assertThat(response.getOffset()).isEqualTo(0);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(8)
        @DisplayName("성공 - 채굴 내역 조회 (기본값)")
        void successGetMiningHistoryDefault(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/history"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get mining history (default) response: {}", res.bodyAsJsonObject());
                    MiningHistoryResponseDto response = expectSuccessAndGetResponse(res, refMiningHistory);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getItems()).isNotNull();
                    assertThat(response.getTotal()).isNotNull();
                    assertThat(response.getTotalAmount()).isNotNull();
                    assertThat(response.getLimit()).isEqualTo(20); // 기본값
                    assertThat(response.getOffset()).isEqualTo(0); // 기본값
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(9)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/history"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("채굴 정보 조회 테스트")
    class GetMiningInfoTest {
        
        @Test
        @Order(10)
        @DisplayName("성공 - 채굴 정보 조회")
        void successGetMiningInfo(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/info"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get mining info response: {}", res.bodyAsJsonObject());
                    MiningInfoResponseDto response = expectSuccessAndGetResponse(res, refMiningInfo);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getTodayMiningAmount()).isNotNull();
                    assertThat(response.getTotalBalance()).isNotNull();
                    assertThat(response.getBonusEfficiency()).isNotNull();
                    assertThat(response.getRemainingTime()).isNotNull();
                    assertThat(response.getRemainingTime()).matches("\\d{2}:\\d{2}:\\d{2}"); // HH:MM:SS 형식
                    assertThat(response.getIsActive()).isNotNull();
                    assertThat(response.getDailyMaxMining()).isNotNull();
                    assertThat(response.getCurrentLevel()).isNotNull();
                    assertThat(response.getNextLevelRequired()).isNotNull();
                    assertThat(response.getAdWatchCount()).isNotNull();
                    assertThat(response.getMaxAdWatchCount()).isNotNull();
                    
                    // 값 검증
                    assertThat(response.getTodayMiningAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                    assertThat(response.getTotalBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                    assertThat(response.getBonusEfficiency()).isGreaterThanOrEqualTo(0);
                    assertThat(response.getCurrentLevel()).isGreaterThanOrEqualTo(1);
                    assertThat(response.getAdWatchCount()).isGreaterThanOrEqualTo(0);
                    assertThat(response.getMaxAdWatchCount()).isGreaterThan(0);
                    assertThat(response.getAdWatchCount()).isLessThanOrEqualTo(response.getMaxAdWatchCount());
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(11)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/info"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("광고 시청 테스트")
    class WatchAdTest {
        
        @Test
        @Order(12)
        @DisplayName("성공 - 광고 시청")
        void successWatchAd(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqPost(getUrl("/watch-ad"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Watch ad response: {}", res.bodyAsJsonObject());
                    expectSuccess(res);
                    
                    // 광고 시청 후 채굴 정보 조회하여 adWatchCount 증가 확인
                    reqGet(getUrl("/info"))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(infoRes -> tc.verify(() -> {
                            MiningInfoResponseDto info = expectSuccessAndGetResponse(infoRes, refMiningInfo);
                            assertThat(info.getAdWatchCount()).isGreaterThanOrEqualTo(1);
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(13)
        @DisplayName("실패 - 인증 없이 광고 시청")
        void failNoAuth(VertxTestContext tc) {
            reqPost(getUrl("/watch-ad"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

