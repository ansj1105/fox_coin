package com.foxya.coin.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlConnection;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferServiceOfflinePayHistoryTest {

    @Test
    void recordsOfflinePayHistoryUsingHotWalletAndInternalCurrency() {
        PgPool pool = mock(PgPool.class);
        TransferRepository transferRepository = mock(TransferRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CurrencyRepository currencyRepository = mock(CurrencyRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
        TransferService service = new TransferService(
            pool,
            transferRepository,
            userRepository,
            currencyRepository,
            walletRepository,
            null,
            null,
            null,
            null,
            appConfigRepository,
            null,
            null,
            null,
            null,
            null
        );

        Currency currency = Currency.builder()
            .id(1)
            .code("KORI")
            .chain("INTERNAL")
            .build();
        Wallet hotWallet = Wallet.builder()
            .id(10L)
            .userId(99L)
            .currencyId(1)
            .balance(new BigDecimal("1000"))
            .lockedBalance(BigDecimal.ZERO)
            .status("ACTIVE")
            .build();
        Wallet receiverWallet = Wallet.builder()
            .id(20L)
            .userId(77L)
            .currencyId(1)
            .balance(BigDecimal.ZERO)
            .lockedBalance(BigDecimal.ZERO)
            .status("ACTIVE")
            .build();
        InternalTransfer created = InternalTransfer.builder()
            .transferId("settlement-1")
            .status(InternalTransfer.STATUS_COMPLETED)
            .transactionType("OFFLINE_PAY_CONFLICT")
            .orderNumber("batch-1")
            .memo("offlinePay settlementId=settlement-1 collateralId=collateral-1 proofId=proof-1 deviceId=device-1 status=SETTLED")
            .amount(new BigDecimal("12.5"))
            .build();
        InternalTransfer completed = InternalTransfer.builder()
            .transferId("settlement-1")
            .status(InternalTransfer.STATUS_COMPLETED)
            .build();
        SqlConnection client = mock(SqlConnection.class);

        when(currencyRepository.getCurrencyByCodeAndChain(pool, "KORI", "INTERNAL"))
            .thenReturn(Future.succeededFuture(currency));
        when(appConfigRepository.getByKey(pool, "hot_wallet_user_id"))
            .thenReturn(Future.succeededFuture("99"));
        when(transferRepository.getWalletByUserIdAndCurrencyId(pool, 99L, 1))
            .thenReturn(Future.succeededFuture(hotWallet));
        when(transferRepository.getWalletByUserIdAndCurrencyId(pool, 77L, 1))
            .thenReturn(Future.succeededFuture(null));
        when(walletRepository.createWallet(pool, 77L, 1, "KORI_INTERNAL_77"))
            .thenReturn(Future.succeededFuture(receiverWallet));
        when(pool.withTransaction(any()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Function<SqlConnection, Future<InternalTransfer>> fn =
                    invocation.getArgument(0);
                when(transferRepository.deductBalance(client, 10L, new BigDecimal("12.5")))
                    .thenReturn(Future.succeededFuture(hotWallet));
                when(transferRepository.addBalance(client, 20L, new BigDecimal("12.5")))
                    .thenReturn(Future.succeededFuture(receiverWallet));
                when(transferRepository.createInternalTransfer(eq(client), any(InternalTransfer.class)))
                    .thenReturn(Future.succeededFuture(created));
                when(transferRepository.completeInternalTransfer(client, "settlement-1"))
                    .thenReturn(Future.succeededFuture(completed));
                return fn.apply(client);
            });

        InternalTransfer result = service.recordOfflinePaySettlementHistory(
            77L,
            "settlement-1",
            "batch-1",
            "collateral-1",
            "proof-1",
            "device-1",
            null,
            "12.5",
            "SETTLED",
            "OFFLINE_PAY_CONFLICT"
        ).result();

        assertThat(result.getStatus()).isEqualTo(InternalTransfer.STATUS_COMPLETED);
        assertThat(result.getTransferId()).isEqualTo("settlement-1");
        verify(currencyRepository).getCurrencyByCodeAndChain(pool, "KORI", "INTERNAL");
        verify(appConfigRepository).getByKey(pool, "hot_wallet_user_id");
        verify(transferRepository).createInternalTransfer(eq(client), any(InternalTransfer.class));
        verify(transferRepository).completeInternalTransfer(client, "settlement-1");
    }

    @Test
    void failsWhenInternalCurrencyIsMissing() {
        PgPool pool = mock(PgPool.class);
        TransferRepository transferRepository = mock(TransferRepository.class);
        CurrencyRepository currencyRepository = mock(CurrencyRepository.class);
        AppConfigRepository appConfigRepository = mock(AppConfigRepository.class);
        TransferService service = new TransferService(
            pool,
            transferRepository,
            mock(UserRepository.class),
            currencyRepository,
            mock(WalletRepository.class),
            null,
            null,
            null,
            null,
            appConfigRepository,
            null,
            null,
            null,
            null,
            null
        );

        when(currencyRepository.getCurrencyByCodeAndChain(pool, "KORI", "INTERNAL"))
            .thenReturn(Future.succeededFuture(null));

        var future = service.recordOfflinePaySettlementHistory(
            77L,
            "settlement-1",
            "batch-1",
            "collateral-1",
            "proof-1",
            "device-1",
            null,
            "12.5",
            "SETTLED",
            "OFFLINE_PAY_CONFLICT"
        );

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(NotFoundException.class);
    }
}
