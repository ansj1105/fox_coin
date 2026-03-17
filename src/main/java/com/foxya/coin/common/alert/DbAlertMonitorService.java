package com.foxya.coin.common.alert;

import com.foxya.coin.common.metrics.MetricsCollector;
import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DbAlertMonitorService {

    private static final String SNAPSHOT_SQL = """
        SELECT
          current_database() AS database_name,
          current_setting('max_connections')::int AS max_connections,
          COALESCE((SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()), 0) AS total_connections,
          COALESCE((SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'active'), 0) AS active_connections,
          COALESCE((SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'), 0) AS lock_waits,
          COALESCE((SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event = 'SyncRep'), 0) AS sync_rep_waits,
          COALESCE((SELECT COUNT(*) FROM pg_stat_replication WHERE state = 'streaming'), 0) AS streaming_replicas,
          COALESCE((SELECT COUNT(*) FROM pg_stat_replication WHERE state = 'streaming' AND sync_state IN ('sync', 'quorum')), 0) AS healthy_sync_replicas,
          COALESCE(current_setting('synchronous_standby_names', true), '') AS synchronous_standby_names,
          (current_setting('transaction_read_only') = 'on') AS transaction_read_only,
          pg_is_in_recovery() AS in_recovery
        """;

    private final PgPool pool;
    private final MetricsCollector metricsCollector;
    private final TelegramAlertNotifier notifier;
    private final boolean monitorEnabled;
    private final double connectionRatioThreshold;
    private final int lockWaitThreshold;
    private final int syncRepWaitThreshold;
    private final int consecutiveBreaches;
    private final Map<String, AlertState> alertStates = new ConcurrentHashMap<>();

    public DbAlertMonitorService(PgPool pool,
                                 MetricsCollector metricsCollector,
                                 TelegramAlertNotifier notifier,
                                 boolean monitorEnabled,
                                 double connectionRatioThreshold,
                                 int lockWaitThreshold,
                                 int syncRepWaitThreshold,
                                 int consecutiveBreaches) {
        this.pool = pool;
        this.metricsCollector = metricsCollector;
        this.notifier = notifier;
        this.monitorEnabled = monitorEnabled;
        this.connectionRatioThreshold = connectionRatioThreshold;
        this.lockWaitThreshold = lockWaitThreshold;
        this.syncRepWaitThreshold = syncRepWaitThreshold;
        this.consecutiveBreaches = consecutiveBreaches;
    }

    public static DbAlertMonitorService fromEnv(PgPool pool, MetricsCollector metricsCollector, WebClient webClient) {
        TelegramAlertNotifier notifier = new TelegramAlertNotifier(
            webClient,
            System.getenv("TELEGRAM_BOT_TOKEN"),
            System.getenv("TELEGRAM_CHAT_ID")
        );

        return new DbAlertMonitorService(
            pool,
            metricsCollector,
            notifier,
            parseBooleanEnv("DB_ALERT_MONITOR_ENABLED", true),
            parseDoubleEnv("DB_ALERT_CONNECTION_RATIO_THRESHOLD", 0.75d, 0.10d, 1.0d),
            parseIntEnv("DB_ALERT_LOCK_WAIT_THRESHOLD", 3, 1, 1_000),
            parseIntEnv("DB_ALERT_SYNC_REP_WAIT_THRESHOLD", 1, 1, 1_000),
            parseIntEnv("DB_ALERT_CONSECUTIVE_BREACHES", 2, 1, 10)
        );
    }

    public boolean isMonitorEnabled() {
        return monitorEnabled;
    }

    public boolean isTelegramEnabled() {
        return notifier.isEnabled();
    }

    public Future<Void> monitorOnce() {
        return fetchSnapshot()
            .compose(snapshot -> {
                metricsCollector.recordDbHealthSnapshot(snapshot);
                return evaluateSnapshot(snapshot)
                    .compose(v -> processAlert(
                        "monitor_failure",
                        false,
                        "[KORION] DB Monitor Failed",
                        "",
                        "[KORION] DB Monitor Recovered",
                        buildLines(
                            "database=" + snapshot.databaseName(),
                            "message=db monitor queries recovered"
                        ),
                        1
                    ));
            })
            .recover(error -> processAlert(
                "monitor_failure",
                true,
                "[KORION] DB Monitor Failed",
                buildLines("error=" + safeMessage(error)),
                "[KORION] DB Monitor Recovered",
                "message=db monitor queries recovered",
                1
            ));
    }

    private Future<DbHealthSnapshot> fetchSnapshot() {
        return pool.query(SNAPSHOT_SQL)
            .execute()
            .compose(rows -> {
                Row row = rows.iterator().hasNext() ? rows.iterator().next() : null;
                if (row == null) {
                    return Future.failedFuture("DB monitor query returned no rows");
                }
                return Future.succeededFuture(new DbHealthSnapshot(
                    row.getString("database_name"),
                    getInt(row, "max_connections"),
                    getInt(row, "total_connections"),
                    getInt(row, "active_connections"),
                    getInt(row, "lock_waits"),
                    getInt(row, "sync_rep_waits"),
                    getInt(row, "streaming_replicas"),
                    getInt(row, "healthy_sync_replicas"),
                    row.getString("synchronous_standby_names"),
                    Boolean.TRUE.equals(row.getBoolean("transaction_read_only")),
                    Boolean.TRUE.equals(row.getBoolean("in_recovery"))
                ));
            });
    }

    private Future<Void> evaluateSnapshot(DbHealthSnapshot snapshot) {
        return processAlert(
            "connection_saturation",
            snapshot.connectionUsageRatio() >= connectionRatioThreshold,
            "[KORION] DB Alert - Connection Saturation",
            buildLines(
                "database=" + snapshot.databaseName(),
                "total=" + snapshot.totalConnections() + "/" + snapshot.maxConnections(),
                "active=" + snapshot.activeConnections(),
                "usageRatio=" + formatRatio(snapshot.connectionUsageRatio()),
                "threshold=" + formatRatio(connectionRatioThreshold)
            ),
            "[KORION] DB Recovered - Connection Saturation",
            buildLines(
                "database=" + snapshot.databaseName(),
                "usageRatio=" + formatRatio(snapshot.connectionUsageRatio())
            ),
            consecutiveBreaches
        ).compose(v -> processAlert(
            "lock_wait",
            snapshot.lockWaits() >= lockWaitThreshold,
            "[KORION] DB Alert - Lock Waits",
            buildLines(
                "database=" + snapshot.databaseName(),
                "lockWaits=" + snapshot.lockWaits(),
                "threshold=" + lockWaitThreshold
            ),
            "[KORION] DB Recovered - Lock Waits",
            buildLines(
                "database=" + snapshot.databaseName(),
                "lockWaits=" + snapshot.lockWaits()
            ),
            consecutiveBreaches
        )).compose(v -> processAlert(
            "sync_rep_wait",
            snapshot.syncRepWaits() >= syncRepWaitThreshold,
            "[KORION] DB Alert - Sync Rep Wait",
            buildLines(
                "database=" + snapshot.databaseName(),
                "syncRepWaits=" + snapshot.syncRepWaits(),
                "threshold=" + syncRepWaitThreshold
            ),
            "[KORION] DB Recovered - Sync Rep Wait",
            buildLines(
                "database=" + snapshot.databaseName(),
                "syncRepWaits=" + snapshot.syncRepWaits()
            ),
            consecutiveBreaches
        )).compose(v -> processAlert(
            "sync_replica_missing",
            snapshot.synchronousReplicationExpected() && snapshot.healthySyncReplicas() < 1,
            "[KORION] DB Alert - Sync Standby Missing",
            buildLines(
                "database=" + snapshot.databaseName(),
                "synchronousStandbyNames=" + blankAsDash(snapshot.synchronousStandbyNames()),
                "streamingReplicas=" + snapshot.streamingReplicas(),
                "healthySyncReplicas=" + snapshot.healthySyncReplicas()
            ),
            "[KORION] DB Recovered - Sync Standby Missing",
            buildLines(
                "database=" + snapshot.databaseName(),
                "healthySyncReplicas=" + snapshot.healthySyncReplicas()
            ),
            consecutiveBreaches
        )).compose(v -> processAlert(
            "read_only_route",
            snapshot.transactionReadOnly(),
            "[KORION] DB Alert - Read Only Route",
            buildLines(
                "database=" + snapshot.databaseName(),
                "transactionReadOnly=" + snapshot.transactionReadOnly(),
                "pgIsInRecovery=" + snapshot.inRecovery(),
                "message=write traffic is routed to a read-only database"
            ),
            "[KORION] DB Recovered - Read Only Route",
            buildLines(
                "database=" + snapshot.databaseName(),
                "transactionReadOnly=" + snapshot.transactionReadOnly(),
                "pgIsInRecovery=" + snapshot.inRecovery()
            ),
            1
        )).compose(v -> processAlert(
            "standby_attached",
            snapshot.inRecovery(),
            "[KORION] DB Alert - Standby Attached To Write Path",
            buildLines(
                "database=" + snapshot.databaseName(),
                "pgIsInRecovery=" + snapshot.inRecovery(),
                "transactionReadOnly=" + snapshot.transactionReadOnly(),
                "message=db-proxy is pointing the write path at a standby node"
            ),
            "[KORION] DB Recovered - Standby Detached From Write Path",
            buildLines(
                "database=" + snapshot.databaseName(),
                "pgIsInRecovery=" + snapshot.inRecovery()
            ),
            1
        ));
    }

    private Future<Void> processAlert(String key,
                                      boolean triggered,
                                      String alertTitle,
                                      String alertBody,
                                      String recoveryTitle,
                                      String recoveryBody,
                                      int requiredConsecutiveBreaches) {
        AlertState state = alertStates.computeIfAbsent(key, ignored -> new AlertState());

        if (triggered) {
            state.consecutiveBreaches++;
            metricsCollector.recordDbAlertState(key, state.active || state.consecutiveBreaches >= requiredConsecutiveBreaches);
            if (state.consecutiveBreaches < requiredConsecutiveBreaches) {
                return Future.succeededFuture();
            }

            String signature = alertTitle + "\n" + alertBody;
            if (state.active && signature.equals(state.lastSignature)) {
                return Future.succeededFuture();
            }

            return notifier.sendMessage(alertTitle, alertBody)
                .onSuccess(v -> {
                    state.active = true;
                    state.lastSignature = signature;
                });
        }

        state.consecutiveBreaches = 0;
        metricsCollector.recordDbAlertState(key, false);
        if (!state.active) {
            state.lastSignature = null;
            return Future.succeededFuture();
        }

        return notifier.sendMessage(recoveryTitle, recoveryBody)
            .onSuccess(v -> {
                state.active = false;
                state.lastSignature = null;
            });
    }

    private static String buildLines(String... lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line.trim());
        }
        return builder.toString();
    }

    private static String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "unknown" : error.getClass().getSimpleName();
        }
        return error.getMessage().replace('\n', ' ').trim();
    }

    private static String formatRatio(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static int getInt(Row row, String column) {
        Object value = row.getValue(column);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean parseBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static int parseIntEnv(String key, int defaultValue, int min, int max) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static double parseDoubleEnv(String key, double defaultValue, double min, double max) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static final class AlertState {
        private int consecutiveBreaches;
        private boolean active;
        private String lastSignature;
    }
}
