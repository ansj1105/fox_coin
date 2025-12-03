package com.foxya.coin.transfer;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.transfer.dto.TransferResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransferHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<TransferResponseDto>> refTransfer = new TypeReference<>() {};
    private final TypeReference<ApiResponse<List<TransferResponseDto>>> refTransferList = new TypeReference<>() {};
    
    // 테스트용 사용자 ID (R__01_test_users.sql 참고)
    private static final Long TESTUSER_ID = 1L;      // testuser - 잔액 1000 FOXYA
    private static final Long TESTUSER2_ID = 2L;     // testuser2 - 잔액 500 FOXYA
    private static final Long ADMIN_USER_ID = 3L;    // admin_user - 잔액 10000 FOXYA
    private static final Long REFERRER_USER_ID = 5L; // referrer_user - 잔액 2000 FOXYA, 추천인코드: REFER123
    private static final Long NO_CODE_USER_ID = 6L;  // no_code_user - 잔액 100 FOXYA
    
    // 테스트용 지갑 주소
    private static final String TESTUSER2_WALLET_ADDRESS = "TADDR_TESTUSER2_001";
    private static final String EXTERNAL_WALLET_ADDRESS = "TExternalWalletAddress12345678901234";
    
    public TransferHandlerTest() {
        super("/api/v1/transfers");
    }
    
    @Nested
    @DisplayName("내부 전송 테스트 - 지갑 주소로 전송")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InternalTransferByAddressTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 지갑 주소로 내부 전송")
        void successTransferByAddress(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", TESTUSER2_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0)
                .put("memo", "테스트 전송");
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Internal transfer by address response: {}", res.bodyAsJsonObject());
                    TransferResponseDto transfer = expectSuccessAndGetResponse(res, refTransfer);
                    
                    assertThat(transfer.getTransferId()).isNotNull();
                    assertThat(transfer.getTransferType()).isEqualTo("INTERNAL");
                    assertThat(transfer.getSenderId()).isEqualTo(TESTUSER_ID);
                    assertThat(transfer.getReceiverId()).isEqualTo(TESTUSER2_ID);
                    assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("100"));
                    assertThat(transfer.getStatus()).isEqualTo("COMPLETED");
                    assertThat(transfer.getFee()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 지갑 주소")
        void failInvalidAddress(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", "INVALID_ADDRESS_12345")
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0);
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Invalid address response: {}", res.bodyAsJsonObject());
                    expectError(res, 404); // NotFoundException: 수신자를 찾을 수 없습니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 자기 자신에게 전송")
        void failSelfTransfer(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", "TADDR_TESTUSER_001") // 자신의 지갑 주소
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0);
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Self transfer response: {}", res.bodyAsJsonObject());
                    expectError(res, 400); // BadRequestException: 자기 자신에게 전송할 수 없습니다
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("내부 전송 테스트 - 추천인 코드로 전송")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InternalTransferByReferralCodeTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 추천인 코드로 내부 전송")
        void successTransferByReferralCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "REFERRAL_CODE")
                .put("receiverValue", "REFER123") // referrer_user의 추천인 코드
                .put("currencyCode", "FOXYA")
                .put("amount", 50.0)
                .put("memo", "추천인 코드로 전송");
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Internal transfer by referral code response: {}", res.bodyAsJsonObject());
                    TransferResponseDto transfer = expectSuccessAndGetResponse(res, refTransfer);
                    
                    assertThat(transfer.getTransferId()).isNotNull();
                    assertThat(transfer.getTransferType()).isEqualTo("INTERNAL");
                    assertThat(transfer.getSenderId()).isEqualTo(TESTUSER_ID);
                    assertThat(transfer.getReceiverId()).isEqualTo(REFERRER_USER_ID);
                    assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("50"));
                    assertThat(transfer.getStatus()).isEqualTo("COMPLETED");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 추천인 코드")
        void failInvalidReferralCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "REFERRAL_CODE")
                .put("receiverValue", "INVALID999")
                .put("currencyCode", "FOXYA")
                .put("amount", 50.0);
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Invalid referral code response: {}", res.bodyAsJsonObject());
                    expectError(res, 404); // NotFoundException: 수신자를 찾을 수 없습니다
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("내부 전송 테스트 - 유저 ID로 전송 (관리자)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InternalTransferByUserIdTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - ADMIN이 유저 ID로 내부 전송")
        void successAdminTransferByUserId(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(ADMIN_USER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "USER_ID")
                .put("receiverValue", TESTUSER_ID.toString())
                .put("currencyCode", "FOXYA")
                .put("amount", 200.0)
                .put("memo", "관리자 지급");
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Admin transfer by user ID response: {}", res.bodyAsJsonObject());
                    TransferResponseDto transfer = expectSuccessAndGetResponse(res, refTransfer);
                    
                    assertThat(transfer.getTransferId()).isNotNull();
                    assertThat(transfer.getTransferType()).isEqualTo("INTERNAL");
                    assertThat(transfer.getSenderId()).isEqualTo(ADMIN_USER_ID);
                    assertThat(transfer.getReceiverId()).isEqualTo(TESTUSER_ID);
                    assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("200"));
                    assertThat(transfer.getStatus()).isEqualTo("COMPLETED");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 유저 ID")
        void failInvalidUserId(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(ADMIN_USER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "USER_ID")
                .put("receiverValue", "99999")
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0);
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Invalid user ID response: {}", res.bodyAsJsonObject());
                    expectError(res, 404); // NotFoundException: 수신자를 찾을 수 없습니다
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("내부 전송 테스트 - 잔액 부족")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InternalTransferInsufficientBalanceTest {
        
        @Test
        @Order(1)
        @DisplayName("실패 - 잔액 부족")
        void failInsufficientBalance(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(NO_CODE_USER_ID); // 잔액 100 FOXYA
            
            JsonObject data = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", TESTUSER2_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 1000.0); // 잔액보다 많은 금액
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Insufficient balance response: {}", res.bodyAsJsonObject());
                    expectError(res, 400); // BadRequestException: 잔액이 부족합니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 최소 전송 금액 미만")
        void failMinimumAmount(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", TESTUSER2_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 0.0000001); // 최소 금액 미만
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Minimum amount response: {}", res.bodyAsJsonObject());
                    expectError(res, 400); // BadRequestException: 최소 전송 금액
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("내부 전송 테스트 - 인증")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InternalTransferAuthTest {
        
        @Test
        @Order(1)
        @DisplayName("실패 - 인증 없이 전송 시도")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", TESTUSER2_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0);
            
            reqPost(getUrl("/internal"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("외부 전송 테스트 (출금 요청)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ExternalTransferTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - TRON 네트워크로 외부 전송 요청")
        void successExternalTransferRequest(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("toAddress", EXTERNAL_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0)
                .put("chain", "TRON")
                .put("memo", "외부 출금 테스트");
            
            reqPost(getUrl("/external"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("External transfer response: {}", res.bodyAsJsonObject());
                    TransferResponseDto transfer = expectSuccessAndGetResponse(res, refTransfer);
                    
                    assertThat(transfer.getTransferId()).isNotNull();
                    assertThat(transfer.getTransferType()).isEqualTo("EXTERNAL");
                    assertThat(transfer.getSenderId()).isEqualTo(TESTUSER_ID);
                    assertThat(transfer.getToAddress()).isEqualTo(EXTERNAL_WALLET_ADDRESS);
                    assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("100"));
                    assertThat(transfer.getStatus()).isEqualTo("PENDING"); // 외부 전송은 PENDING 상태로 시작
                    assertThat(transfer.getFee()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 잘못된 체인")
        void failInvalidChain(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("toAddress", EXTERNAL_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0)
                .put("chain", "INVALID_CHAIN");
            
            reqPost(getUrl("/external"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Invalid chain response: {}", res.bodyAsJsonObject());
                    expectError(res, 400); // Validation error
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 빈 주소")
        void failEmptyAddress(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject data = new JsonObject()
                .put("toAddress", "")
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0)
                .put("chain", "TRON");
            
            reqPost(getUrl("/external"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Empty address response: {}", res.bodyAsJsonObject());
                    expectError(res, 400); // Validation error
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 잔액 부족")
        void failInsufficientBalance(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(NO_CODE_USER_ID); // 잔액 100 FOXYA
            
            JsonObject data = new JsonObject()
                .put("toAddress", EXTERNAL_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 1000.0)
                .put("chain", "TRON");
            
            reqPost(getUrl("/external"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Insufficient balance response: {}", res.bodyAsJsonObject());
                    expectError(res, 400); // BadRequestException: 잔액이 부족합니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(5)
        @DisplayName("실패 - 인증 없이 외부 전송 시도")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("toAddress", EXTERNAL_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 100.0)
                .put("chain", "TRON");
            
            reqPost(getUrl("/external"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("전송 내역 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TransferHistoryTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 전송 내역 조회")
        void successGetHistory(VertxTestContext tc) {
            // 먼저 전송 실행
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject transferData = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", TESTUSER2_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 10.0);
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(transferData, tc.succeeding(transferRes -> tc.verify(() -> {
                    assertThat(transferRes.statusCode()).isEqualTo(200);
                    
                    // 전송 내역 조회
                    reqGet(getUrl("/history?limit=10&offset=0"))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            log.info("Transfer history response: {}", res.bodyAsJsonObject());
                            List<TransferResponseDto> history = expectSuccessAndGetResponse(res, refTransferList);
                            
                            assertThat(history).isNotEmpty();
                            assertThat(history.get(0).getTransferId()).isNotNull();
                            
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 내역 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/history"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401); // Unauthorized
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("전송 상세 조회 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TransferDetailTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 전송 상세 조회")
        void successGetDetail(VertxTestContext tc) {
            // 먼저 전송 실행
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            JsonObject transferData = new JsonObject()
                .put("receiverType", "ADDRESS")
                .put("receiverValue", TESTUSER2_WALLET_ADDRESS)
                .put("currencyCode", "FOXYA")
                .put("amount", 5.0);
            
            reqPost(getUrl("/internal"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(transferData, tc.succeeding(transferRes -> tc.verify(() -> {
                    TransferResponseDto created = expectSuccessAndGetResponse(transferRes, refTransfer);
                    String transferId = created.getTransferId();
                    
                    // 전송 상세 조회
                    reqGet(getUrl("/" + transferId))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(res -> tc.verify(() -> {
                            log.info("Transfer detail response: {}", res.bodyAsJsonObject());
                            TransferResponseDto detail = expectSuccessAndGetResponse(res, refTransfer);
                            
                            assertThat(detail.getTransferId()).isEqualTo(transferId);
                            assertThat(detail.getSenderId()).isEqualTo(TESTUSER_ID);
                            assertThat(detail.getReceiverId()).isEqualTo(TESTUSER2_ID);
                            
                            tc.completeNow();
                        })));
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 존재하지 않는 전송 ID")
        void failInvalidTransferId(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(TESTUSER_ID);
            
            reqGet(getUrl("/invalid-transfer-id-12345"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Invalid transfer ID response: {}", res.bodyAsJsonObject());
                    // null이 반환되므로 200이지만 data가 null
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
    }
}

