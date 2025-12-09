package com.foxya.coin.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.auth.dto.LoginResponseDto;
import com.foxya.coin.auth.dto.TokenResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<LoginResponseDto>> refLoginResponse = new TypeReference<>() {};
    private final TypeReference<ApiResponse<TokenResponseDto>> refTokenResponse = new TypeReference<>() {};
    
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
                .put("loginId", "newuser")
                .put("password", "Test1234!@");
            
            reqPost(getUrl("/register"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Register response: {}", res.bodyAsJsonObject());
                    LoginResponseDto dto = expectSuccessAndGetResponse(res, refLoginResponse);
                    
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getRefreshToken()).isNotNull();
                    assertThat(dto.getLoginId()).isEqualTo("newuser");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("실패 - 비밀번호 형식 오류")
        void failInvalidPassword(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "weakuser")
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
                    TokenResponseDto dto = expectSuccessAndGetResponse(res, refTokenResponse);
                    
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getUserId()).isEqualTo(1L);
                    
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
    
    @Nested
    @DisplayName("소셜 계정 연동 테스트")
    class LinkSocialTest {
        
        @Test
        @Order(7)
        @DisplayName("성공 - 카카오 계정 연동")
        void successLinkKakao(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("provider", "KAKAO")
                .put("token", "kakao_token_123");
            
            reqPost(getUrl("/link-social"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Link social response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(8)
        @DisplayName("성공 - 구글 계정 연동")
        void successLinkGoogle(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("provider", "GOOGLE")
                .put("token", "google_token_123");
            
            reqPost(getUrl("/link-social"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Link social response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(9)
        @DisplayName("실패 - 인증 없이 연동")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("provider", "KAKAO")
                .put("token", "kakao_token_123");
            
            reqPost(getUrl("/link-social"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("본인인증(휴대폰) 등록 테스트")
    class VerifyPhoneTest {
        
        @Test
        @Order(10)
        @DisplayName("성공 - 본인인증 등록")
        void successVerifyPhone(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("phoneNumber", "01012345678")
                .put("verificationCode", "123456");
            
            reqPost(getUrl("/verify-phone"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Verify phone response: {}", res.bodyAsJsonObject());
                    assertThat(res.statusCode()).isEqualTo(200);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(11)
        @DisplayName("실패 - 인증 없이 등록")
        void failNoAuth(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("phoneNumber", "01012345678")
                .put("verificationCode", "123456");
            
            reqPost(getUrl("/verify-phone"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

