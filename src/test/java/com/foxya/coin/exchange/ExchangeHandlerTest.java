package com.foxya.coin.exchange;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.exchange.dto.ExchangeResponseDto;
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
public class ExchangeHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<ExchangeResponseDto>> refExchange = new TypeReference<>() {};
    
    // 테스트용 사용자 ID
    private static final Long TESTUSER_ID = 1L;
    
    public ExchangeHandlerTest() {
        super("/api/v1/exchange");
    }
    
    @Nested
    @DisplayName("환전 실행 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExecuteExchangeTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 환전 실행")
        void successExecuteExchange(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("fromAmount", 100.0);
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Exchange response: {}", res.bodyAsJsonObject());
                    ExchangeResponseDto response = expectSuccessAndGetResponse(res, refExchange);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getExchangeId()).isNotNull();
                    assertThat(response.getOrderNumber()).isNotNull();
                    assertThat(response.getFromCurrencyCode()).isEqualTo("KRWT");
                    assertThat(response.getToCurrencyCode()).isEqualTo("BLUEDIA");
                    assertThat(response.getFromAmount()).isNotNull();
                    assertThat(response.getToAmount()).isNotNull();
                    assertThat(response.getStatus()).isEqualTo("COMPLETED");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 실행")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("fromAmount", 100.0);
            
            reqPost(getUrl("/"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 최소 금액 미만")
        void failMinAmount(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("fromAmount", 0.5);
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("환전 상세 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetExchangeTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 환전 상세 조회")
        void successGetExchange(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            // 먼저 환전 실행
            JsonObject data = new JsonObject()
                .put("fromAmount", 100.0);
            
            reqPost(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(createRes -> tc.verify(() -> {
                    ExchangeResponseDto created = expectSuccessAndGetResponse(createRes, refExchange);
                    
                    // 환전 상세 조회
                    reqGet(getUrl("/" + created.getExchangeId()))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            ExchangeResponseDto response = expectSuccessAndGetResponse(res, refExchange);
                            
                            assertThat(response).isNotNull();
                            assertThat(response.getExchangeId()).isEqualTo(created.getExchangeId());
                            assertThat(response.getOrderNumber()).isEqualTo(created.getOrderNumber());
                            
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 환전 조회")
        void failNotFound(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            reqGet(getUrl("/non-existent-exchange-id"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }
    }
}

