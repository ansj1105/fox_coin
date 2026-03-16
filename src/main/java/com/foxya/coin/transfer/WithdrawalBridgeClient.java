package com.foxya.coin.transfer;

import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import io.vertx.core.Future;

public interface WithdrawalBridgeClient {
    boolean supports(String currencyCode, String chain);

    Future<String> requestWithdrawal(Long userId, String transferId, ExternalTransferRequestDto request, String requestIp);
}
