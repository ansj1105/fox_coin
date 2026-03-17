package com.foxya.coin.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;
import io.vertx.ext.web.client.HttpRequest;
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
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Sha256Hash;
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
    @DisplayName("TRON legacy 시드 로그인 복구 테스트")
    class LegacyTronSeedLoginRecoveryTest {

        @Test
        @Order(9)
        @DisplayName("성공 - owner_address 없는 legacy virtual wallet도 지갑 등록 후 시드 로그인 가능")
        void bindsLegacyVirtualWalletAndLoginSucceeds(VertxTestContext tc) {
            ECKeyPair keyPair = ECKeyPair.create(new BigInteger(SEED_PRIVATE_KEY_HEX, 16));
            String ownerAddress = tronAddressFromKeyPair(keyPair);
            String virtualAddress = "TVirtualLegacy1111111111111111111111";
            String hotWalletAddress = "THotWalletLegacy1111111111111111111";
            String accessToken = getAccessTokenOfUser(1L);

            sqlClient.preparedQuery("SELECT id FROM currency WHERE code = $1 AND chain = $2 LIMIT 1")
                .execute(Tuple.of("TRX", "TRON"))
                .compose(currencyRows -> {
                    Integer currencyId = currencyRows.iterator().next().getInteger("id");
                    return sqlClient.preparedQuery(
                            "INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at) " +
                                "VALUES ($1, $2, $3, 0, 0, 'ACTIVE', NOW(), NOW()) " +
                                "ON CONFLICT (user_id, currency_id) DO UPDATE SET address = EXCLUDED.address, updated_at = NOW()"
                        )
                        .execute(Tuple.of(1L, currencyId, virtualAddress))
                        .compose(v -> sqlClient.preparedQuery(
                                "INSERT INTO virtual_wallet_mappings (user_id, network, hot_wallet_address, virtual_address, owner_address, mapping_seed, status, created_at, updated_at) " +
                                    "VALUES ($1, 'TRON', $2, $3, NULL, $4, 'ACTIVE', NOW(), NOW()) " +
                                    "ON CONFLICT (user_id, network) WHERE deleted_at IS NULL DO UPDATE " +
                                    "SET hot_wallet_address = EXCLUDED.hot_wallet_address, virtual_address = EXCLUDED.virtual_address, owner_address = NULL, updated_at = NOW()"
                            )
                            .execute(Tuple.of(1L, hotWalletAddress, virtualAddress, "user:1:TRON")));
                })
                .onSuccess(v -> {
                    JsonObject challengeRequest = new JsonObject()
                        .put("address", ownerAddress)
                        .put("chain", "TRON");

                    reqPost("/api/v1/wallets/register-challenge")
                        .bearerTokenAuthentication(accessToken)
                        .sendJson(challengeRequest, tc.succeeding(challengeRes -> tc.verify(() -> {
                            expectSuccess(challengeRes);
                            String walletMessage = challengeRes.bodyAsJsonObject()
                                .getJsonObject("data")
                                .getString("message");
                            String walletSignature = signRecoveryMessage(walletMessage, keyPair);

                            JsonObject registerRequest = new JsonObject()
                                .put("currencyCode", "TRX")
                                .put("address", ownerAddress)
                                .put("chain", "TRON")
                                .put("signature", walletSignature);

                            reqPost("/api/v1/wallets/register")
                                .bearerTokenAuthentication(accessToken)
                                .sendJson(registerRequest, tc.succeeding(registerRes -> tc.verify(() -> {
                                    expectSuccess(registerRes);

                                    JsonObject recoveryRequest = new JsonObject()
                                        .put("address", ownerAddress)
                                        .put("chain", "TRON");

                                    reqPost(getUrl("/recovery/challenge"))
                                        .sendJson(recoveryRequest, tc.succeeding(recoveryRes -> tc.verify(() -> {
                                            expectSuccess(recoveryRes);
                                            String recoveryMessage = recoveryRes.bodyAsJsonObject()
                                                .getJsonObject("data")
                                                .getString("message");
                                            String recoverySignature = signRecoveryMessage(recoveryMessage, keyPair);

                                            JsonObject loginRequest = new JsonObject()
                                                .put("address", ownerAddress)
                                                .put("chain", "TRON")
                                                .put("signature", recoverySignature)
                                                .mergeIn(deviceInfo("seed-device-tron-legacy-bind-1", "MOBILE", "ANDROID"));

                                            reqPost(getUrl("/login-with-seed"))
                                                .sendJson(loginRequest, tc.succeeding(loginRes -> tc.verify(() -> {
                                                    LoginResponseDto dto = expectSuccessAndGetResponse(loginRes, refLoginResponse);
                                                    assertThat(dto.getUserId()).isEqualTo(1L);
                                                    assertThat(dto.getAccessToken()).isNotBlank();
                                                    tc.completeNow();
                                                })));
                                        })));
                                })));
                        })));
                })
                .onFailure(tc::failNow);
        }

        @Test
        @DisplayName("성공 - owner_address 없는 legacy virtual wallet도 private_key fallback 으로 바로 시드 로그인 가능")
        void loginSucceedsWithLegacyPrivateKeyFallback(VertxTestContext tc) {
            ECKeyPair keyPair = ECKeyPair.create(new BigInteger(SEED_PRIVATE_KEY_HEX, 16));
            String ownerAddress = tronAddressFromKeyPair(keyPair);
            String virtualAddress = "TVirtualLegacyDirect11111111111111111";
            String hotWalletAddress = "THotWalletLegacyDirect111111111111111";

            sqlClient.preparedQuery("SELECT id FROM currency WHERE code = $1 AND chain = $2 LIMIT 1")
                .execute(Tuple.of("TRX", "TRON"))
                .compose(currencyRows -> {
                    Integer currencyId = currencyRows.iterator().next().getInteger("id");
                    return sqlClient.preparedQuery(
                            "INSERT INTO user_wallets (user_id, currency_id, address, private_key, balance, locked_balance, status, created_at, updated_at) " +
                                "VALUES ($1, $2, $3, $4, 0, 0, 'ACTIVE', NOW(), NOW()) " +
                                "ON CONFLICT (user_id, currency_id) DO UPDATE " +
                                "SET address = EXCLUDED.address, private_key = EXCLUDED.private_key, updated_at = NOW()"
                        )
                        .execute(Tuple.of(1L, currencyId, virtualAddress, SEED_PRIVATE_KEY_HEX))
                        .compose(v -> sqlClient.preparedQuery(
                                "INSERT INTO virtual_wallet_mappings (user_id, network, hot_wallet_address, virtual_address, owner_address, mapping_seed, status, created_at, updated_at) " +
                                    "VALUES ($1, 'TRON', $2, $3, NULL, $4, 'ACTIVE', NOW(), NOW()) " +
                                    "ON CONFLICT (user_id, network) WHERE deleted_at IS NULL DO UPDATE " +
                                    "SET hot_wallet_address = EXCLUDED.hot_wallet_address, virtual_address = EXCLUDED.virtual_address, owner_address = NULL, updated_at = NOW()"
                            )
                            .execute(Tuple.of(1L, hotWalletAddress, virtualAddress, "user:1:TRON")));
                })
                .onSuccess(v -> {
                    JsonObject recoveryRequest = new JsonObject()
                        .put("address", ownerAddress)
                        .put("chain", "TRON");

                    reqPost(getUrl("/recovery/challenge"))
                        .sendJson(recoveryRequest, tc.succeeding(recoveryRes -> tc.verify(() -> {
                            expectSuccess(recoveryRes);
                            String recoveryMessage = recoveryRes.bodyAsJsonObject()
                                .getJsonObject("data")
                                .getString("message");
                            String recoverySignature = signRecoveryMessage(recoveryMessage, keyPair);

                            JsonObject loginRequest = new JsonObject()
                                .put("address", ownerAddress)
                                .put("chain", "TRON")
                                .put("signature", recoverySignature)
                                .mergeIn(deviceInfo("seed-device-tron-legacy-fallback-1", "MOBILE", "ANDROID"));

                            reqPost(getUrl("/login-with-seed"))
                                .sendJson(loginRequest, tc.succeeding(loginRes -> tc.verify(() -> {
                                    LoginResponseDto dto = expectSuccessAndGetResponse(loginRes, refLoginResponse);
                                    assertThat(dto.getUserId()).isEqualTo(1L);
                                    assertThat(dto.getAccessToken()).isNotBlank();

                                    sqlClient.preparedQuery(
                                            "SELECT owner_address FROM virtual_wallet_mappings WHERE user_id = $1 AND network = 'TRON' AND deleted_at IS NULL"
                                        )
                                        .execute(Tuple.of(1L), tc.succeeding(rows -> tc.verify(() -> {
                                            assertThat(rows.iterator().next().getString("owner_address")).isEqualTo(ownerAddress);
                                            tc.completeNow();
                                        })));
                                })));
                        })));
                })
                .onFailure(tc::failNow);
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
                                .put("country", "KR")
                                .mergeIn(deviceInfo("register-device-web-1", "WEB", "WEB"));
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
                .put("seedConfirmed", true)
                .put("nickname", "nickfail")
                .put("name", "DN")
                .put("country", "KR")
                .mergeIn(deviceInfo("register-device-web-2", "WEB", "WEB"));
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
                .put("seedConfirmed", true)
                .put("nickname", "nicknam1")
                .put("name", "D")
                .put("country", "KR")
                .mergeIn(deviceInfo("register-device-web-3", "WEB", "WEB"));
            reqPost(getUrl("/register"))
                .sendJson(reg, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(33)
        @DisplayName("실패 - 소셜 가입 토큰 만료 (signupToken 없음/만료 시 400 + errorCode SOCIAL_SIGNUP_EXPIRED)")
        void failSocialSignupExpired(VertxTestContext tc) {
            JsonObject reg = new JsonObject()
                .put("email", "social-expired@test.com")
                .put("code", "")
                .put("password", "Test1234!@")
                .put("seedConfirmed", true)
                .put("signupToken", "nonexistent-or-expired-token-12345")
                .put("nickname", "nickexp")
                .put("name", "Expired")
                .put("country", "KR")
                .mergeIn(deviceInfo("register-device-web-exp", "WEB", "WEB"));
            reqPost(getUrl("/register"))
                .sendJson(reg, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    JsonObject body = res.bodyAsJsonObject();
                    assertThat(body.getString("errorCode")).isEqualTo("SOCIAL_SIGNUP_EXPIRED");
                    assertThat(body.getString("status")).isEqualTo("ERROR");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(34)
        @DisplayName("성공 - device 없이 회원가입 (deviceId/deviceType/deviceOs 선택)")
        void successRegisterWithoutDevice(VertxTestContext tc) {
            String email = "register-no-device@test.com";
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
                                .put("nickname", "nicknode")
                                .put("name", "NoDevice")
                                .put("country", "KR");
                            reqPost(getUrl("/register"))
                                .sendJson(reg, tc.succeeding(regRes -> tc.verify(() -> {
                                    assertThat(regRes.statusCode()).as("device 없이 회원가입 성공").isEqualTo(200);
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
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("test-device-web-1", "WEB", "WEB"));
            
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
                .put("password", "WrongPassword123!")
                .mergeIn(deviceInfo("test-device-web-2", "WEB", "WEB"));
            
            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(5)
        @DisplayName("성공 - device 없이 로그인 (deviceId/deviceType/deviceOs 선택)")
        void successWithoutDevice(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@");

            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    LoginResponseDto dto = expectSuccessAndGetResponse(res, refLoginResponse);
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getRefreshToken()).isNotNull();
                    assertThat(dto.getLoginId()).isEqualTo("testuser");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(6)
        @DisplayName("성공 - 로그인 응답에 minAppVersion 포함 (Android/WEB 기준 config_value)")
        void loginResponseContainsMinAppVersion(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("test-device-minver", "WEB", "WEB"));

            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    LoginResponseDto dto = expectSuccessAndGetResponse(res, refLoginResponse);
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getMinAppVersion()).isEqualTo("1.1.8");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(7)
        @DisplayName("성공 - deviceOs=IOS 로그인 시 minAppVersion 포함 (iOS는 config_value_apple, 비어있으면 config_value)")
        void loginResponseWithIosDeviceContainsMinAppVersion(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("test-device-ios", "MOBILE", "IOS"));

            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    LoginResponseDto dto = expectSuccessAndGetResponse(res, refLoginResponse);
                    assertThat(dto.getAccessToken()).isNotNull();
                    assertThat(dto.getMinAppVersion()).isEqualTo("1.1.8");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(8)
        @DisplayName("실패 - 계정 차단(is_account_blocked) 시 로그인 불가 (V41)")
        void failLoginWhenAccountBlocked(VertxTestContext tc) {
            JsonObject data = new JsonObject()
                .put("loginId", "blocked_user")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("blocked-device-1", "WEB", "WEB"));

            reqPost(getUrl("/login"))
                .sendJson(data, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    String message = res.bodyAsJsonObject().getString("message");
                    assertThat(message).isNotNull().contains("차단");
                    tc.completeNow();
                })));
        }

        @Test
        @Order(26)
        @DisplayName("성공 - 동일 슬롯 로그인 시 기존 기기 교체")
        void replaceDeviceOnSameSlot(VertxTestContext tc) {
            JsonObject first = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("limit-device-web-1", "WEB", "WEB"));
            JsonObject second = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("limit-device-web-2", "WEB", "WEB"));
            JsonObject mobile = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("limit-device-mobile-1", "MOBILE", "ANDROID"));

            reqPost(getUrl("/login"))
                .sendJson(first, tc.succeeding(firstRes -> tc.verify(() -> {
                    LoginResponseDto firstDto = expectSuccessAndGetResponse(firstRes, refLoginResponse);
                    reqPost(getUrl("/login"))
                        .sendJson(second, tc.succeeding(secondRes -> tc.verify(() -> {
                            LoginResponseDto secondDto = expectSuccessAndGetResponse(secondRes, refLoginResponse);
                            reqGetWithCustomHeaders(
                                "/api/v1/users/me",
                                firstDto.getAccessToken(),
                                "limit-device-web-1",
                                "WEB",
                                "WEB"
                            ).send(tc.succeeding(oldRes -> tc.verify(() -> {
                                    expectError(oldRes, 401);
                                    reqGetWithCustomHeaders(
                                        "/api/v1/users/me",
                                        secondDto.getAccessToken(),
                                        "limit-device-web-2",
                                        "WEB",
                                        "WEB"
                                    ).send(tc.succeeding(newRes -> tc.verify(() -> {
                                            expectSuccess(newRes);
                                            reqPost(getUrl("/login"))
                                                .sendJson(mobile, tc.succeeding(mobileRes -> tc.verify(() -> {
                                                    expectSuccessAndGetResponse(mobileRes, refLoginResponse);
                                                    tc.completeNow();
                                                })));
                                        })));
                                })));
                        })));
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
                                .put("signature", signature)
                                .mergeIn(deviceInfo("seed-device-mobile-1", "MOBILE", "ANDROID"));

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

        @Test
        @DisplayName("성공 - ETH 저장 주소가 달라도 private_key fallback 으로 시드 구문 로그인")
        void successLoginWithSeedForLegacyEthAddressMismatch(VertxTestContext tc) {
            ECKeyPair keyPair = ECKeyPair.create(new BigInteger(SEED_PRIVATE_KEY_HEX, 16));
            String ownerAddress = "0x" + Keys.getAddress(keyPair.getPublicKey());
            String legacyAddress = "0x00000000000000000000000000000000000000aa";

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
                                    "INSERT INTO user_wallets (user_id, currency_id, address, private_key, balance, locked_balance, status, created_at, updated_at) " +
                                        "VALUES ($1, $2, $3, $4, 0, 0, 'ACTIVE', NOW(), NOW()) " +
                                        "ON CONFLICT (user_id, currency_id) DO UPDATE " +
                                        "SET address = EXCLUDED.address, private_key = EXCLUDED.private_key, updated_at = NOW()"
                                )
                                .execute(Tuple.of(userId, currencyId, legacyAddress, SEED_PRIVATE_KEY_HEX));
                        });
                })
                .onSuccess(v -> {
                    JsonObject challengeRequest = new JsonObject()
                        .put("address", ownerAddress)
                        .put("chain", "ETH");

                    reqPost(getUrl("/recovery/challenge"))
                        .sendJson(challengeRequest, tc.succeeding(chRes -> tc.verify(() -> {
                            expectSuccess(chRes);
                            String message = chRes.bodyAsJsonObject()
                                .getJsonObject("data")
                                .getString("message");

                            String signature = signRecoveryMessage(message, keyPair);
                            JsonObject loginRequest = new JsonObject()
                                .put("address", ownerAddress)
                                .put("chain", "ETH")
                                .put("signature", signature)
                                .mergeIn(deviceInfo("seed-device-eth-legacy-fallback-1", "MOBILE", "ANDROID"));

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

        @Test
        @DisplayName("성공 - TRON legacy owner 주소로도 시드 구문 로그인")
        void successLoginWithSeedForLegacyTronOwnerAddress(VertxTestContext tc) {
            ECKeyPair keyPair = ECKeyPair.create(new BigInteger(SEED_PRIVATE_KEY_HEX, 16));
            String ownerAddress = tronAddressFromKeyPair(keyPair);
            String virtualAddress = "TVirtualLegacyAddress1111111111111111";

            sqlClient.preparedQuery("SELECT id FROM users WHERE login_id = $1")
                .execute(Tuple.of("testuser"))
                .compose(userRows -> {
                    if (!userRows.iterator().hasNext()) {
                        return io.vertx.core.Future.failedFuture("user not found");
                    }
                    Long userId = userRows.iterator().next().getLong("id");
                    return sqlClient.preparedQuery("SELECT id FROM currency WHERE code = $1 AND chain = $2 LIMIT 1")
                        .execute(Tuple.of("TRX", "TRON"))
                        .compose(currencyRows -> {
                            if (!currencyRows.iterator().hasNext()) {
                                return io.vertx.core.Future.failedFuture("tron currency not found");
                            }
                            Integer currencyId = currencyRows.iterator().next().getInteger("id");
                            return sqlClient.preparedQuery(
                                    "INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at) " +
                                        "VALUES ($1, $2, $3, 0, 0, 'ACTIVE', NOW(), NOW()) " +
                                        "ON CONFLICT (user_id, currency_id) DO UPDATE " +
                                        "SET address = EXCLUDED.address, updated_at = NOW()"
                                )
                                .execute(Tuple.of(userId, currencyId, virtualAddress))
                                .compose(v -> sqlClient.preparedQuery(
                                        "INSERT INTO virtual_wallet_mappings (user_id, network, hot_wallet_address, virtual_address, owner_address, mapping_seed, status, created_at, updated_at) " +
                                            "VALUES ($1, 'TRON', $2, $3, $4, $5, 'ACTIVE', NOW(), NOW()) " +
                                            "ON CONFLICT DO NOTHING"
                                    )
                                    .execute(Tuple.of(userId, "THotWalletLegacy1111111111111111111", virtualAddress, ownerAddress, "user:" + userId + ":TRON")));
                        });
                })
                .onSuccess(v -> {
                    JsonObject challengeRequest = new JsonObject()
                        .put("address", ownerAddress)
                        .put("chain", "TRON");

                    reqPost(getUrl("/recovery/challenge"))
                        .sendJson(challengeRequest, tc.succeeding(chRes -> tc.verify(() -> {
                            expectSuccess(chRes);
                            String message = chRes.bodyAsJsonObject()
                                .getJsonObject("data")
                                .getString("message");

                            String signature = signRecoveryMessage(message, keyPair);
                            JsonObject loginRequest = new JsonObject()
                                .put("address", ownerAddress)
                                .put("chain", "TRON")
                                .put("signature", signature)
                                .mergeIn(deviceInfo("seed-device-tron-legacy-1", "MOBILE", "ANDROID"));

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

    private static JsonObject deviceInfo(String deviceId, String deviceType, String deviceOs) {
        return new JsonObject()
            .put("deviceId", deviceId)
            .put("deviceType", deviceType)
            .put("deviceOs", deviceOs)
            .put("appVersion", "1.0.0");
    }

    private static String tronAddressFromKeyPair(ECKeyPair keyPair) {
        byte[] addressBytes = Numeric.hexStringToByteArray(Keys.getAddress(keyPair.getPublicKey()));
        byte[] tron = new byte[addressBytes.length + 1];
        tron[0] = 0x41;
        System.arraycopy(addressBytes, 0, tron, 1, addressBytes.length);
        byte[] checksum = Sha256Hash.hashTwice(tron);
        byte[] withChecksum = new byte[tron.length + 4];
        System.arraycopy(tron, 0, withChecksum, 0, tron.length);
        System.arraycopy(checksum, 0, withChecksum, tron.length, 4);
        return Base58.encode(withChecksum);
    }

    private static HttpRequest<io.vertx.core.buffer.Buffer> withDeviceHeaders(HttpRequest<io.vertx.core.buffer.Buffer> req, String token, String deviceId, String deviceType, String deviceOs) {
        return req
            .putHeader("Authorization", "Bearer " + token)
            .putHeader("X-Device-Id", deviceId)
            .putHeader("X-Device-Type", deviceType)
            .putHeader("X-Device-Os", deviceOs);
    }
    
    private HttpRequest<io.vertx.core.buffer.Buffer> reqGetWithCustomHeaders(String url, String token, String deviceId, String deviceType, String deviceOs) {
        return withDeviceHeaders(
            webClient.get(port, "localhost", url),
            token,
            deviceId,
            deviceType,
            deviceOs
        );
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
            JsonObject login = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("refresh-device-web-1", "WEB", "WEB"));
            reqPost(getUrl("/login"))
                .sendJson(login, tc.succeeding(loginRes -> tc.verify(() -> {
                    LoginResponseDto loginDto = expectSuccessAndGetResponse(loginRes, refLoginResponse);
                    String refreshToken = loginDto.getRefreshToken();
                    assertThat(refreshToken).isNotNull();

                    // 2) POST /refresh (로그인과 동일한 device 헤더 사용 — ensureActiveDevice 검증용)
                    JsonObject body = new JsonObject().put("refreshToken", refreshToken);
                    webClient.post(port, "localhost", getUrl("/refresh"))
                        .putHeader("X-Device-Id", "refresh-device-web-1")
                        .putHeader("X-Device-Type", "WEB")
                        .putHeader("X-Device-Os", "WEB")
                        .sendJson(body, tc.succeeding(refreshRes -> tc.verify(() -> {
                            assertThat(refreshRes.statusCode()).isEqualTo(200);
                            JsonObject json = refreshRes.bodyAsJsonObject();
                            assertThat(json.getString("status")).isEqualTo("OK");
                            assertThat(json.getString("accessToken")).isNotNull();
                            assertThat(json.getString("refreshToken")).isNotNull();
                            assertThat(json.getString("accessToken")).isNotEqualTo(json.getString("refreshToken"));
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

        @Test
        @Order(26)
        @DisplayName("실패 - accessToken을 refreshToken으로 전달하면 401")
        void failAccessTokenUsedAsRefreshToken(VertxTestContext tc) {
            JsonObject login = new JsonObject()
                .put("loginId", "testuser")
                .put("password", "Test1234!@")
                .mergeIn(deviceInfo("refresh-device-web-2", "WEB", "WEB"));
            reqPost(getUrl("/login"))
                .sendJson(login, tc.succeeding(loginRes -> tc.verify(() -> {
                    LoginResponseDto loginDto = expectSuccessAndGetResponse(loginRes, refLoginResponse);
                    String accessToken = loginDto.getAccessToken();
                    assertThat(accessToken).isNotNull();

                    JsonObject body = new JsonObject().put("refreshToken", accessToken);
                    webClient.post(port, "localhost", getUrl("/refresh"))
                        .putHeader("X-Device-Id", "refresh-device-web-2")
                        .putHeader("X-Device-Type", "WEB")
                        .putHeader("X-Device-Os", "WEB")
                        .sendJson(body, tc.succeeding(refreshRes -> tc.verify(() -> {
                            expectError(refreshRes, 401);
                            tc.completeNow();
                        })));
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
                        .put("password", "Test1234!@")
                        .mergeIn(deviceInfo("deleted-device-web-1", "WEB", "WEB"));
                    
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
