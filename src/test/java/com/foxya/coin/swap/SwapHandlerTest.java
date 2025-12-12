package com.foxya.coin.swap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.swap.dto.SwapResponseDto;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SwapHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<SwapResponseDto>> refSwap = new TypeReference<>() {};
    
    // 테스트용 사용자 ID
    private static final Long TESTUSER_ID = 1L;
    
    public SwapHandlerTest() {
        super("/api/v1/swap");
    }
    
    @Nested
    @DisplayName("스왑 실행 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExecuteSwapTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 스왑 실행")
        void successExecuteSwap(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject requestBody = new JsonObject()
                .put("fromCurrencyCode", "ETH")
                .put("toCurrencyCode", "USDT")
                .put("fromAmount", 0.1)
                .put("network", "Ether");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(requestBody)
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Swap response: {}", res.bodyAsJsonObject());
                    SwapResponseDto response = expectSuccessAndGetResponse(res, refSwap);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getSwapId()).isNotNull();
                    assertThat(response.getOrderNumber()).isNotNull();
                    assertThat(response.getFromCurrencyCode()).isEqualTo("ETH");
                    assertThat(response.getToCurrencyCode()).isEqualTo("USDT");
                    assertThat(response.getFromAmount()).isNotNull();
                    assertThat(response.getToAmount()).isNotNull();
                    assertThat(response.getNetwork()).isEqualTo("Ether");
                    assertThat(response.getStatus()).isEqualTo("COMPLETED");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 실행")
        void failNoAuth(VertxTestContext tc) {
            JsonObject requestBody = new JsonObject()
                .put("fromCurrencyCode", "ETH")
                .put("toCurrencyCode", "USDT")
                .put("fromAmount", 0.1)
                .put("network", "Ether");
            
            reqPost(getUrl("/"))
                .sendJson(requestBody)
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 같은 통화로 스왑")
        void failSameCurrency(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject requestBody = new JsonObject()
                .put("fromCurrencyCode", "ETH")
                .put("toCurrencyCode", "ETH")
                .put("fromAmount", 0.1)
                .put("network", "Ether");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(requestBody)
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("스왑 상세 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetSwapTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 스왑 상세 조회")
        void successGetSwap(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            // 먼저 스왑 실행
            JsonObject requestBody = new JsonObject()
                .put("fromCurrencyCode", "ETH")
                .put("toCurrencyCode", "USDT")
                .put("fromAmount", 0.1)
                .put("network", "Ether");
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(requestBody)
                .onComplete(tc.succeeding(createRes -> tc.verify(() -> {
                    SwapResponseDto created = expectSuccessAndGetResponse(createRes, refSwap);
                    
                    // 스왑 상세 조회
                    reqGet(getUrl("/" + created.getSwapId()))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            SwapResponseDto response = expectSuccessAndGetResponse(res, refSwap);
                            
                            assertThat(response).isNotNull();
                            assertThat(response.getSwapId()).isEqualTo(created.getSwapId());
                            assertThat(response.getOrderNumber()).isEqualTo(created.getOrderNumber());
                            
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 스왑 조회")
        void failNotFound(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            reqGet(getUrl("/non-existent-swap-id"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }
    }
}

