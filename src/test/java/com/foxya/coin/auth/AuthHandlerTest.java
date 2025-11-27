package com.foxya.coin.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.auth.dto.LoginResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<LoginResponseDto>> refLoginResponse = new TypeReference<>() {};
    private final TypeReference<ApiResponse<JsonObject>> refJsonResponse = new TypeReference<>() {};
    
    public AuthHandlerTest() {
        super("/api/v1/auth");
    }
    
    @Nested
    @DisplayName("회원가입 테스트")
    class RegisterTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - 정상 회원가입")
        void success(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@");
            
            reqPost(getUrl("/register"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Register response: {}", res.bodyAsJsonObject());
                    LoginResponseDto dto = expectSuccessAndGetResponse(res, refLoginResponse);
                    
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getRefreshToken()).isNotNull();
                    assertThat(dto.getLoginId()).isEqualTo("testuser");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 비밀번호 형식 오류")
        void failInvalidPassword(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser2")
                .put("password", "weak");
            
            reqPost(getUrl("/register"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("로그인 테스트")
    class LoginTest {
        
        @Test
        @Order(3)
        @DisplayName("성공 - 정상 로그인")
        void success(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@");
            
            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Login response: {}", res.bodyAsJsonObject());
                    LoginResponseDto dto = expectSuccessAndGetResponse(res, refLoginResponse);
                    
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getRefreshToken()).isNotNull();
                    assertThat(dto.getLoginId()).isEqualTo("testuser");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(4)
        @DisplayName("실패 - 잘못된 비밀번호")
        void failWrongPassword(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "WrongPassword123!");
            
            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("토큰 재발급 테스트")
    class TokenRefreshTest {
        
        @Test
        @Order(5)
        @DisplayName("성공 - Access Token 재발급")
        void successAccessToken(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/access-token"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Access token refresh response: {}", res.bodyAsJsonObject());
                    JsonObject dto = expectSuccessAndGetResponse(res, refJsonResponse);
                    
                    assertThat(dto.getString("accessToken")).isNotNull();
                    assertThat(dto.getLong("userId")).isEqualTo(1L);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(6)
        @DisplayName("실패 - 토큰 없이 요청")
        void failNoToken(VertxTestContext tc) {
            reqGet(getUrl("/access-token"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

