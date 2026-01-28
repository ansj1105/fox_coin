package com.foxya.coin.airdrop;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.airdrop.dto.AirdropPhaseDto;
import com.foxya.coin.airdrop.dto.AirdropPhaseReleaseResponseDto;
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
    private final TypeReference<ApiResponse<AirdropPhaseReleaseResponseDto>> refRelease = new TypeReference<>() {};
    
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
    @DisplayName("성공 - Phase Release (보상형 영상 시청 완료 후 락업 해제 반영)")
    void successReleasePhase(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        reqGet(getUrl("/status"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(statusRes -> tc.verify(() -> {
                AirdropStatusDto status = expectSuccessAndGetResponse(statusRes, refStatus);
                AirdropPhaseDto toRelease = status.getPhases().stream()
                    .filter(p -> "RELEASED".equals(p.getStatus()) && !Boolean.TRUE.equals(p.getClaimed()))
                    .findFirst()
                    .orElse(null);
                assertThat(toRelease).isNotNull();
                reqPost(getUrl("/phase/" + toRelease.getId() + "/release"))
                    .bearerTokenAuthentication(accessToken)
                    .send(tc.succeeding(releaseRes -> tc.verify(() -> {
                        AirdropPhaseReleaseResponseDto released = expectSuccessAndGetResponse(releaseRes, refRelease);
                        assertThat(released.getPhaseId()).isEqualTo(toRelease.getId());
                        assertThat(released.getAmount()).isEqualByComparingTo(toRelease.getAmount());
                        tc.completeNow();
                    })));
            })));
    }

    @Test
    @Order(3)
    @DisplayName("성공 - 에어드랍 전송")
    void successTransferAirdrop(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        // 락업 해제 금액 = RELEASED && claimed true 만 해당. 시드에 RELEASED phase가 2개 있으므로
        // Order 2가 하나 Release 후, 남은 하나를 이 테스트에서 Release 한 뒤 전송한다.
        reqGet(getUrl("/status"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(statusRes -> tc.verify(() -> {
                AirdropStatusDto status = expectSuccessAndGetResponse(statusRes, refStatus);
                AirdropPhaseDto toRelease = status.getPhases().stream()
                    .filter(p -> "RELEASED".equals(p.getStatus()) && !Boolean.TRUE.equals(p.getClaimed()))
                    .findFirst()
                    .orElse(null);
                assertThat(toRelease).as("Release 가능한 phase가 있어야 함 (시드에 RELEASED 2개)").isNotNull();
                reqPost(getUrl("/phase/" + toRelease.getId() + "/release"))
                    .bearerTokenAuthentication(accessToken)
                    .send(tc.succeeding(releaseRes -> tc.verify(() -> {
                        expectSuccess(releaseRes);
                        JsonObject data = new JsonObject().put("amount", 10000.0);
                        reqPost(getUrl("/transfer"))
                            .bearerTokenAuthentication(accessToken)
                            .sendJson(data, tc.succeeding(transferRes -> tc.verify(() -> {
                                AirdropTransferResponseDto transfer = expectSuccessAndGetResponse(transferRes, refTransfer);
                                assertThat(transfer.getTransferId()).isNotNull();
                                assertThat(transfer.getAmount()).isEqualByComparingTo(new BigDecimal("10000.0"));
                                assertThat(transfer.getStatus()).isEqualTo("COMPLETED");
                                tc.completeNow();
                            })));
                    })));
            })));
    }
    
    @Test
    @Order(4)
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
    @Order(5)
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

    @Test
    @Order(6)
    @DisplayName("실패 - Phase Release 이미 완료된 Phase")
    void failReleasePhaseAlreadyClaimed(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        reqGet(getUrl("/status"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(statusRes -> tc.verify(() -> {
                AirdropStatusDto status = expectSuccessAndGetResponse(statusRes, refStatus);
                AirdropPhaseDto released = status.getPhases().stream()
                    .filter(p -> "RELEASED".equals(p.getStatus()) && Boolean.TRUE.equals(p.getClaimed()))
                    .findFirst()
                    .orElse(null);
                if (released == null) {
                    tc.completeNow();
                    return;
                }
                reqPost(getUrl("/phase/" + released.getId() + "/release"))
                    .bearerTokenAuthentication(accessToken)
                    .send(tc.succeeding(res -> tc.verify(() -> {
                        expectError(res, 400);
                        tc.completeNow();
                    })));
            })));
    }

    @Test
    @Order(7)
    @DisplayName("실패 - Phase Release 존재하지 않는 phaseId")
    void failReleasePhaseNotFound(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        reqPost(getUrl("/phase/999999/release"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(res -> tc.verify(() -> {
                expectError(res, 404);
                tc.completeNow();
            })));
    }

    @Test
    @Order(8)
    @DisplayName("실패 - Phase Release 잘못된 phaseId (숫자 아님)")
    void failReleasePhaseInvalidId(VertxTestContext tc) {
        String accessToken = getAccessTokenOfUser(TESTUSER_ID);
        reqPost(getUrl("/phase/not-a-number/release"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(res -> tc.verify(() -> {
                expectError(res, 400);
                tc.completeNow();
            })));
    }
}

