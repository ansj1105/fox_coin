package com.foxya.coin.admin;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.utils.AuthUtils;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.JWTAuthHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
public class AdminWalletOpsHandler extends BaseHandler {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_MONITORING_LIMIT = 12;
    private static final String DEFAULT_KORION_SYSTEM_API_URL = "http://korion-service:3000";
    private static final String DEFAULT_LEDGER_SIGNER_API_URL = "http://ledger-signer:3000";

    private final WebClient webClient;
    private final JWTAuth jwtAuth;
    private final String korionSystemApiBaseUrl;
    private final String korionSystemAdminApiKey;
    private final String ledgerSignerApiBaseUrl;
    private final String ledgerSignerApiKey;

    public AdminWalletOpsHandler(Vertx vertx, WebClient webClient, JWTAuth jwtAuth) {
        super(vertx);
        this.webClient = webClient;
        this.jwtAuth = jwtAuth;
        this.korionSystemApiBaseUrl = readEnv("KORION_SYSTEM_API_BASE_URL", DEFAULT_KORION_SYSTEM_API_URL);
        this.korionSystemAdminApiKey = readEnv("KORION_SYSTEM_ADMIN_API_KEY", readEnv("KORION_WITHDRAW_ADMIN_API_KEY", ""));
        this.ledgerSignerApiBaseUrl = readEnv("LEDGER_SIGNER_API_URL", DEFAULT_LEDGER_SIGNER_API_URL);
        this.ledgerSignerApiKey = readEnv("LEDGER_SIGNER_API_KEY", readEnv("KORION_WITHDRAW_SIGNER_API_KEY", ""));
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.get("/overview")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.SUPER_ADMIN))
            .handler(this::getOverview);
        return router;
    }

    private void getOverview(RoutingContext ctx) {
        Query query;
        try {
            query = parseQuery(ctx);
        } catch (BadRequestException error) {
            ctx.fail(400, error);
            return;
        }

        Future<JsonObject> monitoringFuture = fetchKorionMonitoringPoints(query)
            .recover(error -> {
                log.warn("wallet ops monitoring fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackArrayResponse("items", "monitoring_points_unavailable", error));
            });
        Future<JsonObject> feeFuture = fetchKorionFeeSnapshots(query.days)
            .recover(error -> {
                log.warn("wallet ops fee snapshot fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackArrayResponse("items", "fee_snapshots_unavailable", error));
            });
        Future<JsonObject> outboxFuture = fetchKorionOutboxSummary()
            .recover(error -> {
                log.warn("wallet ops outbox fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackObjectResponse("summary", "outbox_summary_unavailable", error));
            });
        Future<JsonObject> consumerFuture = fetchKorionEventConsumers()
            .recover(error -> {
                log.warn("wallet ops event consumer fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackObjectResponse("summary", "event_consumer_summary_unavailable", error));
            });
        Future<JsonObject> signerFuture = fetchLedgerSignerObservability()
            .recover(error -> {
                log.warn("wallet ops signer observability fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackObjectResponse("summary", "signer_observability_unavailable", error));
            });

        response(ctx, Future.all(monitoringFuture, feeFuture, outboxFuture, consumerFuture, signerFuture)
            .map(result -> buildOverviewResponse(
                monitoringFuture.result(),
                feeFuture.result(),
                outboxFuture.result(),
                consumerFuture.result(),
                signerFuture.result()
            )));
    }

    private Query parseQuery(RoutingContext ctx) {
        MultiMap params = ctx.queryParams();
        int days = parsePositiveInt(params.get("days"), DEFAULT_DAYS, MAX_DAYS, "days");
        String startDate = trimToNull(params.get("startDate"));
        String endDate = trimToNull(params.get("endDate"));
        return new Query(days, startDate, endDate);
    }

    private JsonObject buildOverviewResponse(
        JsonObject monitoringBody,
        JsonObject feeBody,
        JsonObject outboxBody,
        JsonObject consumerBody,
        JsonObject signerBody
    ) {
        JsonObject outboxSummary = monitoringSafeObject(outboxBody, "summary");
        JsonObject consumerSummary = monitoringSafeObject(consumerBody, "summary");
        JsonObject signerSummary = monitoringSafeObject(signerBody, "summary");

        JsonArray monitoringItems = monitoringBody.getJsonArray("items", new JsonArray());
        JsonArray feeItems = feeBody.getJsonArray("items", new JsonArray());
        JsonArray warnings = collectWarnings(monitoringBody, feeBody, outboxBody, consumerBody, signerBody);

        JsonArray monitoringPoints = new JsonArray();
        for (int i = 0; i < monitoringItems.size(); i++) {
            JsonObject item = monitoringItems.getJsonObject(i);
            if (item == null) {
                continue;
            }
            monitoringPoints.add(new JsonObject()
                .put("snapshotId", item.getString("snapshotId"))
                .put("walletCode", item.getString("walletCode"))
                .put("tokenSymbol", item.getString("tokenSymbol", "KORI"))
                .put("tokenBalance", item.getString("tokenBalance"))
                .put("trxBalance", item.getString("trxBalance"))
                .put("status", item.getString("status", "ok"))
                .put("createdAt", item.getString("createdAt"))
            );
        }

        JsonArray feeSnapshots = new JsonArray();
        for (int i = 0; i < feeItems.size(); i++) {
            JsonObject item = feeItems.getJsonObject(i);
            if (item == null) {
                continue;
            }
            feeSnapshots.add(new JsonObject()
                .put("snapshotDate", item.getString("snapshotDate"))
                .put("ledgerFeeAmount", item.getString("ledgerFeeAmount", "0"))
                .put("actualFeeAmount", item.getString("actualFeeAmount", "0"))
                .put("gapFeeAmount", item.getString("gapFeeAmount", "0"))
                .put("status", item.getString("status", "balanced"))
            );
        }

        return new JsonObject()
            .put("summary", new JsonObject()
                .put("signerTotalRequests", signerSummary.getInteger("totalRequests", 0))
                .put("signerFailedRequests", signerSummary.getInteger("failedCount", 0))
                .put("outboxPendingCount", outboxSummary.getInteger("pendingCount", 0))
                .put("outboxDeadLetteredCount", outboxSummary.getInteger("deadLetteredCount", 0))
                .put("consumerFailureCount", consumerSummary.getInteger("failureCount", 0))
                .put("consumerDeadLetterCount", consumerSummary.getInteger("deadLetterCount", 0))
            )
            .put("monitoringPoints", monitoringPoints)
            .put("feeSnapshots", feeSnapshots)
            .put("warnings", warnings);
    }

    private JsonObject fallbackArrayResponse(String fieldName, String warningCode, Throwable error) {
        return new JsonObject()
            .put(fieldName, new JsonArray())
            .put("warnings", new JsonArray().add(buildWarning(warningCode, error)));
    }

    private JsonObject fallbackObjectResponse(String fieldName, String warningCode, Throwable error) {
        return new JsonObject()
            .put(fieldName, new JsonObject())
            .put("warnings", new JsonArray().add(buildWarning(warningCode, error)));
    }

    private JsonObject buildWarning(String warningCode, Throwable error) {
        return new JsonObject()
            .put("code", warningCode)
            .put("message", error != null && error.getMessage() != null ? error.getMessage() : "unknown upstream error");
    }

    private JsonArray collectWarnings(JsonObject... bodies) {
        JsonArray warnings = new JsonArray();
        for (JsonObject body : bodies) {
            if (body == null) {
                continue;
            }
            JsonArray bodyWarnings = body.getJsonArray("warnings");
            if (bodyWarnings == null) {
                continue;
            }
            for (int i = 0; i < bodyWarnings.size(); i++) {
                JsonObject warning = bodyWarnings.getJsonObject(i);
                if (warning != null) {
                    warnings.add(warning);
                }
            }
        }
        return warnings;
    }

    private Future<JsonObject> fetchKorionMonitoringPoints(Query query) {
        JsonObject requestQuery = new JsonObject().put("limit", DEFAULT_MONITORING_LIMIT);
        if (query.startDate != null) {
            requestQuery.put("createdFrom", query.startDate);
        } else {
            requestQuery.put("createdFrom", Instant.now().minus(query.days, ChronoUnit.DAYS).toString());
        }
        if (query.endDate != null) {
            requestQuery.put("createdTo", query.endDate);
        }
        return getJson(normalizeUrl(korionSystemApiBaseUrl, "/api/system/monitoring/history"), requestQuery, korionSystemAdminApiKey, "X-Api-Key");
    }

    private Future<JsonObject> fetchKorionFeeSnapshots(int days) {
        return getJson(
            normalizeUrl(korionSystemApiBaseUrl, "/api/system/network-fees/daily-snapshots"),
            new JsonObject().put("days", days),
            korionSystemAdminApiKey,
            "X-Api-Key"
        );
    }

    private Future<JsonObject> fetchKorionOutboxSummary() {
        return getJson(
            normalizeUrl(korionSystemApiBaseUrl, "/api/system/outbox"),
            new JsonObject().put("limit", 1),
            korionSystemAdminApiKey,
            "X-Api-Key"
        );
    }

    private Future<JsonObject> fetchKorionEventConsumers() {
        return getJson(
            normalizeUrl(korionSystemApiBaseUrl, "/api/system/event-consumers"),
            new JsonObject().put("limit", 1),
            korionSystemAdminApiKey,
            "X-Api-Key"
        );
    }

    private Future<JsonObject> fetchLedgerSignerObservability() {
        return getJson(
            normalizeUrl(ledgerSignerApiBaseUrl, "/api/internal/signer/observability"),
            new JsonObject(),
            ledgerSignerApiKey,
            "X-Internal-Api-Key"
        );
    }

    private Future<JsonObject> getJson(String url, JsonObject query, String apiKey, String apiKeyHeader) {
        HttpRequest<io.vertx.core.buffer.Buffer> request = webClient.getAbs(url).timeout(8000);
        if (query != null) {
            for (String fieldName : query.fieldNames()) {
                Object value = query.getValue(fieldName);
                if (value != null) {
                    request.addQueryParam(fieldName, String.valueOf(value));
                }
            }
        }
        if (apiKey != null && !apiKey.isBlank()) {
            request.putHeader(apiKeyHeader, apiKey);
        }
        request.putHeader("Accept", "application/json");
        return request.send().compose(this::parseJsonResponse);
    }

    private Future<JsonObject> parseJsonResponse(HttpResponse<io.vertx.core.buffer.Buffer> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Future.failedFuture("upstream request failed - status: " + response.statusCode() + ", body: " + response.bodyAsString());
        }
        JsonObject body = response.bodyAsJsonObject();
        if (body == null) {
            return Future.failedFuture("upstream response body missing");
        }
        return Future.succeededFuture(body);
    }

    private JsonObject monitoringSafeObject(JsonObject body, String fieldName) {
        if (body == null) {
            return new JsonObject();
        }
        JsonObject value = body.getJsonObject(fieldName);
        return value != null ? value : new JsonObject();
    }

    private int parsePositiveInt(String raw, int fallback, int max, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0 || parsed > max) {
                throw new BadRequestException(fieldName + " must be between 1 and " + max);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new BadRequestException(fieldName + " must be a number");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUrl(String baseUrl, String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private String readEnv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Query(int days, String startDate, String endDate) {}
}
