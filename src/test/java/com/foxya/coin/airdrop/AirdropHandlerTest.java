package com.foxya.coin.airdrop;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.airdrop.dto.AirdropStatusDto;
import com.foxya.coin.airdrop.dto.AirdropTransferRequestDto;
import com.foxya.coin.airdrop.dto.AirdropTransferResponseDto;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AirdropHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<AirdropStatusDto>> refStatus = new TypeReference<>() {};
    private final TypeReference<ApiResponse<AirdropTransferResponseDto>> refTransfer = new TypeReference<>() {};
    
    // 테스트용 사용자 ID (R__01_test_users.sql 참고)
    private static final Long TESTUSER_ID = 1L;
    
    public AirdropHandlerTest() {
        super("/api/v1/airdrop");
    }
    
    @Test
    @Order(1)
    @DisplayName("성공 - 에어드랍 상태 조회")
    void successGetAirdropStatus(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        
        reqGet(getUrl("/status"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(res -> tc.verify(() -> {
                log.info("Airdrop status response: {}", res.bodyAsJsonObject());
                AirdropStatusDto status = expectSuccessAndGetResponse(res, refStatus);
                
                assertThat(status).isNotNull();
                assertThat(status.getTotalReceived()).isNotNull();
                assertThat(status.getTotalReward()).isNotNull();
                assertThat(status.getPhases()).isNotNull();
                
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(2)
    @DisplayName("성공 - 에어드랍 전송")
    void successTransferAirdrop(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        
        JsonObject data = new JsonObject()
            .put("amount", 10000.0);
        
        reqPost(getUrl("/transfer"))
            .bearerTokenAuthentication(accessToken)
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Airdrop transfer response: {}", res.bodyAsJsonObject());
                AirdropTransferResponseDto transfer = expectSuccessAndGetResponse(res, refTransfer);
                
                assertThat(transfer.getTransferId()).isNotNull();
                assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("10000.0"));
                assertThat(transfer.getStatus()).isEqualTo("COMPLETED");
                
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(3)
    @DisplayName("실패 - 전송 가능한 금액 부족")
    void failInsufficientAmount(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        
        JsonObject data = new JsonObject()
            .put("amount", 999999.0);
        
        reqPost(getUrl("/transfer"))
            .bearerTokenAuthentication(accessToken)
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Insufficient amount response: {}", res.bodyAsJsonObject());
                expectError(res, 400);
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(4)
    @DisplayName("실패 - 잘못된 금액 (0보다 작음)")
    void failInvalidAmount(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        
        JsonObject data = new JsonObject()
            .put("amount", -1.0);
        
        reqPost(getUrl("/transfer"))
            .bearerTokenAuthentication(accessToken)
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Invalid amount response: {}", res.bodyAsJsonObject());
                expectError(res, 400);
                tc.completeNow();
            })));
    }
}

