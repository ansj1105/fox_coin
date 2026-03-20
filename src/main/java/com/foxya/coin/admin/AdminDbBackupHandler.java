package com.foxya.coin.admin;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import io.vertx.core.Future;
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
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdminDbBackupHandler extends BaseHandler {

    private static final String DEFAULT_KORION_SYSTEM_API_URL = "http://korion-service:3000";

    private final WebClient webClient;
    private final JWTAuth jwtAuth;
    private final PgPool pool;
    private final String korionSystemApiBaseUrl;
    private final String korionSystemAdminApiKey;

    public AdminDbBackupHandler(Vertx vertx, WebClient webClient, JWTAuth jwtAuth, PgPool pool) {
        super(vertx);
        this.webClient = webClient;
        this.jwtAuth = jwtAuth;
        this.pool = pool;
        this.korionSystemApiBaseUrl = readEnv("KORION_SYSTEM_API_BASE_URL", DEFAULT_KORION_SYSTEM_API_URL);
        this.korionSystemAdminApiKey = readEnv("KORION_SYSTEM_ADMIN_API_KEY", readEnv("KORION_WITHDRAW_ADMIN_API_KEY", ""));
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
        Future<JsonObject> foxyaFuture = fetchLocalDbStatus()
            .recover(error -> {
                log.warn("db backup foxya status fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackSystem("foxya", "Foxya DB", "foxya_db_status_unavailable", error));
            });
        Future<JsonObject> korionFuture = fetchKorionDbStatus()
            .recover(error -> {
                log.warn("db backup korion status fetch failed: {}", error.getMessage());
                return Future.succeededFuture(fallbackSystem("korion", "Korion DB", "korion_db_status_unavailable", error));
            });

        response(ctx, Future.all(foxyaFuture, korionFuture).map(result -> {
            JsonObject foxya = foxyaFuture.result();
            JsonObject korion = korionFuture.result();
            JsonArray systems = new JsonArray().add(foxya).add(korion);
            JsonArray warnings = collectWarnings(foxya, korion);

            int syncProtectedCount = 0;
            int archiveEnabledCount = 0;
            for (int i = 0; i < systems.size(); i++) {
                JsonObject system = systems.getJsonObject(i);
                JsonObject currentNode = system.getJsonObject("currentNode", new JsonObject());
                if (currentNode.getInteger("healthySyncReplicaCount", 0) > 0) {
                    syncProtectedCount++;
                }
                String archiveMode = currentNode.getString("archiveMode", "");
                if ("on".equalsIgnoreCase(archiveMode) || "always".equalsIgnoreCase(archiveMode)) {
                    archiveEnabledCount++;
                }
            }

            return new JsonObject()
                .put("summary", new JsonObject()
                    .put("systemCount", systems.size())
                    .put("syncProtectedCount", syncProtectedCount)
                    .put("archiveEnabledCount", archiveEnabledCount)
                    .put("warningCount", warnings.size()))
                .put("systems", systems)
                .put("warnings", warnings);
        }));
    }

    private Future<JsonObject> fetchLocalDbStatus() {
        return pool.query("""
            SELECT
              'foxya' AS system_id,
              current_database() AS database_name,
              (current_setting('transaction_read_only') = 'on') AS transaction_read_only,
              COALESCE(current_setting('wal_level', true), 'unknown') AS wal_level,
              COALESCE(current_setting('archive_mode', true), 'unknown') AS archive_mode,
              COALESCE(current_setting('archive_command', true), '') AS archive_command,
              COALESCE(current_setting('archive_timeout', true), '') AS archive_timeout,
              COALESCE(current_setting('synchronous_standby_names', true), '') AS synchronous_standby_names,
              COALESCE((SELECT COUNT(*) FROM pg_stat_replication WHERE state = 'streaming'), 0) AS attached_replica_count,
              COALESCE((SELECT COUNT(*) FROM pg_stat_replication WHERE state = 'streaming' AND sync_state IN ('sync', 'quorum')), 0) AS healthy_sync_replica_count
        """).execute().compose(rows -> pool.query("""
            SELECT
              application_name,
              client_addr::text AS client_address,
              state,
              sync_state,
              COALESCE(pg_wal_lsn_diff(sent_lsn, replay_lsn)::text, '0') AS replay_lag_bytes
            FROM pg_stat_replication
            ORDER BY application_name ASC
        """).execute().map(replicas -> mapSystem("Foxya DB", rows, replicas)));
    }

    private JsonObject mapSystem(String displayName, RowSet<Row> statusRows, RowSet<Row> replicaRows) {
        Row row = statusRows.iterator().next();
        String archiveMode = row.getString("archive_mode");
        String archiveTimeout = row.getString("archive_timeout");
        Integer archiveTimeoutSec = parseArchiveTimeoutSec(archiveTimeout);
        JsonArray replicas = new JsonArray();
        JsonArray notes = new JsonArray();

        if (Boolean.TRUE.equals(row.getBoolean("transaction_read_only"))) {
            notes.add("current node is read-only; confirm db-proxy is routing writes to the primary");
        }
        String synchronousStandbyNames = safeString(row, "synchronous_standby_names", "");
        if (synchronousStandbyNames.isBlank()) {
            notes.add("synchronous standby is not configured on the current node");
        }
        if ("off".equalsIgnoreCase(archiveMode)) {
            notes.add("archive_mode is off on the current node; WAL archive may be managed on a standby node");
        }

        for (Row replica : replicaRows) {
            replicas.add(new JsonObject()
                .put("applicationName", safeString(replica, "application_name", ""))
                .put("clientAddress", replica.getString("client_address"))
                .put("state", safeString(replica, "state", "unknown"))
                .put("syncState", safeString(replica, "sync_state", "unknown"))
                .put("replayLagBytes", safeString(replica, "replay_lag_bytes", "0")));
        }

        return new JsonObject()
            .put("systemId", row.getString("system_id"))
            .put("displayName", displayName)
            .put("databaseName", row.getString("database_name"))
            .put("currentNode", new JsonObject()
                .put("transactionReadOnly", row.getBoolean("transaction_read_only"))
                .put("walLevel", safeString(row, "wal_level", "unknown"))
                .put("archiveMode", archiveMode)
                .put("archiveCommandConfigured", !safeString(row, "archive_command", "").isBlank() && !"(disabled)".equals(safeString(row, "archive_command", "")))
                .put("archiveTimeoutSec", archiveTimeoutSec)
                .put("synchronousStandbyNames", synchronousStandbyNames)
                .put("attachedReplicaCount", safeInteger(row, "attached_replica_count", 0))
                .put("healthySyncReplicaCount", safeInteger(row, "healthy_sync_replica_count", 0)))
            .put("replicas", replicas)
            .put("notes", notes);
    }

    private Future<JsonObject> fetchKorionDbStatus() {
        return getJson(normalizeUrl(korionSystemApiBaseUrl, "/api/system/db-backup/status"), korionSystemAdminApiKey, "X-Api-Key")
            .map(body -> body.copy().put("displayName", "Korion DB"));
    }

    private Future<JsonObject> getJson(String url, String apiKey, String apiKeyHeader) {
        HttpRequest<io.vertx.core.buffer.Buffer> request = webClient.getAbs(url).timeout(8000);
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

    private JsonObject fallbackSystem(String systemId, String displayName, String warningCode, Throwable error) {
        return new JsonObject()
            .put("systemId", systemId)
            .put("displayName", displayName)
            .put("databaseName", "")
            .put("currentNode", new JsonObject()
                .put("transactionReadOnly", false)
                .put("walLevel", "unknown")
                .put("archiveMode", "unknown")
                .put("archiveCommandConfigured", false)
                .put("archiveTimeoutSec", (Integer) null)
                .put("synchronousStandbyNames", "")
                .put("attachedReplicaCount", 0)
                .put("healthySyncReplicaCount", 0))
            .put("replicas", new JsonArray())
            .put("notes", new JsonArray())
            .put("warnings", new JsonArray().add(new JsonObject()
                .put("code", warningCode)
                .put("message", error != null && error.getMessage() != null ? error.getMessage() : "unknown upstream error")));
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

    private Integer parseArchiveTimeoutSec(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            if (raw.endsWith("s")) {
                return Integer.parseInt(raw.substring(0, raw.length() - 1));
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String safeString(Row row, String column, String fallback) {
        String value = row.getString(column);
        return value != null ? value : fallback;
    }

    private Integer safeInteger(Row row, String column, int fallback) {
        Integer value = row.getInteger(column);
        return value != null ? value : fallback;
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
}
