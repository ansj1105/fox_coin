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

    @Nested
    @DisplayName("오프라인 페이 거래 비밀번호 상태/검증 테스트")
    class OfflinePayPinTest {

        @Test
        @DisplayName("상태 조회 - 기본 오프라인 페이 PIN 상태 반환")
        void getOfflinePayStatus(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            reqGet(getUrl("/offline-pay/status"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(res -> tc.verify(() -> {
                    JsonObject body = res.bodyAsJsonObject();
                    Assertions.assertEquals("OK", body.getString("status"));
                    JsonObject data = body.getJsonObject("data");
                    Assertions.assertNotNull(data);
                    Assertions.assertTrue(data.containsKey("pinFailedAttempts"));
                    Assertions.assertTrue(data.containsKey("pinRemainingAttempts"));
                    Assertions.assertTrue(data.containsKey("pinLocked"));
                    tc.completeNow();
                })));
        }

        @Test
        @DisplayName("성공 - 올바른 거래 비밀번호로 오프라인 PIN 검증")
        void verifyOfflinePayPinSuccess(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);
            JsonObject passwordBody = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "123456");

            reqPost(getUrl("/transaction-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(passwordBody, tc.succeeding(res -> tc.verify(() -> {
                    expectSuccessAndGetResponse(res, refVoid);

                    reqPost(getUrl("/offline-pay/pin/verify"))
                        .bearerTokenAuthentication(accessToken)
                        .sendJson(new JsonObject().put("pin", "123456"), tc.succeeding(verifyRes -> tc.verify(() -> {
                            JsonObject body = verifyRes.bodyAsJsonObject();
                            Assertions.assertEquals("OK", body.getString("status"));
                            JsonObject data = body.getJsonObject("data");
                            Assertions.assertTrue(data.getBoolean("verified"));
                            Assertions.assertFalse(data.getBoolean("pinLocked"));
                            Assertions.assertEquals(0, data.getInteger("pinFailedAttempts"));
                            tc.completeNow();
                        })));
                })));
        }

        @Test
        @DisplayName("실패 3회 - 오프라인 PIN 잠금")
        void verifyOfflinePayPinLocksAfterThreeFailures(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);
            JsonObject passwordBody = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "123456");

            reqPost(getUrl("/transaction-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(passwordBody, tc.succeeding(res -> tc.verify(() -> {
                    expectSuccessAndGetResponse(res, refVoid);

                    reqPost(getUrl("/offline-pay/pin/verify"))
                        .bearerTokenAuthentication(accessToken)
                        .sendJson(new JsonObject().put("pin", "000000"), tc.succeeding(firstRes -> tc.verify(() -> {
                            JsonObject firstData = firstRes.bodyAsJsonObject().getJsonObject("data");
                            Assertions.assertFalse(firstData.getBoolean("verified"));
                            Assertions.assertEquals(1, firstData.getInteger("pinFailedAttempts"));
                            Assertions.assertEquals(2, firstData.getInteger("pinRemainingAttempts"));

                            reqPost(getUrl("/offline-pay/pin/verify"))
                                .bearerTokenAuthentication(accessToken)
                                .sendJson(new JsonObject().put("pin", "000000"), tc.succeeding(secondRes -> tc.verify(() -> {
                                    JsonObject secondData = secondRes.bodyAsJsonObject().getJsonObject("data");
                                    Assertions.assertFalse(secondData.getBoolean("verified"));
                                    Assertions.assertEquals(2, secondData.getInteger("pinFailedAttempts"));
                                    Assertions.assertEquals(1, secondData.getInteger("pinRemainingAttempts"));

                                    reqPost(getUrl("/offline-pay/pin/verify"))
                                        .bearerTokenAuthentication(accessToken)
                                        .sendJson(new JsonObject().put("pin", "000000"), tc.succeeding(thirdRes -> tc.verify(() -> {
                                            JsonObject thirdData = thirdRes.bodyAsJsonObject().getJsonObject("data");
                                            Assertions.assertFalse(thirdData.getBoolean("verified"));
                                            Assertions.assertTrue(thirdData.getBoolean("pinLocked"));
                                            Assertions.assertEquals(0, thirdData.getInteger("pinRemainingAttempts"));

                                            reqGet(getUrl("/offline-pay/status"))
                                                .bearerTokenAuthentication(accessToken)
                                                .send(tc.succeeding(statusRes -> tc.verify(() -> {
                                                    JsonObject statusData = statusRes.bodyAsJsonObject().getJsonObject("data");
                                                    Assertions.assertTrue(statusData.getBoolean("pinLocked"));
                                                    Assertions.assertEquals(3, statusData.getInteger("pinFailedAttempts"));
                                                    tc.completeNow();
                                                })));
                                        })));
                                })));
                        })));
                })));
        }
    }

    @Nested
    @DisplayName("오프라인 페이 설정/공유 테스트")
    class OfflinePaySettingsAndShareTest {

        @Test
        @DisplayName("설정 조회/저장 - 서버 authoritative 설정 동기화")
        void getAndUpdateOfflinePaySettings(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            reqGet(getUrl("/offline-pay/settings"))
                .bearerTokenAuthentication(accessToken)
                .send(tc.succeeding(initialRes -> tc.verify(() -> {
                    JsonObject initialData = initialRes.bodyAsJsonObject().getJsonObject("data");
                    Assertions.assertNotNull(initialData);
                    Assertions.assertTrue(initialData.getBoolean("paymentOfflineEnabled"));

                    JsonObject updateBody = new JsonObject()
                        .put("paymentOfflineEnabled", false)
                        .put("paymentBleEnabled", false)
                        .put("paymentNfcEnabled", false)
                        .put("paymentApprovalMode", "EVERY_TIME")
                        .put("settlementAutoEnabled", true)
                        .put("settlementCycleMinutes", 10)
                        .put("storeMerchantLabel", "Offline Merchant");

                    reqPut(getUrl("/offline-pay/settings"))
                        .bearerTokenAuthentication(accessToken)
                        .sendJson(updateBody, tc.succeeding(updateRes -> tc.verify(() -> {
                            JsonObject updatedData = updateRes.bodyAsJsonObject().getJsonObject("data");
                            Assertions.assertFalse(updatedData.getBoolean("paymentOfflineEnabled"));
                            Assertions.assertFalse(updatedData.getBoolean("paymentBleEnabled"));
                            Assertions.assertEquals("EVERY_TIME", updatedData.getString("paymentApprovalMode"));
                            Assertions.assertEquals(10, updatedData.getInteger("settlementCycleMinutes"));
                            Assertions.assertEquals("Offline Merchant", updatedData.getString("storeMerchantLabel"));

                            reqGet(getUrl("/offline-pay/settings"))
                                .bearerTokenAuthentication(accessToken)
                                .send(tc.succeeding(getRes -> tc.verify(() -> {
                                    JsonObject persistedData = getRes.bodyAsJsonObject().getJsonObject("data");
                                    Assertions.assertFalse(persistedData.getBoolean("paymentOfflineEnabled"));
                                    Assertions.assertEquals("EVERY_TIME", persistedData.getString("paymentApprovalMode"));
                                    Assertions.assertEquals("Offline Merchant", persistedData.getString("storeMerchantLabel"));
                                    tc.completeNow();
                                })));
                        })));
                })));
        }

        @Test
        @DisplayName("공유 토큰 생성/공개 조회 - 로그인 없이 resolve 가능")
        void createAndResolveOfflinePaySharedDetail(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);
            JsonObject payload = new JsonObject()
                .put("asset", "KORI")
                .put("direction", "SENT")
                .put("item", new JsonObject()
                    .put("id", "item-1")
                    .put("title", "Collateral top-up")
                    .put("memo", "Offline collateral top-up request"));

            reqPost(getUrl("/offline-pay/shared-details"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(new JsonObject()
                    .put("itemId", "item-1")
                    .put("payload", payload), tc.succeeding(createRes -> tc.verify(() -> {
                        JsonObject createData = createRes.bodyAsJsonObject().getJsonObject("data");
                        String token = createData.getString("token");
                        Assertions.assertNotNull(token);
                        Assertions.assertTrue(createData.getString("url").contains("token=" + token));

                        reqGet("/api/v1/public/offline-pay/shared-details/" + token)
                            .send(tc.succeeding(publicRes -> tc.verify(() -> {
                                JsonObject publicData = publicRes.bodyAsJsonObject().getJsonObject("data");
                                Assertions.assertEquals("item-1", publicData.getString("itemId"));
                                Assertions.assertEquals(token, publicData.getString("token"));
                                Assertions.assertEquals("KORI", publicData.getJsonObject("payload").getString("asset"));
                                tc.completeNow();
                            })));
                    })));
        }

        @Test
        @DisplayName("보안 센터 조회/저장 - proof log와 상태를 서버 authoritative로 동기화")
        void getAndUpdateOfflinePayTrustCenter(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject updateBody = new JsonObject()
                .put("platform", "android")
                .put("deviceName", "Galaxy Test")
                .put("teeAvailable", true)
                .put("keySigningActive", true)
                .put("deviceRegistrationId", "device-reg-1")
                .put("faceAvailable", true)
                .put("fingerprintAvailable", true)
                .put("authBindingKey", "binding-key-1")
                .put("lastVerifiedAuthMethod", "FACE_ID")
                .put("proofLogs", new io.vertx.core.json.JsonArray()
                    .add(new JsonObject()
                        .put("id", "log-1")
                        .put("eventType", "REQUEST_SENT")
                        .put("eventStatus", "ACKNOWLEDGED")
                        .put("message", "sent")
                        .put("createdAt", "2026-03-30T08:00:00")));

            reqPut(getUrl("/offline-pay/trust-center"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(updateBody, tc.succeeding(updateRes -> tc.verify(() -> {
                    JsonObject updatedData = updateRes.bodyAsJsonObject().getJsonObject("data");
                    Assertions.assertEquals("android", updatedData.getString("platform"));
                    Assertions.assertTrue(updatedData.getBoolean("teeAvailable"));
                    Assertions.assertEquals("SYNCED", updatedData.getString("syncStatus"));
                    Assertions.assertNotNull(updatedData.getString("lastSyncedAt"));
                    Assertions.assertTrue(updatedData.getJsonArray("statusLogs").size() >= 1);

                    reqGet(getUrl("/offline-pay/trust-center"))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(getRes -> tc.verify(() -> {
                            JsonObject persistedData = getRes.bodyAsJsonObject().getJsonObject("data");
                            Assertions.assertEquals("Galaxy Test", persistedData.getString("deviceName"));
                            Assertions.assertEquals("FACE_ID", persistedData.getString("lastVerifiedAuthMethod"));
                            Assertions.assertNotNull(persistedData.getJsonArray("proofLogs"));
                            Assertions.assertEquals("log-1", persistedData.getJsonArray("proofLogs").getJsonObject(0).getString("id"));
                            Assertions.assertNotNull(persistedData.getJsonArray("statusLogs"));
                            Assertions.assertTrue(persistedData.getJsonArray("statusLogs").size() >= 1);
                            Assertions.assertEquals("SYNCED", persistedData.getString("syncStatus"));
                            tc.completeNow();
                        })));
                })));
        }

        @Test
        @DisplayName("알림 센터 조회/저장 - 최근 발송 결과를 서버 authoritative로 동기화")
        void getAndUpdateOfflinePayNotificationCenter(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject updateBody = new JsonObject()
                .put("logs", new io.vertx.core.json.JsonArray()
                    .add(new JsonObject()
                        .put("id", "notification-log-1")
                        .put("category", "PAYMENT_COMPLETED")
                        .put("eventStatus", "DELIVERED")
                        .put("title", "결제 완료 알림")
                        .put("message", "결제가 정상적으로 완료되었습니다.")
                        .put("createdAt", "2026-03-30T09:00:00")));

            reqPut(getUrl("/offline-pay/notification-center"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(updateBody, tc.succeeding(updateRes -> tc.verify(() -> {
                    JsonObject updatedData = updateRes.bodyAsJsonObject().getJsonObject("data");
                    Assertions.assertNotNull(updatedData.getJsonArray("logs"));
                    Assertions.assertEquals("notification-log-1", updatedData.getJsonArray("logs").getJsonObject(0).getString("id"));

                    reqGet(getUrl("/offline-pay/notification-center"))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(getRes -> tc.verify(() -> {
                            JsonObject persistedData = getRes.bodyAsJsonObject().getJsonObject("data");
                            Assertions.assertEquals("PAYMENT_COMPLETED", persistedData.getJsonArray("logs").getJsonObject(0).getString("category"));
                            tc.completeNow();
                        })));
                })));
        }

        @Test
        @DisplayName("정산 센터 조회/저장 - 최근 원장 반영 결과를 서버 authoritative로 동기화")
        void getAndUpdateOfflinePaySettlementCenter(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject updateBody = new JsonObject()
                .put("logs", new io.vertx.core.json.JsonArray()
                    .add(new JsonObject()
                        .put("id", "settlement-log-1")
                        .put("category", "SETTLEMENT")
                        .put("eventStatus", "COMPLETED")
                        .put("title", "정산 완료")
                        .put("message", "내부 원장 반영이 완료되었습니다.")
                        .put("requestId", "request-1")
                        .put("settlementId", "settlement-1")
                        .put("createdAt", "2026-03-30T09:10:00")));

            reqPut(getUrl("/offline-pay/settlement-center"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(updateBody, tc.succeeding(updateRes -> tc.verify(() -> {
                    JsonObject updatedData = updateRes.bodyAsJsonObject().getJsonObject("data");
                    Assertions.assertNotNull(updatedData.getJsonArray("logs"));
                    Assertions.assertEquals("settlement-log-1", updatedData.getJsonArray("logs").getJsonObject(0).getString("id"));

                    reqGet(getUrl("/offline-pay/settlement-center"))
                        .bearerTokenAuthentication(accessToken)
                        .send(tc.succeeding(getRes -> tc.verify(() -> {
                            JsonObject persistedData = getRes.bodyAsJsonObject().getJsonObject("data");
                            JsonObject logItem = persistedData.getJsonArray("logs").getJsonObject(0);
                            Assertions.assertEquals("COMPLETED", logItem.getString("eventStatus"));
                            Assertions.assertEquals("settlement-1", logItem.getString("settlementId"));
                            tc.completeNow();
                        })));
                })));
        }
    }

    @Nested
    @DisplayName("로그인 비밀번호 변경 테스트")
    class LoginPasswordTest {

        @Test
        @Order(1)
        @DisplayName("성공 - 올바른 이메일 인증 코드로 로그인 비밀번호 변경")
        void successChangeLoginPassword(VertxTestContext tc) {
            // testuser2(ID:2)의 이메일 인증 코드: 222222 (R__03_test_email_verifications.sql)
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject body = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "new_password_8chars");

            reqPost(getUrl("/login-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    log.info("Change login password response: {}", res.bodyAsJsonObject());
                    expectSuccessAndGetResponse(res, refVoid);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(2)
        @DisplayName("실패 - 잘못된 코드로 로그인 비밀번호 변경")
        void failInvalidCode(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject body = new JsonObject()
                .put("code", "999999")
                .put("newPassword", "new_password_8chars");

            reqPost(getUrl("/login-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(3)
        @DisplayName("실패 - 8자 미만 비밀번호")
        void failPasswordTooShort(VertxTestContext tc) {
            String accessToken = getAccessTokenOfUser(2L);

            JsonObject body = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "short77");  // 7자 (8자 미만)

            reqPost(getUrl("/login-password"))
                .bearerTokenAuthentication(accessToken)
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 400);
                    tc.completeNow();
                })));
        }

        @Test
        @Order(4)
        @DisplayName("실패 - 인증 없이 로그인 비밀번호 변경")
        void failNoAuth(VertxTestContext tc) {
            JsonObject body = new JsonObject()
                .put("code", "222222")
                .put("newPassword", "new_password_8chars");

            reqPost(getUrl("/login-password"))
                .sendJson(body, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}
