package com.foxya.coin.notification;

import com.foxya.coin.common.HandlerTestBase;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class AdminNotificationHandlerTest extends HandlerTestBase {

    AdminNotificationHandlerTest() {
        super("/api/v1/admin/notifications");
    }

    @Nested
    @DisplayName("전체 테스트 알림 발송")
    class BroadcastNotificationTest {

        @Test
        @DisplayName("성공 - 전체 사용자 대상 큐 적재")
        void successBroadcast(VertxTestContext tc) {
            String accessToken = getAccessTokenOfAdmin(3L);
            JsonObject payload = new JsonObject()
                .put("type", "NOTICE")
                .put("title", "[TEST] broadcast")
                .put("message", "broadcast body");

            reqPost(getUrl("/test/broadcast"))
                .bearerTokenAuthentication(accessToken)
                .sendJsonObject(payload, tc.succeeding(res -> tc.verify(() -> {
                    expectSuccess(res);
                    JsonObject data = res.bodyAsJsonObject().getJsonObject("data");

                    assertThat(data.getString("requestId")).isNotBlank();
                    assertThat(data.getInteger("total")).isGreaterThanOrEqualTo(1);
                    assertThat(data.getInteger("enqueued")).isGreaterThanOrEqualTo(0);
                    assertThat(data.getInteger("enqueueFailed")).isGreaterThanOrEqualTo(0);
                    assertThat(data.getJsonObject("status")).isNotNull();

                    tc.completeNow();
                })));
        }

        @Test
        @DisplayName("실패 - 인증 없이 호출")
        void failWithoutAuth(VertxTestContext tc) {
            JsonObject payload = new JsonObject()
                .put("type", "NOTICE")
                .put("title", "[TEST] broadcast")
                .put("message", "broadcast body");

            reqPost(getUrl("/test/broadcast"))
                .sendJsonObject(payload, tc.succeeding(res -> tc.verify(() -> {
                    expectError(res, 401);
                    tc.completeNow();
                })));
        }
    }
}
