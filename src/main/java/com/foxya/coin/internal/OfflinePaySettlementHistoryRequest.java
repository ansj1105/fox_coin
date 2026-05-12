package com.foxya.coin.internal;

import java.math.BigDecimal;

public record OfflinePaySettlementHistoryRequest(
    String settlementId,
    String transferRef,
    String batchId,
    String collateralId,
    String proofId,
    Long userId,
    String deviceId,
    String assetCode,
    BigDecimal amount,
    String settlementStatus,
    String historyType
) {}
