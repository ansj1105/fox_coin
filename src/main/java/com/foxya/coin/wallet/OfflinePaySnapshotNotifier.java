package com.foxya.coin.wallet;

import com.foxya.coin.common.alert.TelegramAlertNotifier;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class OfflinePaySnapshotNotifier {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    private final Vertx vertx;
    private final WebClient webClient;
    private final String baseUrl;
    private final String internalApiKey;
    private final TelegramAlertNotifier telegramNotifier;
    private final int failureThreshold;
    private final long cooldownMs;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicBoolean circuitOpenNotified = new AtomicBoolean(false);
    private volatile long circuitOpenedAt = 0L;

    public OfflinePaySnapshotNotifier(
        Vertx vertx,
        WebClient webClient,
        String baseUrl,
        String internalApiKey,
        TelegramAlertNotifier telegramNotifier,
        int failureThreshold,
        long cooldownMs
    ) {
        this.vertx = vertx;
        this.webClient = webClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
        this.telegramNotifier = telegramNotifier;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMs = Math.max(1_000L, cooldownMs);
    }

    public static OfflinePaySnapshotNotifier fromEnv(Vertx vertx, WebClient webClient) {
        String baseUrl = System.getenv("OFFLINE_PAY_BASE_URL");
        String internalApiKey = System.getenv("OFFLINE_PAY_INTERNAL_API_KEY");
        TelegramAlertNotifier telegramNotifier = new TelegramAlertNotifier(
            webClient,
            System.getenv("TELEGRAM_BOT_TOKEN"),
            System.getenv("TELEGRAM_CHAT_ID")
        );
        return new OfflinePaySnapshotNotifier(
            vertx,
            webClient,
            baseUrl,
            internalApiKey,
            telegramNotifier,
            parseIntEnv("OFFLINE_PAY_NOTIFY_FAILURE_THRESHOLD", 3),
            parseLongEnv("OFFLINE_PAY_NOTIFY_COOLDOWN_MS", 60000L)
        );
    }

    public void notifyWalletRefreshAsync(Long userId, String reason) {
        if (userId == null || baseUrl.isBlank() || internalApiKey.isBlank()) {
            return;
        }
        if (isCircuitOpen()) {
            log.warn("offline_pay snapshot notifier circuit open - skipping wallet refresh notify, userId={}", userId);
            return;
        }
        vertx.runOnContext(ignored -> notifyWalletRefresh(userId, reason)
            .onSuccess(ignoredResult -> onSuccess())
            .onFailure(error -> onFailure(userId, reason, error)));
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

    private void onSuccess() {
        boolean recovered = circuitOpenedAt != 0L || consecutiveFailures.get() > 0;
        consecutiveFailures.set(0);
        circuitOpenedAt = 0L;
        circuitOpenNotified.set(false);
        if (recovered) {
            sendTelegram("[KORION] offline_pay notify recovered", "baseUrl=" + baseUrl + "\nmessage=wallet refresh notify recovered");
        }
    }

    private void onFailure(Long userId, String reason, Throwable error) {
        log.warn("Failed to notify offline_pay wallet refresh - userId={}", userId, error);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures < failureThreshold) {
            return;
        }
        if (circuitOpenedAt == 0L) {
            circuitOpenedAt = System.currentTimeMillis();
        }
        if (circuitOpenNotified.compareAndSet(false, true)) {
            String title = "[KORION] offline_pay notify circuit opened";
            String body = "userId=" + userId +
                "\nreason=" + normalizeReason(reason) +
                "\nfailures=" + failures +
                "\nbaseUrl=" + baseUrl +
                "\nerror=" + safeMessage(error);
            sendTelegram(title, body);
        }
    }

    private boolean isCircuitOpen() {
        if (circuitOpenedAt == 0L) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - circuitOpenedAt;
        if (elapsed < cooldownMs) {
            return true;
        }
        consecutiveFailures.set(0);
        circuitOpenedAt = 0L;
        circuitOpenNotified.set(false);
        return false;
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "unknown";
        }
        return error.getMessage();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "wallet_balance_changed";
        }
        return reason;
    }

    private void sendTelegram(String title, String body) {
        if (telegramNotifier == null || !telegramNotifier.isEnabled()) {
            return;
        }
        telegramNotifier.sendMessage(title, body)
            .onFailure(alertError -> log.warn("Failed to send offline_pay notifier telegram alert", alertError));
    }

    private static int parseIntEnv(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static long parseLongEnv(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
