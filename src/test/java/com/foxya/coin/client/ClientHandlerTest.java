package com.foxya.coin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.client.dto.TokenResponseDto;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClientHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<TokenResponseDto>> refToken = new TypeReference<>() {};
    
    // 테스트용 API Key (R__02_test_api_keys.sql 참고)
    private static final String TEST_API_KEY_001 = "test_api_key_001";
    private static final String TEST_SECRET_001 = "test_secret_001";
    private static final String TEST_API_KEY_002 = "test_api_key_002";
    private static final String TEST_SECRET_002 = "test_secret_002";
    private static final String TEST_API_KEY_003 = "test_api_key_003"; // 비활성화된 키
    private static final String TEST_SECRET_003 = "test_secret_003";
    private static final String TEST_API_KEY_004 = "test_api_key_004"; // 만료된 키
    private static final String TEST_SECRET_004 = "test_secret_004";
    
    public ClientHandlerTest() {
        super("/api/v1/client");
    }
    
    @Test
    @Order(1)
    @DisplayName("성공 - API Key로 토큰 발급")
    void successIssueToken(VertxTestContext tc) {
        JsonObject data = new JsonObject()
            .put("apiKey", TEST_API_KEY_001)
            .put("apiSecret", TEST_SECRET_001);
        
        reqPost(getUrl("/token"))
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Token issue response: {}", res.bodyAsJsonObject());
                TokenResponseDto token = expectSuccessAndGetResponse(res, refToken);
                
                assertThat(token.getAccessToken()).isNotNull();
                assertThat(token.getRefreshToken()).isNotNull();
                assertThat(token.getExpiresIn()).isEqualTo(1800L);
                
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(2)
    @DisplayName("실패 - 잘못된 API Key")
    void failInvalidApiKey(VertxTestContext tc) {
        JsonObject data = new JsonObject()
            .put("apiKey", "invalid_api_key")
            .put("apiSecret", TEST_SECRET_001);
        
        reqPost(getUrl("/token"))
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Invalid API key response: {}", res.bodyAsJsonObject());
                expectError(res, 401);
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(3)
    @DisplayName("실패 - 잘못된 API Secret")
    void failInvalidApiSecret(VertxTestContext tc) {
        JsonObject data = new JsonObject()
            .put("apiKey", TEST_API_KEY_001)
            .put("apiSecret", "invalid_secret");
        
        reqPost(getUrl("/token"))
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Invalid API secret response: {}", res.bodyAsJsonObject());
                expectError(res, 401);
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(4)
    @DisplayName("실패 - 비활성화된 API Key")
    void failInactiveApiKey(VertxTestContext tc) {
        JsonObject data = new JsonObject()
            .put("apiKey", TEST_API_KEY_003)
            .put("apiSecret", TEST_SECRET_003);
        
        reqPost(getUrl("/token"))
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Inactive API key response: {}", res.bodyAsJsonObject());
                expectError(res, 401);
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(5)
    @DisplayName("실패 - 만료된 API Key")
    void failExpiredApiKey(VertxTestContext tc) {
        JsonObject data = new JsonObject()
            .put("apiKey", TEST_API_KEY_004)
            .put("apiSecret", TEST_SECRET_004);
        
        reqPost(getUrl("/token"))
            .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                log.info("Expired API key response: {}", res.bodyAsJsonObject());
                expectError(res, 401);
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(6)
    @DisplayName("성공 - Refresh Token으로 새 토큰 발급")
    void successRefreshToken(VertxTestContext tc) {
        // 먼저 토큰 발급
        JsonObject tokenData = new JsonObject()
            .put("apiKey", TEST_API_KEY_001)
            .put("apiSecret", TEST_SECRET_001);
        
        reqPost(getUrl("/token"))
            .sendJson(tokenData, tc.succeeding(tokenRes -> tc.verify(() -> {
                TokenResponseDto token = expectSuccessAndGetResponse(tokenRes, refToken);
                String refreshToken = token.getRefreshToken();
                
                // Refresh Token으로 새 토큰 발급
                JsonObject refreshData = new JsonObject()
                    .put("refreshToken", refreshToken);
                
                reqPost(getUrl("/refresh"))
                    .sendJson(refreshData, tc.succeeding(refreshRes -> tc.verify(() -> {
                        log.info("Token refresh response: {}", refreshRes.bodyAsJsonObject());
                        TokenResponseDto newToken = expectSuccessAndGetResponse(refreshRes, refToken);
                        
                        assertThat(newToken.getAccessToken()).isNotNull();
                        assertThat(newToken.getRefreshToken()).isNotNull();
                        assertThat(newToken.getExpiresIn()).isEqualTo(1800L);
                        // 새 토큰은 이전 토큰과 다를 수 있음
                        
                        tc.completeNow();
                    })));
            })));
    }
    
    @Test
    @Order(7)
    @DisplayName("성공 - 유저 데이터 수신")
    void successReceiveUserData(VertxTestContext tc) {
        // 먼저 토큰 발급
        JsonObject tokenData = new JsonObject()
            .put("apiKey", TEST_API_KEY_001)
            .put("apiSecret", TEST_SECRET_001);
        
        reqPost(getUrl("/token"))
            .sendJson(tokenData, tc.succeeding(tokenRes -> tc.verify(() -> {
                TokenResponseDto token = expectSuccessAndGetResponse(tokenRes, refToken);
                String accessToken = token.getAccessToken();
                
                // 유저 데이터 전송
                JsonObject userData = new JsonObject()
                    .put("USER_SN", 1L)
                    .put("USER_NM", "홍길동")
                    .put("USER_EML", "test@example.com")
                    .put("USER_NICKNM", "닉네임")
                    .put("USER_MBLNO_NTN_CD", "82")
                    .put("USER_MBLNO_ENCPT", "encrypted_phone")
                    .put("USER_MBLNO_MASKING", "010-****-1234")
                    .put("USER_GNDR", 1)
                    .put("USER_NTN_CD", "KOR")
                    .put("IDTT_VRFC_YN", "Y")
                    .put("RMMBR_TOKEN", "remember_token")
                    .put("EML_VRFC_DT", "2026-01-05T10:00:00Z")
                    .put("ACTVTN_YN", "Y");
                
                JsonObject requestData = new JsonObject()
                    .put("userData", userData);
                
                reqPost(getUrl("/user-data"))
                    .bearerTokenAuthentication(accessToken)
                    .sendJson(requestData, tc.succeeding(userDataRes -> tc.verify(() -> {
                        log.info("User data response: {}", userDataRes.bodyAsJsonObject());
                        expectSuccess(userDataRes);
                        
                        tc.completeNow();
                    })));
            })));
    }
    
    @Test
    @Order(8)
    @DisplayName("실패 - 유저 데이터 수신 (토큰 없음)")
    void failReceiveUserDataNoToken(VertxTestContext tc) {
        JsonObject userData = new JsonObject()
            .put("USER_SN", 1L)
            .put("USER_NM", "홍길동");
        
        JsonObject requestData = new JsonObject()
            .put("userData", userData);
        
        reqPost(getUrl("/user-data"))
            .sendJson(requestData, tc.succeeding(res -> tc.verify(() -> {
                log.info("User data without token response: {}", res.bodyAsJsonObject());
                expectError(res, 401);
                tc.completeNow();
            })));
    }
    
    @Test
    @Order(9)
    @DisplayName("실패 - 유저 데이터 수신 (userData 필드 누락)")
    void failReceiveUserDataMissingField(VertxTestContext tc) {
        // 먼저 토큰 발급
        JsonObject tokenData = new JsonObject()
            .put("apiKey", TEST_API_KEY_001)
            .put("apiSecret", TEST_SECRET_001);
        
        reqPost(getUrl("/token"))
            .sendJson(tokenData, tc.succeeding(tokenRes -> tc.verify(() -> {
                TokenResponseDto token = expectSuccessAndGetResponse(tokenRes, refToken);
                String accessToken = token.getAccessToken();
                
                // userData 필드 없이 요청
                JsonObject requestData = new JsonObject()
                    .put("invalidField", "value");
                
                reqPost(getUrl("/user-data"))
                    .bearerTokenAuthentication(accessToken)
                    .sendJson(requestData, tc.succeeding(res -> tc.verify(() -> {
                        log.info("User data missing field response: {}", res.bodyAsJsonObject());
                        expectError(res, 400);
                        tc.completeNow();
                    })));
            })));
    }
}

