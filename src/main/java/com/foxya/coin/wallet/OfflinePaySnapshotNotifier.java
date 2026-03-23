package com.foxya.coin.wallet;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OfflinePaySnapshotNotifier {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    private final Vertx vertx;
    private final WebClient webClient;
    private final String baseUrl;
    private final String internalApiKey;

    public OfflinePaySnapshotNotifier(Vertx vertx, WebClient webClient, String baseUrl, String internalApiKey) {
        this.vertx = vertx;
        this.webClient = webClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
    }

    public static OfflinePaySnapshotNotifier fromEnv(Vertx vertx, WebClient webClient) {
        String baseUrl = System.getenv("OFFLINE_PAY_BASE_URL");
        String internalApiKey = System.getenv("OFFLINE_PAY_INTERNAL_API_KEY");
        return new OfflinePaySnapshotNotifier(vertx, webClient, baseUrl, internalApiKey);
    }

    public void notifyWalletRefreshAsync(Long userId, String reason) {
        if (userId == null || baseUrl.isBlank() || internalApiKey.isBlank()) {
            return;
        }
        vertx.runOnContext(ignored -> notifyWalletRefresh(userId, reason)
            .onFailure(error -> log.warn("Failed to notify offline_pay wallet refresh - userId={}", userId, error)));
    }

    public Future<Void> notifyWalletRefresh(Long userId, String reason) {
        if (userId == null || baseUrl.isBlank() || internalApiKey.isBlank()) {
            return Future.succeededFuture();
        }
        JsonObject body = new JsonObject()
            .put("userId", userId)
            .put("reason", reason == null || reason.isBlank() ? "wallet_balance_changed" : reason);
        return webClient
            .postAbs(baseUrl + "/api/snapshots/internal/wallet-refresh")
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
            .putHeader(HEADER_API_KEY, internalApiKey)
            .sendJsonObject(body)
            .compose(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return Future.succeededFuture();
                }
                return Future.failedFuture("offline_pay wallet refresh notify failed: HTTP " + response.statusCode());
            });
    }
}
