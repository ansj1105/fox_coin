package com.foxya.coin.wallet;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class WalletBalanceSummary {
    String currencyCode;
    BigDecimal totalBalance;
    BigDecimal totalLockedBalance;
    Integer walletCount;
}
