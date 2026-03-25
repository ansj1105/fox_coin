package com.foxya.coin.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foxya.coin.common.utils.ErrorHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

@ExtendWith(VertxExtension.class)
class InternalWalletHandlerTest {

    @Test
    void returnsCanonicalWalletSnapshot(Vertx vertx, VertxTestContext tc) {
        WalletService walletService = mock(WalletService.class);
        when(walletService.getCanonicalWalletSnapshot(1L, "KORI")).thenReturn(Future.succeededFuture(
            WalletService.CanonicalWalletSnapshot.builder()
                .userId(1L)
                .currencyCode("KORI")
                .totalBalance(new BigDecimal("198.253587"))
                .lockedBalance(new BigDecimal("1.001"))
                .walletCount(1)
                .canonicalBasis("FOX_CLIENT_VISIBLE_TOTAL_KORI")
                .build()
        ));

        InternalWalletHandler handler = new InternalWalletHandler(vertx, walletService, "secret-key");
        Router router = Router.router(vertx);
        router.mountSubRouter("/", handler.getRouter());
        router.route().failureHandler(ErrorHandler::handle);

        WebClient client = WebClient.create(vertx);
        HttpServer server = vertx.createHttpServer().requestHandler(router);

        server.listen(0).compose(httpServer ->
            client.get(httpServer.actualPort(), "localhost", "/snapshot?userId=1&currencyCode=KORI")
                .putHeader("X-Internal-Api-Key", "secret-key")
                .send()
                .compose(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.bodyAsJsonObject().getString("currencyCode")).isEqualTo("KORI");
                    assertThat(response.bodyAsJsonObject().getString("totalBalance")).isEqualTo("198.253587");
                    assertThat(response.bodyAsJsonObject().getString("canonicalBasis")).isEqualTo("FOX_CLIENT_VISIBLE_TOTAL_KORI");
                    return server.close();
                })
        ).onSuccess(v -> tc.completeNow())
         .onFailure(tc::failNow);
    }
}
