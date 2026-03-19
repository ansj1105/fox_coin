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
class AdminWalletOpsHandlerTest extends HandlerTestBase {

    AdminWalletOpsHandlerTest() {
        super("/api/v2/admin/wallet-ops");
    }

    @Test
    @DisplayName("성공 - 업스트림 실패 시에도 기본 wallet ops overview 응답")
    void successOverviewWithFallbackDefaults(VertxTestContext tc) {
        String accessToken = getAccessTokenOfAdmin(3L);

        reqGet(getUrl("/overview?days=7"))
            .bearerTokenAuthentication(accessToken)
            .send(tc.succeeding(res -> tc.verify(() -> {
                expectSuccess(res);
                JsonObject data = res.bodyAsJsonObject().getJsonObject("data");
                assertThat(data).isNotNull();
                assertThat(data.getJsonObject("summary")).isNotNull();
                assertThat(data.getJsonObject("summary").getInteger("signerTotalRequests")).isGreaterThanOrEqualTo(0);
                assertThat(data.getJsonObject("summary").getInteger("outboxPendingCount")).isGreaterThanOrEqualTo(0);
                assertThat(data.getJsonObject("summary").getInteger("consumerFailureCount")).isGreaterThanOrEqualTo(0);
                assertThat(data.getJsonArray("monitoringPoints", new JsonArray())).isNotNull();
                assertThat(data.getJsonArray("feeSnapshots", new JsonArray())).isNotNull();
                tc.completeNow();
            })));
    }
}
