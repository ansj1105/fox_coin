package com.foxya.coin.internal;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public class InternalOfflinePayService extends BaseService {

    private static final BigDecimal ZERO_FEE = BigDecimal.ZERO;

    private final TransferRepository transferRepository;
    private final CurrencyRepository currencyRepository;

    public InternalOfflinePayService(
        PgPool pool,
        TransferRepository transferRepository,
        CurrencyRepository currencyRepository
    ) {
        super(pool);
        this.transferRepository = transferRepository;
        this.currencyRepository = currencyRepository;
    }

    public Future<OfflinePaySettlementHistoryResponse> recordSettlementHistory(OfflinePaySettlementHistoryRequest request) {
        try {
            validate(request);
        } catch (RuntimeException e) {
            return Future.failedFuture(e);
        }

        String transferRef = request.transferRef() == null || request.transferRef().isBlank()
            ? request.settlementId()
            : request.transferRef();

        return transferRepository.getInternalTransferById(pool, transferRef)
            .compose(existing -> {
                if (existing != null) {
                    if (!existing.getReceiverId().equals(request.userId())
                        || existing.getAmount().compareTo(request.amount()) != 0
                        || !request.historyType().equals(existing.getTransactionType())) {
                        return Future.failedFuture(new BadRequestException("duplicate settlement history with different payload"));
                    }
                    return Future.succeededFuture(new OfflinePaySettlementHistoryResponse(
                        request.settlementId(),
                        transferRef,
                        true,
                        existing.getStatus()
                    ));
                }
                return createHistoryTransfer(request, transferRef);
            });
    }

    private Future<OfflinePaySettlementHistoryResponse> createHistoryTransfer(
        OfflinePaySettlementHistoryRequest request,
        String transferRef
    ) {
        return resolveCurrency(request.assetCode())
            .compose(currency -> transferRepository.getWalletByUserIdAndCurrencyId(pool, request.userId(), currency.getId())
                .compose(wallet -> {
                    if (wallet == null) {
                        return Future.failedFuture(new NotFoundException("receiver wallet not found"));
                    }
                    return insertHistoryTransfer(request, transferRef, currency, wallet);
                }));
    }

    private Future<OfflinePaySettlementHistoryResponse> insertHistoryTransfer(
        OfflinePaySettlementHistoryRequest request,
        String transferRef,
        Currency currency,
        Wallet wallet
    ) {
        return pool.withTransaction(client ->
            creditWalletIfNeeded(request, wallet, client)
                .compose(ignored -> {
                    InternalTransfer transfer = InternalTransfer.builder()
                        .transferId(transferRef)
                        .senderId(request.userId())
                        .senderWalletId(wallet.getId())
                        .receiverId(request.userId())
                        .receiverWalletId(wallet.getId())
                        .currencyId(currency.getId())
                        .amount(request.amount())
                        .fee(ZERO_FEE)
                        .status(InternalTransfer.STATUS_COMPLETED)
                        .transferType(request.historyType())
                        .orderNumber(request.settlementId())
                        .transactionType(request.historyType())
                        .memo("offline_pay:" + request.settlementStatus() + ":" + request.proofId())
                        .requestIp("offline_pay")
                        .build();

                    return transferRepository.createInternalTransfer(client, transfer);
                })
                .compose(created -> transferRepository.completeInternalTransfer(client, transferRef))
        ).map(created -> new OfflinePaySettlementHistoryResponse(
            request.settlementId(),
            transferRef,
            false,
            created.getStatus()
        ));
    }

    private Future<Void> creditWalletIfNeeded(
        OfflinePaySettlementHistoryRequest request,
        Wallet wallet,
        io.vertx.sqlclient.SqlClient client
    ) {
        if (!"OFFLINE_PAY_RECEIVE".equals(request.historyType())
            && !"OFFLINE_PAY_COMPENSATION".equals(request.historyType())) {
            return Future.succeededFuture();
        }

        return transferRepository.addBalance(client, wallet.getId(), request.amount())
            .compose(updatedWallet -> {
                if (updatedWallet == null) {
                    return Future.failedFuture(new BadRequestException("wallet balance update failed"));
                }
                return Future.succeededFuture();
            });
    }

    private Future<Currency> resolveCurrency(String assetCode) {
        return currencyRepository.getCurrencyByCodeAndChain(pool, assetCode, "INTERNAL")
            .compose(currency -> {
                if (currency != null) {
                    return Future.succeededFuture(currency);
                }
                return currencyRepository.getCurrencyByCode(pool, assetCode);
            })
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("currency not found: " + assetCode));
                }
                return Future.succeededFuture(currency);
            });
    }

    private void validate(OfflinePaySettlementHistoryRequest request) {
        requireText(request.settlementId(), "settlementId");
        requireText(request.batchId(), "batchId");
        requireText(request.proofId(), "proofId");
        requireText(request.assetCode(), "assetCode");
        requireText(request.settlementStatus(), "settlementStatus");
        requireText(request.historyType(), "historyType");
        if (request.userId() == null || request.userId() <= 0) {
            throw new BadRequestException("userId is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("amount must be positive");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
    }
}
