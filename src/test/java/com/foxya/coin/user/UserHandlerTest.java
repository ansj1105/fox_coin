package com.foxya.coin.user;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import com.foxya.coin.user.dto.ReferralCodeResponseDto;
import com.foxya.coin.user.dto.EmailInfoDto;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserHandlerTest extends HandlerTestBase {
    
    private final TypeReference<ApiResponse<User>> refUser = new TypeReference<>() {};
    private final TypeReference<ApiResponse<ReferralCodeResponseDto>> refReferralCodeResponse = new TypeReference<>() {};
    private final TypeReference<ApiResponse<EmailInfoDto>> refEmailInfo = new TypeReference<>() {};
    private final TypeReference<ApiResponse<Void>> refVoid = new TypeReference<>() {};
    
    public UserHandlerTest() {
        super("/api/v1/users");
    }
    
    @Nested
    @DisplayName("사용자 조회 테스트")
    class GetUserTest {
        
        @Test
        @Order(1)
        @DisplayName("성공 - ADMIN 권한으로 조회")
        void successAsAdmin(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(1L);
            
            reqGet(getUrl("/1"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get user response: {}", res.bodyAsJsonObject());
                    User user = expectSuccessAndGetResponse(res, refUser);
                    
                    assertThat(user.getId()).isEqualTo(1L);
                    assertThat(user.getLoginId()).isNotNull();
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(2)
        @DisplayName("성공 - USER 권한으로 조회")
        void successAsUser(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L);
            
            reqGet(getUrl("/1"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get user response: {}", res.bodyAsJsonObject());
                    User user = expectSuccessAndGetResponse(res, refUser);
                    
                    assertThat(user.getId()).isEqualTo(1L);
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(3)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/1"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }

    @Nested
    @DisplayName("이메일 설정 API 테스트")
    class EmailSettingsTest {

        @Test
        @Order(12)
        @DisplayName("성공 - 이메일 정보 조회 (이미 인증된 이메일)")
        void successGetEmailInfoVerified(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser

            reqGet(getUrl("/email"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get email info response: {}", res.bodyAsJsonObject());
                    EmailInfoDto data = expectSuccessAndGetResponse(res, refEmailInfo);

                    assertThat(data.getEmail()).isEqualTo("testuser1@example.com");
                    assertThat(data.isVerified()).isTrue();
                    tc.completeNow();
                })));
        }

        @Test
        @Order(13)
        @DisplayName("성공 - 이메일 정보 조회 (이메일 없음)")
        void successGetEmailInfoNone(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(6L); // no_code_user, 이메일 시드 없음

            reqGet(getUrl("/email"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get email info (none) response: {}", res.bodyAsJsonObject());
                    EmailInfoDto data = expectSuccessAndGetResponse(res, refEmailInfo);

                    assertThat(data.getEmail()).isNull();
                    assertThat(data.isVerified()).isFalse();
                    tc.completeNow();
                })));
        }

        @Test
        @Order(14)
        @DisplayName("성공 - 이메일 인증 코드 발송")
        void successSendEmailCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(6L); // no_code_user

            JsonObject body = new JsonObject()
                .put("email", "newemail@example.com");

            reqPost(getUrl("/email/send-code"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Send email code response: {}", res.bodyAsJsonObject());
                    // 단순 200 OK만 확인 (코드는 DB에 저장되고 EmailService가 로그 출력)
                    expectSuccessAndGetResponse(res, refVoid);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(15)
        @DisplayName("성공 - 이메일 인증 및 등록")
        void successVerifyEmail(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L); // testuser2 - 코드 222222 시드

            JsonObject body = new JsonObject()
                .put("email", "testuser2@example.com")
                .put("code", "222222");

            reqPost(getUrl("/email/verify"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Verify email response: {}", res.bodyAsJsonObject());
                    expectSuccessAndGetResponse(res, refVoid);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(16)
        @DisplayName("실패 - 잘못된 코드로 이메일 인증")
        void failVerifyEmailInvalidCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L); // testuser2

            JsonObject body = new JsonObject()
                .put("email", "testuser2@example.com")
                .put("code", "999999");

            reqPost(getUrl("/email/verify"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(17)
        @DisplayName("실패 - 인증 없이 이메일 정보 조회")
        void failGetEmailNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/email"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(18)
        @DisplayName("실패 - 인증 없이 이메일 코드 발송")
        void failSendCodeNoAuth(VertxTestContext tc) {
            JsonObject body = new JsonObject()
                .put("email", "test@test.com");

            reqPost(getUrl("/email/send-code"))
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(19)
        @DisplayName("실패 - 인증 없이 이메일 인증")
        void failVerifyNoAuth(VertxTestContext tc) {
            JsonObject body = new JsonObject()
                .put("email", "test@test.com")
                .put("code", "123456");

            reqPost(getUrl("/email/verify"))
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("레퍼럴 코드 생성 테스트")
    class GenerateReferralCodeTest {
        
        @Test
        @Order(4)
        @DisplayName("성공 - USER가 본인 레퍼럴 코드 생성")
        void successGenerateOwnCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L); // testuser2
            
            reqPost(getUrl("/generate/referral-code"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Generate referral code response: {}", res.bodyAsJsonObject());
                    ReferralCodeResponseDto data = expectSuccessAndGetResponse(res, refReferralCodeResponse);
                    
                    assertThat(data.getReferralCode()).isNotNull();
                    assertThat(data.getReferralCode()).hasSize(6);
                    assertThat(data.getReferralLink()).isNotNull();
                    assertThat(data.getReferralLink()).contains(data.getReferralCode());
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(5)
        @DisplayName("실패 - 이미 레퍼럴 코드가 있는 경우")
        void failAlreadyHasCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser (이미 REF001 보유)
            
            reqPost(getUrl("/generate/referral-code"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(6)
        @DisplayName("성공 - ADMIN이 특정 유저에게 레퍼럴 코드 생성")
        void successAdminGenerateForUser(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(3L); // admin_user
            
            // no_code_user(ID:6)에게 레퍼럴 코드 생성 (시드 데이터에서 레퍼럴 코드 없음)
            reqPost(getUrl("/6/generate/referral-code"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Admin generate referral code response: {}", res.bodyAsJsonObject());
                    ReferralCodeResponseDto data = expectSuccessAndGetResponse(res, refReferralCodeResponse);
                    
                    assertThat(data.getReferralCode()).isNotNull();
                    assertThat(data.getReferralCode()).hasSize(6);
                    assertThat(data.getReferralLink()).isNotNull();
                    assertThat(data.getReferralLink()).contains(data.getReferralCode());
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(7)
        @DisplayName("실패 - USER가 다른 유저에게 레퍼럴 코드 생성 시도")
        void failUserGenerateForOther(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser
            
            reqPost(getUrl("/2/generate/referral-code"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 403); // Forbidden
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(8)
        @DisplayName("실패 - 인증 없이 레퍼럴 코드 생성")
        void failNoAuth(VertxTestContext tc) {
            reqPost(getUrl("/generate/referral-code"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
    
    @Nested
    @DisplayName("추천인 코드 조회 테스트")
    class GetReferralCodeTest {
        
        @Test
        @Order(9)
        @DisplayName("성공 - 사용자의 추천인 코드 조회")
        void successGetReferralCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(1L); // testuser (REF001 보유)
            
            reqGet(getUrl("/referral-code"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Get referral code response: {}", res.bodyAsJsonObject());
                    ReferralCodeResponseDto data = expectSuccessAndGetResponse(res, refReferralCodeResponse);
                    
                    assertThat(data.getReferralCode()).isNotNull();
                    assertThat(data.getReferralLink()).isNotNull();
                    assertThat(data.getReferralLink()).contains(data.getReferralCode());
                    assertThat(data.getReferralLink()).startsWith("https://foxya.app/ref/");
                    
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(10)
        @DisplayName("실패 - 레퍼럴 코드가 없는 경우")
        void failNoReferralCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L); // testuser2 (레퍼럴 코드 없음)
            
            reqGet(getUrl("/referral-code"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400); // BadRequestException: 레퍼럴 코드가 없습니다
                    tc.completeNow();
                })));
        }
        
        @Test
        @Order(11)
        @DisplayName("실패 - 인증 없이 조회")
        void failNoAuth(VertxTestContext tc) {
            reqGet(getUrl("/referral-code"))
                .send(tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}

