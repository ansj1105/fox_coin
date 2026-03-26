package com.foxya.coin.common.alert;

public record DbHealthSnapshot(
    String databaseName,
    int maxConnections,
    int totalConnections,
    int activeConnections,
    int lockWaits,
    int syncRepWaits,
    int streamingReplicas,
    int healthySyncReplicas,
    String synchronousStandbyNames,
    boolean transactionReadOnly,
    boolean inRecovery,
    String dbProbeError,
    String catalogProbeError,
    boolean appHealthUp,
    String appHealthError
) {

    public double connectionUsageRatio() {
        if (maxConnections <= 0) {
            return 0.0d;
        }
        return (double) totalConnections / (double) maxConnections;
    }

    public boolean synchronousReplicationExpected() {
        return synchronousStandbyNames != null && !synchronousStandbyNames.isBlank();
    }
}
