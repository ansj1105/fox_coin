package com.foxya.coin.wallet;

import com.foxya.coin.wallet.entities.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WalletServiceNormalizeWalletsTest {

    @Test
    void canonicalSnapshotBasis_sumsActiveRawKoriWallets() {
        Wallet koriTron = Wallet.builder()
            .id(10L)
            .userId(1L)
            .currencyCode("KORI")
            .network("TRON")
            .balance(new BigDecimal("162.999000000000000000"))
            .lockedBalance(new BigDecimal("1.001000000000000000"))
            .status("ACTIVE")
            .build();

        Wallet koriInternal = Wallet.builder()
            .id(11L)
            .userId(1L)
            .currencyCode("KORI")
            .network("INTERNAL")
            .balance(new BigDecimal("35.254587460317457206"))
            .lockedBalance(BigDecimal.ZERO)
            .status("ACTIVE")
            .build();

        Wallet inactiveKori = Wallet.builder()
            .id(12L)
            .userId(1L)
            .currencyCode("KORI")
            .network("TRON")
            .balance(new BigDecimal("999"))
            .lockedBalance(new BigDecimal("999"))
            .status("INACTIVE")
            .build();

        BigDecimal totalBalance = List.of(koriTron, koriInternal, inactiveKori).stream()
            .filter(wallet -> "ACTIVE".equalsIgnoreCase(wallet.getStatus()))
            .filter(wallet -> "KORI".equalsIgnoreCase(wallet.getCurrencyCode()))
            .map(wallet -> wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal lockedBalance = List.of(koriTron, koriInternal, inactiveKori).stream()
            .filter(wallet -> "ACTIVE".equalsIgnoreCase(wallet.getStatus()))
            .filter(wallet -> "KORI".equalsIgnoreCase(wallet.getCurrencyCode()))
            .map(wallet -> wallet.getLockedBalance() == null ? BigDecimal.ZERO : wallet.getLockedBalance())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("198.253587460317457206"), totalBalance);
        assertEquals(new BigDecimal("1.001000000000000000"), lockedBalance);
    }

    @Test
    void normalizeWalletsForClient_prefersInternalKoriBalanceWhileKeepingTronAddress() {
        Wallet koriTron = Wallet.builder()
            .id(10L)
            .userId(1L)
            .currencyId(3)
            .currencyCode("KORI")
            .currencyName("KORI Token")
            .currencySymbol("KORI")
            .network("TRON")
            .address("T_KORI_TRON")
            .balance(new BigDecimal("161.999"))
            .lockedBalance(new BigDecimal("1.001"))
            .status("ACTIVE")
            .build();

        Wallet koriInternal = Wallet.builder()
            .id(11L)
            .userId(1L)
            .currencyId(119)
            .currencyCode("KORI")
            .currencyName("KORI Internal")
            .currencySymbol("KORI")
            .network("INTERNAL")
            .address("KORI_INTERNAL_1_119")
            .balance(new BigDecimal("35.017033492063491120"))
            .lockedBalance(BigDecimal.ZERO)
            .status("ACTIVE")
            .build();

        Wallet usdt = Wallet.builder()
            .id(12L)
            .userId(1L)
            .currencyId(2)
            .currencyCode("USDT")
            .currencyName("Tether USD")
            .currencySymbol("USDT")
            .network("TRON")
            .address("T_USDT")
            .balance(new BigDecimal("100"))
            .lockedBalance(BigDecimal.ZERO)
            .status("ACTIVE")
            .build();

        List<Wallet> normalized = WalletClientViewUtils.normalizeWalletsForClient(List.of(koriTron, koriInternal, usdt));

        assertEquals(2, normalized.size());

        Wallet normalizedUsdt = normalized.stream()
            .filter(wallet -> "USDT".equals(wallet.getCurrencyCode()))
            .findFirst()
            .orElse(null);
        assertNotNull(normalizedUsdt);
        assertEquals(new BigDecimal("100"), normalizedUsdt.getBalance());

        Wallet normalizedKori = normalized.stream()
            .filter(wallet -> "KORI".equals(wallet.getCurrencyCode()))
            .findFirst()
            .orElse(null);
        assertNotNull(normalizedKori);
        assertEquals(new BigDecimal("35.017033492063491120"), normalizedKori.getBalance());
        assertEquals(BigDecimal.ZERO, normalizedKori.getLockedBalance());
        assertEquals("TRON", normalizedKori.getNetwork());
        assertEquals("T_KORI_TRON", normalizedKori.getAddress());
    }

    @Test
    void sumLogicalBalanceByCurrencyCode_usesInternalKoriClientViewBalance() {
        Wallet koriTron = Wallet.builder()
            .id(10L)
            .userId(1L)
            .currencyCode("KORI")
            .network("TRON")
            .balance(new BigDecimal("161.999"))
            .build();

        Wallet koriInternal = Wallet.builder()
            .id(11L)
            .userId(1L)
            .currencyCode("KORI")
            .network("INTERNAL")
            .balance(new BigDecimal("35.017033492063491120"))
            .build();

        Wallet trx = Wallet.builder()
            .id(12L)
            .userId(1L)
            .currencyCode("TRX")
            .network("TRON")
            .balance(new BigDecimal("50"))
            .build();

        BigDecimal logicalKoriBalance = WalletClientViewUtils.sumLogicalBalanceByCurrencyCode(
            List.of(koriTron, koriInternal, trx),
            "KORI"
        );

        assertEquals(new BigDecimal("35.017033492063491120"), logicalKoriBalance);
    }
}
