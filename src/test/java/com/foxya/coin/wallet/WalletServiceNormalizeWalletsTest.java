package com.foxya.coin.wallet;

import com.foxya.coin.wallet.entities.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WalletServiceNormalizeWalletsTest {

    @Test
    void normalizeWalletsForClient_sumsKoriTronAndInternalBalances() {
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

        List<Wallet> normalized = WalletService.normalizeWalletsForClient(List.of(koriTron, koriInternal, usdt));

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
        assertEquals(new BigDecimal("197.016033492063491120"), normalizedKori.getBalance());
        assertEquals(new BigDecimal("1.001"), normalizedKori.getLockedBalance());
        assertEquals("TRON", normalizedKori.getNetwork());
        assertEquals("T_KORI_TRON", normalizedKori.getAddress());
    }
}
