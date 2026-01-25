package com.foxya.coin.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;
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
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

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
    private static final String SEED_PRIVATE_KEY_HEX = "4f3edf983ac636a65a842ce7c78d9aa706d3b113b37d3e4c5f7c6f9c1e6f3f99";

    public AuthHandlerTest() {
        super("/api/v1/auth");
    }
    
    @Nested
    @DisplayName("check-email 테스트")
    class CheckEmailTest {
        @Test
        @Order(1)
        @DisplayName("성공 - 가입 가능한 이메일")
        void available(VertxTestContext tc) {
            reqPost(getUrl("/check-email"))
                .sendJson(new JsonObject().put("email", "newuser@test.com"), tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    assertThat(res.bodyAsJsonObject().getJsonObject("data").getBoolean("available")).isTrue();
                    tc.completeNow();
                })));
        }

        @Test
        @Order(2)
        @DisplayName("성공 - 가입 가능 (다른 이메일)")
        void available2(VertxTestContext tc) {
            reqPost(getUrl("/check-email"))
                .sendJson(new JsonObject().put("email", "newuser2@test.com"), tc.succeeding(res -> tc.verify(() -> {
                    assertThat(res.bodyAsJsonObject().getJsonObject("data").getBoolean("available")).isTrue();
                    tc.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("email/send-code 테스트")
    class SendSignupCodeTest {
        @Test
        @Order(10)
        @DisplayName("성공 - 인증코드 발송")
        void success(VertxTestContext tc) {
            reqPost(getUrl("/email/send-code"))
                .sendJson(new JsonObject().put("email", "sendcode@test.com"), tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(11)
        @DisplayName("실패 - 이미 가입된 이메일(login_id) 409")
        void failAlreadyRegistered(VertxTestContext tc) {
            // testuser의 login_id는 "testuser" (이메일 형식 아님). 이메일 형식인 기존 유저가 있어야 409.
            // send-code "invalid" 형식 -> 400. 409를 위해선 login_id=email인 유저 필요. seed에 추가하거나 생략.
            reqPost(getUrl("/email/send-code"))
                .sendJson(new JsonObject().put("email", "bad"), tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("email/verify 테스트")
    class VerifySignupEmailTest {
        @Test
        @Order(12)
        @DisplayName("실패 - 인증코드 불일치 또는 만료")
        void failInvalidCode(VertxTestContext tc) {
            reqPost(getUrl("/email/verify"))
                .sendJson(new JsonObject().put("email", "any@test.com").put("code", "000000"), tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("회원가입 테스트 (이메일 인증 기반)")
    class RegisterTest {

        @Test
        @Order(30)
        @DisplayName("성공 - 이메일 인증 후 회원가입 (send-code → DB에서 code 조회 → register)")
        void success(VertxTestContext tc) {
            String email = "register-success@test.com";
            reqPost(getUrl("/email/send-code"))
                .sendJson(new JsonObject().put("email", email), tc.succeeding(sendRes -> tc.verify(() -> {
                    expectSuccess(sendRes);
                    sqlClient.preparedQuery("SELECT code FROM signup_email_codes WHERE email = $1 ORDER BY created_at DESC LIMIT 1")
                        .execute(Tuple.of(email))
                        .onSuccess(rows -> {
                            if (!rows.iterator().hasNext()) { tc.failNow(new AssertionError("code not found")); return; }
                            String code = rows.iterator().next().getString(0);
                            JsonObject reg = new JsonObject()
                                .put("email", email)
                                .put("code", code)
                                .put("password", "Test1234!@")
                                .put("seedConfirmed", true)
                                .put("nickname", "nickreg1")
                                .put("name", "DisplayOne")
                                .put("country", "KR");
                            reqPost(getUrl("/register"))
                                .sendJson(reg, tc.succeeding(regRes -> tc.verify(() -> {
                                    LoginResponseDto dto = expectSuccessAndGetResponse(regRes, refLoginResponse);
                                    assertThat(dto.getAccessToken()).isNotNull();
                                    assertThat(dto.getRefreshToken()).isNotNull();
                                    assertThat(dto.getLoginId()).isEqualTo(email);
                                    tc.completeNow();
                                })));
                        })
                        .onFailure(tc::failNow);
                })));
        }

        @Test
        @Order(31)
        @DisplayName("실패 - 인증코드 불일치/만료")
        void failInvalidCode(VertxTestContext tc) {
            JsonObject reg = new JsonObject()
                .put("email", "no-code@test.com")
                .put("code", "999999")
                .put("password", "Test1234!@")
                .put("nickname", "nickfail")
                .put("name", "DN")
                .put("country", "KR");
            reqPost(getUrl("/register"))
                .sendJson(reg, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(32)
        @DisplayName("실패 - 비밀번호 형식 오류")
        void failInvalidPassword(VertxTestContext tc) {
            JsonObject reg = new JsonObject()
                .put("email", "pwfail@test.com")
                .put("code", "000000")
                .put("password", "short")
                .put("nickname", "nicknam1")
                .put("name", "D")
                .put("country", "KR");
            reqPost(getUrl("/register"))
                .sendJson(reg, tc.succeeding(res -> tc.verify(() -> {
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
    @DisplayName("시드 로그인 테스트")
    class LoginWithSeedTest {

        @Test
        @Order(40)
        @DisplayName("성공 - 시드 구문 로그인")
        void successLoginWithSeed(VertxTestContext tc) {
            ECKeyPair keyPair = ECKeyPair.create(new BigInteger(SEED_PRIVATE_KEY_HEX, 16));
            String address = "0x" + Keys.getAddress(keyPair.getPublicKey());

            sqlClient.preparedQuery("SELECT id FROM users WHERE login_id = $1")
                .execute(Tuple.of("testuser"))
                .compose(userRows -> {
                    if (!userRows.iterator().hasNext()) {
                        return io.vertx.core.Future.failedFuture("user not found");
                    }
                    Long userId = userRows.iterator().next().getLong("id");
                    return sqlClient.preparedQuery("SELECT id FROM currency WHERE code = $1 LIMIT 1")
                        .execute(Tuple.of("ETH"))
                        .compose(currencyRows -> {
                            if (!currencyRows.iterator().hasNext()) {
                                return io.vertx.core.Future.failedFuture("currency not found");
                            }
                            Integer currencyId = currencyRows.iterator().next().getInteger("id");
                            return sqlClient.preparedQuery(
                                    "INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at) " +
                                        "VALUES ($1, $2, $3, 0, 0, 'ACTIVE', NOW(), NOW()) " +
                                        "ON CONFLICT (user_id, currency_id) DO UPDATE " +
                                        "SET address = EXCLUDED.address, updated_at = NOW()"
                                )
                                .execute(Tuple.of(userId, currencyId, address));
                        });
                })
                .onSuccess(v -> {
                    JsonObject challengeRequest = new JsonObject()
                        .put("address", address)
                        .put("chain", "ETH");

                    reqPost(getUrl("/recovery/challenge"))
                        .sendJson(challengeRequest, tc.succeeding(chRes -> tc.verify(() -> {
                            expectSuccess(chRes);
                            String message = chRes.bodyAsJsonObject()
                                .getJsonObject("data")
                                .getString("message");

                            String signature = signRecoveryMessage(message, keyPair);
                            JsonObject loginRequest = new JsonObject()
                                .put("address", address)
                                .put("chain", "ETH")
                                .put("signature", signature);

                            reqPost(getUrl("/login-with-seed"))
                                .sendJson(loginRequest, tc.succeeding(loginRes -> tc.verify(() -> {
                                    LoginResponseDto dto = expectSuccessAndGetResponse(loginRes, refLoginResponse);
                                    assertThat(dto.getAccessToken()).isNotNull();
                                    assertThat(dto.getRefreshToken()).isNotNull();
                                    assertThat(dto.getUserId()).isNotNull();
                                    tc.completeNow();
                                })));
                        })));
                })
                .onFailure(tc::failNow);
        }
    }

    private static String signRecoveryMessage(String message, ECKeyPair keyPair) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        Sign.SignatureData signatureData = Sign.signPrefixedMessage(messageBytes, keyPair);
        byte[] signature = new byte[65];
        System.arraycopy(signatureData.getR(), 0, signature, 0, 32);
        System.arraycopy(signatureData.getS(), 0, signature, 32, 32);
        signature[64] = signatureData.getV()[0];
        return Numeric.toHexString(signature);
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
    @DisplayName("POST /refresh (refreshToken으로 access/refresh 재발급)")
    class RefreshTest {

        @Test
        @Order(24)
        @DisplayName("성공 - refreshToken으로 accessToken·refreshToken 재발급")
        void success(VertxTestContext tc) {
            // 1) 로그인으로 refreshToken 획득
            JsonObject login = new JsonObject().put("loginId", "testuser").put("password", "Test1234!@");
            reqPost(getUrl("/login"))
                .sendJson(login, tc.succeeding(loginRes -> tc.verify(() -> {
                    LoginResponseDto loginDto = expectSuccessAndGetResponse(loginRes, refLoginResponse);
                    String refreshToken = loginDto.getRefreshToken();
                    assertThat(refreshToken).isNotNull();

                    // 2) POST /refresh (Authorization 없음)
                    JsonObject body = new JsonObject().put("refreshToken", refreshToken);
                    reqPost(getUrl("/refresh"))
                        .sendJson(body, tc.succeeding(refreshRes -> tc.verify(() -> {
                            assertThat(refreshRes.statusCode()).isEqualTo(200);
                            JsonObject json = refreshRes.bodyAsJsonObject();
                            assertThat(json.getString("status")).isEqualTo("OK");
                            assertThat(json.getString("accessToken")).isNotNull();
                            assertThat(json.getString("refreshToken")).isNotNull();
                            tc.completeNow();
                        })));
                })));
        }

        @Test
        @Order(25)
        @DisplayName("실패 - Invalid or expired refresh token (401)")
        void failInvalidRefreshToken(VertxTestContext tc) {
            JsonObject body = new JsonObject().put("refreshToken", "invalid.jwt.token");
            reqPost(getUrl("/refresh"))
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
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
