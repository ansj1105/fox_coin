package com.foxya.coin.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.foxya.coin.common.HandlerTestBase;
import com.foxya.coin.common.dto.ApiResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityHandlerTest extends HandlerTestBase {

    private final TypeReference<ApiResponse<Void>> refVoid = new TypeReference<>() {};

    public SecurityHandlerTest() {
        super("/api/v1/security");
    }

    @Nested
    @DisplayName("거래 비밀번호 설정/변경 테스트")
    class TransactionPasswordTest {

        @Test
        @Order(1)
        @DisplayName("성공 - 올바른 이메일 인증 코드로 거래 비밀번호 설정")
        void successSetTransactionPassword(VertxTestContext tc) {
            // testuser2(ID:2)의 이메일 인증 코드: 222222 (R__03_test_email_verifications.sql)
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject body = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "123456");

            reqPost(getUrl("/transaction-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Set transaction password response: {}", res.bodyAsJsonObject());
                    expectSuccessAndGetResponse(res, refVoid);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(2)
        @DisplayName("실패 - 잘못된 코드로 거래 비밀번호 설정")
        void failInvalidCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject body = new JsonObject()
                .put("code", "999999")
                .put("newPassword", "123456");

            reqPost(getUrl("/transaction-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(3)
        @DisplayName("실패 - 형식이 잘못된 비밀번호")
        void failInvalidPasswordFormat(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject body = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "abc123"); // 숫자 6자리가 아님

            reqPost(getUrl("/transaction-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 거래 비밀번호 설정")
        void failNoAuth(VertxTestContext tc) {
            JsonObject body = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "123456");

            reqPost(getUrl("/transaction-password"))
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}


