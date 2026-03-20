package com.foxya.coin.admin;

import com.foxya.coin.common.HandlerTestBase;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class AdminDbBackupHandlerTest extends HandlerTestBase {

    AdminDbBackupHandlerTest() {
        super("/api/v2/admin/db-backup");
    }

    @Test
    @DisplayName("성공 - DB 백업 overview 응답")
    void successOverview(VertxTestContext tc) {
        String accessToken = getAccessTokenOfAdmin(3L);

        reqGet(getUrl("/overview"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(res -> tc.verify(() -> {
                expectSuccess(res);
                JsonObject data = res.bodyAsJsonObject().getJsonObject("data");
                assertThat(data).isNotNull();
                assertThat(data.getJsonObject("summary")).isNotNull();
                assertThat(data.getJsonArray("systems", new JsonArray())).isNotEmpty();
                JsonObject firstSystem = data.getJsonArray("systems").getJsonObject(0);
                assertThat(firstSystem.getJsonObject("currentNode")).isNotNull();
                assertThat(firstSystem.getJsonArray("replicas", new JsonArray())).isNotNull();
                tc.completeNow();
            })));
    }
}
