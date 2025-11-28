package com.foxya.coin.user;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
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
    private final TypeReference<ApiResponse<JsonObject>> refJsonResponse = new TypeReference<>() {};
    
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
                    JsonObject data = expectSuccessAndGetResponse(res, refJsonResponse);
                    
                    assertThat(data.getString("referralCode")).isNotNull();
                    assertThat(data.getString("referralCode")).hasSize(6);
                    assertThat(data.getLong("userId")).isEqualTo(2L);
                    
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
            
            reqPost(getUrl("/4/generate/referral-code")) // blocked_user에게 생성
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    log.info("Admin generate referral code response: {}", res.bodyAsJsonObject());
                    JsonObject data = expectSuccessAndGetResponse(res, refJsonResponse);
                    
                    assertThat(data.getString("referralCode")).isNotNull();
                    assertThat(data.getString("referralCode")).hasSize(6);
                    assertThat(data.getLong("userId")).isEqualTo(4L);
                    
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
}

