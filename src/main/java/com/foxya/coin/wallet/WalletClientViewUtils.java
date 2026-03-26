package com.foxya.coin.wallet;

import com.foxya.coin.wallet.entities.Wallet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WalletClientViewUtils {

    private WalletClientViewUtils() {
    }

    /**
     * Expose KORI as a single logical asset to clients:
     * keep the TRON wallet/address for deposits while surfacing the combined logical balance.
     */
    public static List<Wallet> normalizeWalletsForClient(List<Wallet> wallets) {
        if (wallets == null || wallets.isEmpty()) {
            return wallets;
        }

        Wallet koriTron = null;
        Wallet koriInternal = null;
        List<Wallet> normalized = new ArrayList<>();

        for (Wallet wallet : wallets) {
            if (wallet == null) {
                continue;
            }

            boolean isKori = "KORI".equalsIgnoreCase(wallet.getCurrencyCode());
            boolean isTron = "TRON".equalsIgnoreCase(wallet.getNetwork());
            boolean isInternal = "INTERNAL".equalsIgnoreCase(wallet.getNetwork());

            if (isKori && isTron) {
                koriTron = wallet;
                continue;
            }
            if (isKori && isInternal) {
                koriInternal = wallet;
                continue;
            }

            normalized.add(wallet);
        }

        if (koriTron != null) {
            BigDecimal effectiveBalance = sumNullableBalances(
                koriTron.getBalance(),
                koriInternal != null ? koriInternal.getBalance() : null
            );
            BigDecimal effectiveLockedBalance = sumNullableBalances(
                koriTron.getLockedBalance(),
                koriInternal != null ? koriInternal.getLockedBalance() : null
            );
            normalized.add(Wallet.builder()
                .id(koriTron.getId())
                .userId(koriTron.getUserId())
                .currencyId(koriTron.getCurrencyId())
                .currencyCode(koriTron.getCurrencyCode())
                .currencyName(koriTron.getCurrencyName())
                .currencySymbol(koriTron.getCurrencySymbol())
                .network(koriTron.getNetwork())
                .address(koriTron.getAddress())
                .privateKey(koriTron.getPrivateKey())
                .balance(effectiveBalance)
                .lockedBalance(effectiveLockedBalance)
                .verified(koriTron.getVerified())
                .status(koriTron.getStatus())
                .createdAt(koriTron.getCreatedAt())
                .updatedAt(koriTron.getUpdatedAt())
                .build());
        } else if (koriInternal != null) {
            normalized.add(koriInternal);
        }

        normalized.sort(Comparator.comparing(Wallet::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return normalized;
    }

    public static BigDecimal sumLogicalBalanceByCurrencyCode(List<Wallet> wallets, String currencyCode) {
        if (wallets == null || wallets.isEmpty() || currencyCode == null || currencyCode.isBlank()) {
            return BigDecimal.ZERO;
        }

        return normalizeWalletsForClient(wallets).stream()
            .filter(wallet -> currencyCode.equalsIgnoreCase(wallet.getCurrencyCode()))
            .map(Wallet::getBalance)
            .filter(balance -> balance != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumNullableBalances(BigDecimal first, BigDecimal second) {
        BigDecimal normalizedFirst = first != null ? first : BigDecimal.ZERO;
        BigDecimal normalizedSecond = second != null ? second : BigDecimal.ZERO;
        return normalizedFirst.add(normalizedSecond);
    }

    private static BigDecimal normalizeBalance(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
