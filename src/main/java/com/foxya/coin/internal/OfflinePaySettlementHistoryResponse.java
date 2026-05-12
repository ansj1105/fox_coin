package com.foxya.coin.internal;

public record OfflinePaySettlementHistoryResponse(
    String settlementId,
    String transferRef,
    boolean duplicated,
    String status
) {}
