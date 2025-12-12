package com.foxya.coin.deposit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.deposit.dto.TokenDepositListResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenDepositHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<TokenDepositListResponseDto>> refTokenDepositList = new TypeReference<>() {};
    
    // 테스트용 사용자 ID
    private static final Long TESTUSER_ID = 1L;
    
    public TokenDepositHandlerTest() {
        super("/api/v1/deposits");
    }
    
    @Nested
    @DisplayName("토큰 입금 목록 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetTokenDepositsTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 토큰 입금 목록 조회")
        void successGetTokenDeposits(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            reqGet(getUrl("/"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Token deposits response: {}", res.bodyAsJsonObject());
                    TokenDepositListResponseDto response = expectSuccessAndGetResponse(res, refTokenDepositList);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getDeposits()).isNotNull();
                    assertThat(response.getTotal()).isNotNull();
                    assertThat(response.getLimit()).isNotNull();
                    assertThat(response.getOffset()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 통화 필터로 토큰 입금 목록 조회")
        void successGetTokenDepositsWithCurrency(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            reqGet(getUrl("/?currencyCode=ETH"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    TokenDepositListResponseDto response = expectSuccessAndGetResponse(res, refTokenDepositList);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getDeposits()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

