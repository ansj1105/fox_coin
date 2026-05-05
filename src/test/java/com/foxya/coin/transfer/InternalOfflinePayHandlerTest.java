package com.foxya.coin.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foxya.coin.common.utils.ErrorHandler;
import com.foxya.coin.transfer.entities.InternalTransfer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class InternalOfflinePayHandlerTest {

    @Test
    void recordsOfflinePayHistoryWithInternalApiKey(Vertx vertx, VertxTestContext tc) {
        TransferService transferService = mock(TransferService.class);
        InternalOfflinePayHandler handler = new InternalOfflinePayHandler(vertx, transferService, "secret-key");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.mountSubRouter("/", handler.getRouter());
        router.route().failureHandler(ErrorHandler::handle);

        WebClient client = WebClient.create(vertx);
        HttpServer server = vertx.createHttpServer().requestHandler(router);

        InternalTransfer transfer = InternalTransfer.builder()
            .transferId("settlement-1")
            .status(InternalTransfer.STATUS_COMPLETED)
            .amount(new BigDecimal("12.5"))
            .build();

        when(transferService.recordOfflinePaySettlementHistory(
            77L,
            "settlement-1",
            "settlement-1",
            "batch-1",
            "collateral-1",
            "proof-1",
            "device-1",
            "KORI",
            "12.5",
            "SETTLED",
            "OFFLINE_PAY_CONFLICT"
        )).thenReturn(Future.succeededFuture(transfer));

        server.listen(0).compose(httpServer ->
            client.post(httpServer.actualPort(), "localhost", "/settlements/history")
                .putHeader("X-Internal-Api-Key", "secret-key")
                .sendJsonObject(new JsonObject()
                    .put("userId", 77L)
                    .put("settlementId", "settlement-1")
                    .put("batchId", "batch-1")
                    .put("collateralId", "collateral-1")
                    .put("proofId", "proof-1")
                    .put("deviceId", "device-1")
                    .put("assetCode", "KORI")
                    .put("amount", "12.5")
                    .put("settlementStatus", "SETTLED")
                    .put("historyType", "OFFLINE_PAY_CONFLICT"))
                .compose(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonObject payload = response.bodyAsJsonObject();
                    assertThat(payload.getString("status")).isEqualTo("OK");
                    assertThat(payload.getJsonObject("data").getString("status")).isEqualTo("OK");
                    assertThat(payload.getJsonObject("data").getString("message")).isEqualTo("history recorded");
                    verify(transferService).recordOfflinePaySettlementHistory(
                        77L,
                        "settlement-1",
                        "settlement-1",
                        "batch-1",
                        "collateral-1",
                        "proof-1",
                        "device-1",
                        "KORI",
                        "12.5",
                        "SETTLED",
                        "OFFLINE_PAY_CONFLICT"
                    );
                    return server.close();
                })
        ).onSuccess(v -> tc.completeNow())
         .onFailure(tc::failNow);
    }

    @Test
    void rejectsMissingApiKey(Vertx vertx, VertxTestContext tc) {
        TransferService transferService = mock(TransferService.class);
        InternalOfflinePayHandler handler = new InternalOfflinePayHandler(vertx, transferService, "secret-key");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.mountSubRouter("/", handler.getRouter());
        router.route().failureHandler(ErrorHandler::handle);

        WebClient client = WebClient.create(vertx);
        HttpServer server = vertx.createHttpServer().requestHandler(router);

        server.listen(0).compose(httpServer ->
            client.post(httpServer.actualPort(), "localhost", "/settlements/history")
                .sendJsonObject(new JsonObject().put("userId", 77L))
                .compose(response -> {
                    assertThat(response.statusCode()).isEqualTo(401);
                    assertThat(response.bodyAsJsonObject().getString("error")).isEqualTo("Unauthorized");
                    return server.close();
                })
        ).onSuccess(v -> tc.completeNow())
         .onFailure(tc::failNow);
    }

    @Test
    void rejectsMissingRequiredBodyFields(Vertx vertx, VertxTestContext tc) {
        TransferService transferService = mock(TransferService.class);
        InternalOfflinePayHandler handler = new InternalOfflinePayHandler(vertx, transferService, "secret-key");
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.mountSubRouter("/", handler.getRouter());
        router.route().failureHandler(ErrorHandler::handle);

        WebClient client = WebClient.create(vertx);
        HttpServer server = vertx.createHttpServer().requestHandler(router);

        server.listen(0).compose(httpServer ->
            client.post(httpServer.actualPort(), "localhost", "/settlements/history")
                .putHeader("X-Internal-Api-Key", "secret-key")
                .sendJsonObject(new JsonObject().put("userId", 77L))
                .compose(response -> {
                    assertThat(response.statusCode()).isEqualTo(400);
                    assertThat(response.bodyAsJsonObject().getString("status")).isEqualTo("ERROR");
                    return server.close();
                })
        ).onSuccess(v -> tc.completeNow())
         .onFailure(tc::failNow);
    }
}
