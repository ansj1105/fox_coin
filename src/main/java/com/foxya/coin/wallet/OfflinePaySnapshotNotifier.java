package com.foxya.coin.wallet;

import com.foxya.coin.common.alert.TelegramAlertNotifier;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OfflinePaySnapshotNotifier {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";
    private static final String DEFAULT_REASON = "wallet_balance_changed";

    private final Vertx vertx;
    private final WebClient webClient;
    private final String baseUrl;
    private final String internalApiKey;
    private final TelegramAlertNotifier telegramNotifier;
    private final OfflinePayNotifyCircuitBreaker circuitBreaker;

    public OfflinePaySnapshotNotifier(
        Vertx vertx,
        WebClient webClient,
        String baseUrl,
        String internalApiKey,
        TelegramAlertNotifier telegramNotifier,
        OfflinePayNotifyCircuitBreaker circuitBreaker
    ) {
        this.vertx = vertx;
        this.webClient = webClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
        this.telegramNotifier = telegramNotifier;
        this.circuitBreaker = circuitBreaker;
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
            new OfflinePayNotifyCircuitBreaker(
                parseBooleanEnv("OFFLINE_PAY_NOTIFY_CIRCUIT_BREAKER_ENABLED", true),
                parseIntEnv("OFFLINE_PAY_NOTIFY_FAILURE_THRESHOLD", 3),
                parseLongEnv("OFFLINE_PAY_NOTIFY_COOLDOWN_MS", 60000L)
            )
        );
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
        long nowMs = System.currentTimeMillis();
        OfflinePayNotifyCircuitBreaker.PermitDecision permitDecision = circuitBreaker.tryAcquire(nowMs);
        if (permitDecision == OfflinePayNotifyCircuitBreaker.PermitDecision.REJECT_OPEN) {
            return Future.failedFuture(
                "offline_pay wallet refresh notify circuit open: remainingMs=" + circuitBreaker.openRemainingMs(nowMs)
            );
        }

        String normalizedReason = reason == null || reason.isBlank() ? DEFAULT_REASON : reason;
        JsonObject body = new JsonObject()
            .put("userId", userId)
            .put("reason", normalizedReason);
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
            })
            .compose(ignored -> handleSuccess(userId, normalizedReason))
            .recover(error -> handleFailure(userId, normalizedReason, error));
    }

    private Future<Void> handleSuccess(Long userId, String reason) {
        OfflinePayNotifyCircuitBreaker.Transition transition = circuitBreaker.recordSuccess();
        if (transition == OfflinePayNotifyCircuitBreaker.Transition.CLOSED) {
            log.info("offline_pay snapshot notify circuit recovered - userId={}, reason={}", userId, reason);
            return sendRecoveryAlert(userId, reason)
                .recover(alertError -> {
                    log.warn("Failed to send offline_pay notifier recovery telegram alert", alertError);
                    return Future.succeededFuture();
                });
        }
        return Future.succeededFuture();
    }

    private Future<Void> handleFailure(Long userId, String reason, Throwable error) {
        long nowMs = System.currentTimeMillis();
        OfflinePayNotifyCircuitBreaker.Transition transition = circuitBreaker.recordFailure(nowMs);
        Future<Void> alertFuture = transition == OfflinePayNotifyCircuitBreaker.Transition.OPENED
            ? sendOpenAlert(userId, reason, error, nowMs)
            : Future.succeededFuture();

        return alertFuture
            .recover(alertError -> {
                log.warn("Failed to send offline_pay notifier telegram alert", alertError);
                return Future.succeededFuture();
            })
            .compose(ignored -> Future.failedFuture(error));
    }

    private Future<Void> sendOpenAlert(Long userId, String reason, Throwable error, long nowMs) {
        if (telegramNotifier == null || !telegramNotifier.isEnabled()) {
            return Future.succeededFuture();
        }

        String body = "service=foxya_coin_service" +
            "\ntarget=offline_pay" +
            "\nevent=circuit_opened" +
            "\nbaseUrl=" + baseUrl +
            "\nuserId=" + userId +
            "\nreason=" + reason +
            "\nconsecutiveFailures=" + circuitBreaker.consecutiveFailures() +
            "\nopenRemainingMs=" + circuitBreaker.openRemainingMs(nowMs) +
            "\nerror=" + safeMessage(error);
        return telegramNotifier.sendMessage("[FOX] offline_pay snapshot notify circuit opened", body);
    }

    private Future<Void> sendRecoveryAlert(Long userId, String reason) {
        if (telegramNotifier == null || !telegramNotifier.isEnabled()) {
            return Future.succeededFuture();
        }

        String body = "service=foxya_coin_service" +
            "\ntarget=offline_pay" +
            "\nevent=circuit_recovered" +
            "\nbaseUrl=" + baseUrl +
            "\nuserId=" + userId +
            "\nreason=" + reason;
        return telegramNotifier.sendMessage("[FOX] offline_pay snapshot notify recovered", body);
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "unknown";
        }
        return error.getMessage();
    }

    private static boolean parseBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
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
