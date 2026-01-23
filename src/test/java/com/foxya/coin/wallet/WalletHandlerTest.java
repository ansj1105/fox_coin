package com.foxya.coin.wallet;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.wallet.entities.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WalletHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<List<Wallet>>> refWalletList = new TypeReference<>() {};
    private final TypeReference<ApiResponse<Wallet>> refWallet = new TypeReference<>() {};
    
    public WalletHandlerTest() {
        super("/api/v1/wallets");
    }
    
    @Nested
    @DisplayName("내 지갑 조회 테스트")
    class GetMyWalletsTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - USER 권한으로 조회")
        void successAsUser(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/my"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get wallets response: {}", res.bodyAsJsonObject());
                    List<Wallet> wallets = expectSuccessAndGetResponse(res, refWalletList);
                    
                    assertThat(wallets).isNotNull();
                    log.info("User has {} wallets", wallets.size());
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - ADMIN 권한으로 조회")
        void successAsAdmin(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(1L);
            
            reqGet(getUrl("/my"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get wallets response: {}", res.bodyAsJsonObject());
                    List<Wallet> wallets = expectSuccessAndGetResponse(res, refWalletList);
                    
                    assertThat(wallets).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/my"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("시드 구문 존재 여부 확인 테스트")
    class HasSeedTest {
        
        private final TypeReference<ApiResponse<HasSeedResponse>> refHasSeed = new TypeReference<>() {};
        
        @Test
        @Order(1)
        @DisplayName("성공 - 시드 구문이 있는 경우")
        void successHasSeed(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/has-seed"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Has seed response: {}", res.bodyAsJsonObject());
                    HasSeedResponse response = expectSuccessAndGetResponse(res, refHasSeed);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.hasSeed).isNotNull();
                    log.info("User has seed: {}", response.hasSeed);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - 시드 구문이 없는 경우")
        void successNoSeed(VertxTestContext tc) {
            // 시드 구문이 없는 사용자 (새로 생성된 사용자 등)
            String accessToken = getAccessTokenOfUser(2L);
            
            reqGet(getUrl("/has-seed"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Has seed response: {}", res.bodyAsJsonObject());
                    HasSeedResponse response = expectSuccessAndGetResponse(res, refHasSeed);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.hasSeed).isNotNull();
                    // 시드 구문이 없으면 false일 수 있음
                    log.info("User has seed: {}", response.hasSeed);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("성공 - ADMIN 권한으로 조회")
        void successAsAdmin(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(1L);
            
            reqGet(getUrl("/has-seed"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Has seed response: {}", res.bodyAsJsonObject());
                    HasSeedResponse response = expectSuccessAndGetResponse(res, refHasSeed);
                    
                    assertThat(response).isNotNull();
                    assertThat(response.hasSeed).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/has-seed"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("지갑 개인키 등록 테스트")
    class RegisterPrivateKeyTest {

        @Test
        @Order(1)
        @DisplayName("성공 - 개인키 저장")
        void successRegisterPrivateKey(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);

            io.vertx.core.json.JsonObject data = new io.vertx.core.json.JsonObject()
                .put("currencyCode", "FOXYA")
                .put("chain", "TRON")
                .put("privateKey", "TEST_PRIVATE_KEY_1234567890");

            reqPost(getUrl("/register-private-key"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    Wallet wallet = expectSuccessAndGetResponse(res, refWallet);
                    assertThat(wallet).isNotNull();
                    assertThat(wallet.getAddress()).isNotBlank();
                    tc.completeNow();
                })));
        }

        @Test
        @Order(2)
        @DisplayName("실패 - 인증 없이 요청")
        void failNoAuth(VertxTestContext tc) {
            io.vertx.core.json.JsonObject data = new io.vertx.core.json.JsonObject()
                .put("currencyCode", "FOXYA")
                .put("chain", "TRON")
                .put("privateKey", "TEST_PRIVATE_KEY_1234567890");

            reqPost(getUrl("/register-private-key"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    /**
     * 시드 구문 존재 여부 응답 DTO
     */
    private static class HasSeedResponse {
        public Boolean hasSeed;
    }
}
