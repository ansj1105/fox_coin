package com.foxya.coin.transfer;

import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class KorionWithdrawalBridgeClient implements WithdrawalBridgeClient {

    private static final Set<String> SUPPORTED_CHAINS = Set.of("TRON");
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 429, 500, 502, 503, 504);
    private static final Set<String> RETRYABLE_ERROR_MARKERS = Set.of(
        "timeout",
        "timed out",
        "connection refused",
        "connection reset",
        "connection was closed",
        "connect exception",
        "temporarily unavailable"
    );
    private static final long REQUEST_TIMEOUT_MS = 10_000L;
    private static final int MAX_ATTEMPTS = 2;

    private final WebClient webClient;
    private final String requestUrl;
    private final Set<String> supportedCurrencyCodes;

    public KorionWithdrawalBridgeClient(WebClient webClient, String baseUrl, String path, Set<String> supportedCurrencyCodes) {
        this.webClient = webClient;
        this.requestUrl = normalizeUrl(baseUrl, path);
        this.supportedCurrencyCodes = supportedCurrencyCodes.stream()
            .map(code -> code.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public static KorionWithdrawalBridgeClient fromEnv(WebClient webClient) {
        String baseUrl = readEnv("KORION_WITHDRAW_API_URL", "http://korion-service:3000");
        String path = readEnv("KORION_WITHDRAW_API_PATH", "/api/withdrawals");
        String currencyCodes = readEnv("KORION_WITHDRAW_BRIDGE_CURRENCY_CODES", "KORI,FOXYA");
        Set<String> supportedCurrencyCodes = Arrays.stream(currencyCodes.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
        return new KorionWithdrawalBridgeClient(webClient, baseUrl, path, supportedCurrencyCodes);
    }

    @Override
    public boolean supports(String currencyCode, String chain) {
        if (currencyCode == null || chain == null) {
            return false;
        }
        return SUPPORTED_CHAINS.contains(chain.toUpperCase(Locale.ROOT))
            && supportedCurrencyCodes.contains(currencyCode.toUpperCase(Locale.ROOT));
    }

    @Override
    public Future<String> requestWithdrawal(Long userId, String transferId, ExternalTransferRequestDto request, String requestIp) {
        JsonObject payload = new JsonObject()
            .put("userId", String.valueOf(userId))
            .put("amount", request.getAmount())
            .put("toAddress", request.getToAddress())
            .put("clientIp", requestIp);

        return requestWithdrawal(userId, transferId, payload, 1)
            .recover(error -> {
                log.error("coin_manage withdrawal bridge failed - transferId: {}", transferId, error);
                return Future.failedFuture(error);
            });
    }

    private Future<String> requestWithdrawal(Long userId, String transferId, JsonObject payload, int attempt) {
        return webClient.postAbs(requestUrl)
            .timeout(REQUEST_TIMEOUT_MS)
            .putHeader("Content-Type", "application/json")
            .putHeader("Idempotency-Key", transferId)
            .sendJsonObject(payload)
            .compose(response -> handleResponse(transferId, response, attempt))
            .recover(error -> {
                if (attempt < MAX_ATTEMPTS && isRetryableTransportError(error)) {
                    log.warn("coin_manage withdrawal bridge retry - transferId: {}, attempt: {}", transferId, attempt + 1, error);
                    return requestWithdrawal(userId, transferId, payload, attempt + 1);
                }
                return Future.failedFuture(error);
            });
    }

    private Future<String> handleResponse(String transferId, HttpResponse<?> response, int attempt) {
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            String body = response.bodyAsString();
            String message = "coin_manage withdrawal request failed - status: " + response.statusCode() + ", body: " + body;
            return Future.failedFuture(message);
        }

        JsonObject body = response.bodyAsJsonObject();
        JsonObject withdrawal = body != null ? body.getJsonObject("withdrawal") : null;
        String withdrawalId = withdrawal != null
            ? withdrawal.getString("withdrawalId", withdrawal.getString("id"))
            : null;

        if (withdrawalId == null || withdrawalId.isBlank()) {
            return Future.failedFuture("coin_manage withdrawal response missing withdrawalId");
        }

        log.info("coin_manage withdrawal bridge success - transferId: {}, withdrawalId: {}", transferId, withdrawalId);
        return Future.succeededFuture(withdrawalId);
    }

    private boolean isRetryableTransportError(Throwable error) {
        String message = collectMessages(error).toLowerCase(Locale.ROOT);
        for (String marker : RETRYABLE_ERROR_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String collectMessages(Throwable error) {
        Set<Throwable> visited = new HashSet<>();
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null && visited.add(current)) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private static String normalizeUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private static String readEnv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
