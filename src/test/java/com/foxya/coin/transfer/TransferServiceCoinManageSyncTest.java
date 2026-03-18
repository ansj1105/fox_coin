package com.foxya.coin.transfer;

import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.WalletRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceCoinManageSyncTest {

    @Test
    void marksPendingTransferAsSubmittedWhenCoinManageBroadcasts() {
        PgPool pool = mock(PgPool.class);
        TransferRepository transferRepository = mock(TransferRepository.class);
        TransferService service = new TransferService(
            pool,
            transferRepository,
            mock(UserRepository.class),
            mock(CurrencyRepository.class),
            mock(WalletRepository.class),
            null,
            null,
            null,
            null,
            new AppConfigRepository(),
            null,
            null,
            null,
            null,
            null
        );

        ExternalTransfer pending = ExternalTransfer.builder()
            .transferId("transfer-1")
            .coinManageWithdrawalId("wd-1")
            .status(ExternalTransfer.STATUS_PENDING)
            .build();
        ExternalTransfer submitted = ExternalTransfer.builder()
            .transferId("transfer-1")
            .coinManageWithdrawalId("wd-1")
            .status(ExternalTransfer.STATUS_SUBMITTED)
            .txHash("tx-1")
            .build();

        when(transferRepository.getExternalTransferByCoinManageWithdrawalId(pool, "wd-1"))
            .thenReturn(Future.succeededFuture(pending));
        when(transferRepository.getExternalTransferById(pool, "transfer-1"))
            .thenReturn(Future.succeededFuture(pending));
        when(transferRepository.submitExternalTransfer(pool, "transfer-1", "tx-1"))
            .thenReturn(Future.succeededFuture(submitted));

        ExternalTransfer result = service.syncCoinManageWithdrawalState("wd-1", "TX_BROADCASTED", "tx-1", 20, null).result();

        assertThat(result.getStatus()).isEqualTo(ExternalTransfer.STATUS_SUBMITTED);
    }

    @Test
    void convertsLegacyApprovedTransferToProcessingThenSubmitted() {
        PgPool pool = mock(PgPool.class);
        TransferRepository transferRepository = mock(TransferRepository.class);
        TransferService service = new TransferService(
            pool,
            transferRepository,
            mock(UserRepository.class),
            mock(CurrencyRepository.class),
            mock(WalletRepository.class),
            null,
            null,
            null,
            null,
            new AppConfigRepository(),
            null,
            null,
            null,
            null,
            null
        );

        ExternalTransfer approved = ExternalTransfer.builder()
            .transferId("transfer-approved")
            .coinManageWithdrawalId("wd-approved")
            .status(ExternalTransfer.STATUS_APPROVED)
            .build();
        ExternalTransfer processing = ExternalTransfer.builder()
            .transferId("transfer-approved")
            .coinManageWithdrawalId("wd-approved")
            .status(ExternalTransfer.STATUS_PROCESSING)
            .build();
        ExternalTransfer submitted = ExternalTransfer.builder()
            .transferId("transfer-approved")
            .coinManageWithdrawalId("wd-approved")
            .status(ExternalTransfer.STATUS_SUBMITTED)
            .txHash("tx-approved")
            .build();

        when(transferRepository.getExternalTransferByCoinManageWithdrawalId(pool, "wd-approved"))
            .thenReturn(Future.succeededFuture(approved));
        when(transferRepository.updateExternalTransferStatus(pool, "transfer-approved", ExternalTransfer.STATUS_PROCESSING))
            .thenReturn(Future.succeededFuture(processing));
        when(transferRepository.getExternalTransferById(pool, "transfer-approved"))
            .thenReturn(Future.succeededFuture(processing));
        when(transferRepository.submitExternalTransfer(pool, "transfer-approved", "tx-approved"))
            .thenReturn(Future.succeededFuture(submitted));

        ExternalTransfer result = service.syncCoinManageWithdrawalState("wd-approved", "TX_BROADCASTED", "tx-approved", 20, null).result();

        assertThat(result.getStatus()).isEqualTo(ExternalTransfer.STATUS_SUBMITTED);
    }

    @Test
    void confirmsSubmittedTransferWhenCoinManageCompletes() {
        PgPool pool = mock(PgPool.class);
        TransferRepository transferRepository = mock(TransferRepository.class);
        TransferService service = new TransferService(
            pool,
            transferRepository,
            mock(UserRepository.class),
            mock(CurrencyRepository.class),
            mock(WalletRepository.class),
            null,
            null,
            null,
            null,
            new AppConfigRepository(),
            null,
            null,
            null,
            null,
            null
        );

        ExternalTransfer submitted = ExternalTransfer.builder()
            .transferId("transfer-2")
            .walletId(10L)
            .coinManageWithdrawalId("wd-2")
            .status(ExternalTransfer.STATUS_SUBMITTED)
            .amount(java.math.BigDecimal.TEN)
            .fee(java.math.BigDecimal.ONE)
            .requiredConfirmations(20)
            .build();
        ExternalTransfer confirmed = ExternalTransfer.builder()
            .transferId("transfer-2")
            .coinManageWithdrawalId("wd-2")
            .status(ExternalTransfer.STATUS_CONFIRMED)
            .confirmations(20)
            .build();

        when(transferRepository.getExternalTransferByCoinManageWithdrawalId(pool, "wd-2"))
            .thenReturn(Future.succeededFuture(submitted));
        when(pool.withTransaction(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                java.util.function.Function<io.vertx.sqlclient.SqlConnection, Future<ExternalTransfer>> fn = invocation.getArgument(0);
                io.vertx.sqlclient.SqlConnection conn = mock(io.vertx.sqlclient.SqlConnection.class);
                when(transferRepository.getExternalTransferById(conn, "transfer-2"))
                    .thenReturn(Future.succeededFuture(submitted));
                when(transferRepository.confirmExternalTransfer(conn, "transfer-2", 20))
                    .thenReturn(Future.succeededFuture(confirmed));
                when(transferRepository.unlockBalance(conn, 10L, java.math.BigDecimal.valueOf(11), false))
                    .thenReturn(Future.succeededFuture(null));
                return fn.apply(conn);
            });

        ExternalTransfer result = service.syncCoinManageWithdrawalState("wd-2", "COMPLETED", "tx-2", 20, null).result();

        assertThat(result.getStatus()).isEqualTo(ExternalTransfer.STATUS_CONFIRMED);
        verify(transferRepository, never()).submitExternalTransfer(pool, "transfer-2", "tx-2");
        verify(transferRepository).confirmExternalTransfer(any(), eq("transfer-2"), anyInt());
    }
}
