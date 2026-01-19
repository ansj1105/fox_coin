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
import com.foxya.coin.auth.dto.LogoutResponseDto;
import com.foxya.coin.auth.dto.DeleteAccountRequestDto;
import com.foxya.coin.auth.dto.DeleteAccountResponseDto;
import com.foxya.coin.auth.dto.FindLoginIdDataDto;
import com.foxya.coin.auth.dto.GoogleLoginResponseDto;
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
    private final TypeReference<ApiResponse<LogoutResponseDto>> refLogoutResponse = new TypeReference<>() {};
    private final TypeReference<ApiResponse<DeleteAccountResponseDto>> refDeleteAccountResponse = new TypeReference<>() {};
    private final TypeReference<ApiResponse<GoogleLoginResponseDto>> refGoogleLoginResponse = new TypeReference<>() {};
    private final TypeReference<ApiResponse<FindLoginIdDataDto>> refFindLoginIdResponse = new TypeReference<>() {};

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
    @DisplayName("아이디 찾기 테스트")
    class FindLoginIdTest {

        @Test
        @Order(20)
        @DisplayName("성공 - 이메일로 로그인 아이디 조회 (마스킹)")
        void success(VertxTestContext tc) {
            // testuser는 email_verifications에 testuser1@example.com (is_verified) 등록됨. loginId=testuser -> te**user
            JsonObject data = new JsonObject().put("email", "testuser1@example.com");

            reqPost(getUrl("/find-login-id"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Find login ID response: {}", res.bodyAsJsonObject());
                    FindLoginIdDataDto dto = expectSuccessAndGetResponse(res, refFindLoginIdResponse);
                    assertThat(dto.getMaskedLoginId()).isEqualTo("te**user");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(21)
        @DisplayName("실패 - 등록된 이메일과 일치하는 계정 없음")
        void failNotFound(VertxTestContext tc) {
            JsonObject data = new JsonObject().put("email", "notfound@example.com");

            reqPost(getUrl("/find-login-id"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("비밀번호 찾기(임시 비밀번호 발송) 테스트")
    class SendTempPasswordTest {

        @Test
        @Order(22)
        @DisplayName("실패 - 등록된 이메일과 일치하는 계정 없음")
        void failNotFound(VertxTestContext tc) {
            JsonObject data = new JsonObject().put("email", "notfound@example.com");

            reqPost(getUrl("/send-temp-password"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 404);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(23)
        @DisplayName("실패 또는 성공 - 이메일 발송 (테스트 환경에선 SMTP 미설정 시 400)")
        void sendTempPassword(VertxTestContext tc) {
            // testuser1@example.com은 email_verifications에 등록된 testuser의 이메일.
            // SMTP 미설정 시 "임시 비밀번호 발송에 실패했습니다."(400). SMTP 설정 시 200.
            JsonObject data = new JsonObject().put("email", "testuser1@example.com");

            reqPost(getUrl("/send-temp-password"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Send temp password response: {} {}", res.statusCode(), res.bodyAsString());
                    assertThat(res.statusCode()).isIn(200, 400);
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
    
    @Nested
    @DisplayName("로그아웃 테스트")
    class LogoutTest {
        
        @Test
        @Order(12)
        @DisplayName("성공 - 정상 로그아웃")
        void success(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqPost(getUrl("/logout"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Logout response: {}", res.bodyAsJsonObject());
                    LogoutResponseDto dto = expectSuccessAndGetResponse(res, refLogoutResponse);
                    
                    assertThat(dto.getStatus()).isEqualTo("OK");
                    assertThat(dto.getMessage()).isEqualTo("Logged out successfully");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(13)
        @DisplayName("성공 - 모든 디바이스 로그아웃")
        void successAllDevices(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqPost(getUrl("/logout?allDevices=true"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Logout all devices response: {}", res.bodyAsJsonObject());
                    LogoutResponseDto dto = expectSuccessAndGetResponse(res, refLogoutResponse);
                    
                    assertThat(dto.getStatus()).isEqualTo("OK");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(14)
        @DisplayName("실패 - 인증 토큰 없음")
        void failNoToken(VertxTestContext tc) {
            reqPost(getUrl("/logout"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(15)
        @DisplayName("실패 - 유효하지 않은 토큰")
        void failInvalidToken(VertxTestContext tc) {
            reqPost(getUrl("/logout"))
                .putHeader("Authorization", "Bearer invalid_token_here")
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("회원 탈퇴 테스트")
    class DeleteAccountTest {
        
        @Test
        @Order(16)
        @DisplayName("실패 - 잘못된 비밀번호")
        void failInvalidPassword(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("password", "WrongPassword123!");
            
            reqDelete(getUrl("/account"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(17)
        @DisplayName("실패 - 인증 토큰 없음")
        void failNoToken(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("password", "Test1234!@");
            
            reqDelete(getUrl("/account"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(18)
        @DisplayName("성공 - 정상 탈퇴")
        void success(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            JsonObject data = new JsonObject()
                .put("password", "Test1234!@");
            
            reqDelete(getUrl("/account"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Delete account response: {}", res.bodyAsJsonObject());
                    DeleteAccountResponseDto dto = expectSuccessAndGetResponse(res, refDeleteAccountResponse);
                    
                    assertThat(dto.getStatus()).isEqualTo("OK");
                    assertThat(dto.getMessage()).isEqualTo("Account deleted successfully");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(19)
        @DisplayName("성공 - 탈퇴 후 로그인 불가")
        void successCannotLoginAfterDeletion(VertxTestContext tc) {
            // 먼저 사용자를 삭제
            String accessToken = getAccessTokenOfUser(1L);
            JsonObject deleteData = new JsonObject()
                .put("password", "Test1234!@");
            
            reqDelete(getUrl("/account"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(deleteData, tc.succeeding(deleteRes -> tc.verify(() -> {
                    // 삭제 성공 확인
                    assertThat(deleteRes.statusCode()).isEqualTo(200);
                    
                    // 삭제된 사용자로 로그인 시도
                    JsonObject loginData = new JsonObject()
                        .put("loginId", "testuser")
                        .put("password", "Test1234!@");
                    
                    reqPost(getUrl("/login"))
                        .sendJson(loginData, tc.succeeding(loginRes -> tc.verify(() -> {
                            // 탈퇴된 사용자는 로그인 불가
                            expectError(loginRes, 401);
                            tc.completeNow();
                        })));
                })));
        }
    }
}

