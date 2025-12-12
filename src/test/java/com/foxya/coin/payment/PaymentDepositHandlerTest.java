package com.foxya.coin.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.payment.dto.PaymentDepositResponseDto;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PaymentDepositHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<PaymentDepositResponseDto>> refPaymentDeposit = new TypeReference<>() {};
    
    // 테스트용 사용자 ID
    private static final Long TESTUSER_ID = 1L;
    
    public PaymentDepositHandlerTest() {
        super("/api/v1/payment");
    }
    
    @Nested
    @DisplayName("결제 입금 요청 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RequestPaymentDepositTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 결제 입금 요청 (카드)")
        void successRequestPaymentDepositCard(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            String requestBody = """
                {
                    "currencyCode": "KRC",
                    "amount": 100.0,
                    "depositMethod": "CARD",
                    "paymentAmount": 10000.0
                }
                """;
            
            reqPost(getUrl("/deposit"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(requestBody)
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Payment deposit response: {}", res.bodyAsJsonObject());
                    PaymentDepositResponseDto response = expectSuccessAndGetResponse(res, refPaymentDeposit);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getDepositId()).isNotNull();
                    assertThat(response.getOrderNumber()).isNotNull();
                    assertThat(response.getCurrencyCode()).isEqualTo("KRC");
                    assertThat(response.getAmount()).isNotNull();
                    assertThat(response.getDepositMethod()).isEqualTo("CARD");
                    assertThat(response.getPaymentAmount()).isNotNull();
                    assertThat(response.getStatus()).isEqualTo("PENDING");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 결제 입금 요청 (계좌이체)")
        void successRequestPaymentDepositBankTransfer(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            String requestBody = """
                {
                    "currencyCode": "KRC",
                    "amount": 200.0,
                    "depositMethod": "BANK_TRANSFER",
                    "paymentAmount": 20000.0
                }
                """;
            
            reqPost(getUrl("/deposit"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(requestBody)
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    PaymentDepositResponseDto response = expectSuccessAndGetResponse(res, refPaymentDeposit);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.getDepositMethod()).isEqualTo("BANK_TRANSFER");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 요청")
        void failNoAuth(VertxTestContext tc) {
            String requestBody = """
                {
                    "currencyCode": "KRC",
                    "amount": 100.0,
                    "depositMethod": "CARD",
                    "paymentAmount": 10000.0
                }
                """;
            
            reqPost(getUrl("/deposit"))
                .sendJson(requestBody)
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("결제 입금 상세 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetPaymentDepositTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 결제 입금 상세 조회")
        void successGetPaymentDeposit(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            // 먼저 결제 입금 요청
            String requestBody = """
                {
                    "currencyCode": "KRC",
                    "amount": 100.0,
                    "depositMethod": "CARD",
                    "paymentAmount": 10000.0
                }
                """;
            
            reqPost(getUrl("/deposit"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(requestBody)
                .onComplete(tc.succeeding(createRes -> tc.verify(() -> {
                    PaymentDepositResponseDto created = expectSuccessAndGetResponse(createRes, refPaymentDeposit);
                    
                    // 결제 입금 상세 조회
                    reqGet(getUrl("/deposit/" + created.getDepositId()))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            PaymentDepositResponseDto response = expectSuccessAndGetResponse(res, refPaymentDeposit);
                            
                            assertThat(response).isNotNull();
                            assertThat(response.getDepositId()).isEqualTo(created.getDepositId());
                            assertThat(response.getOrderNumber()).isEqualTo(created.getOrderNumber());
                            
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 결제 입금 조회")
        void failNotFound(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            reqGet(getUrl("/deposit/non-existent-deposit-id"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }
    }
}

