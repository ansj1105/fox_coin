package com.foxya.coin.wallet;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Slf4j
public class OfflinePayCollateralReserveClient {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    private final WebClient webClient;
    private final String offlinePayBaseUrl;
    private final String internalApiKey;

    public OfflinePayCollateralReserveClient(WebClient webClient, String offlinePayBaseUrl, String internalApiKey) {
        this.webClient = webClient;
        this.offlinePayBaseUrl = offlinePayBaseUrl == null ? "" : offlinePayBaseUrl.trim();
        this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
    }

    public Future<BigDecimal> getLockedAmount(Long userId, String assetCode) {
        if (userId == null || userId <= 0 || offlinePayBaseUrl.isBlank() || internalApiKey.isBlank()) {
            return Future.succeededFuture(BigDecimal.ZERO);
        }

        String normalizedAssetCode = assetCode == null || assetCode.isBlank() ? "KORI" : assetCode.trim().toUpperCase();
        String url = offlinePayBaseUrl
            + "/api/internal/collateral/summary?userId="
            + userId
            + "&assetCode="
            + java.net.URLEncoder.encode(normalizedAssetCode, StandardCharsets.UTF_8);

        return webClient.getAbs(url)
            .putHeader(HEADER_API_KEY, internalApiKey)
            .send()
            .map(response -> {
                if (response.statusCode() >= 400) {
                    log.warn("offline_pay collateral summary request failed: userId={}, assetCode={}, status={}",
                        userId, normalizedAssetCode, response.statusCode());
                    return BigDecimal.ZERO;
                }
                JsonObject body = response.bodyAsJsonObject();
                if (body == null) {
                    return BigDecimal.ZERO;
                }
                return parseDecimal(body.getString("lockedAmount"));
            })
            .recover(error -> {
                log.warn("offline_pay collateral summary request failed: userId={}, assetCode={}",
                    userId, normalizedAssetCode, error);
                return Future.succeededFuture(BigDecimal.ZERO);
            });
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }
}
