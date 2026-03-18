package com.foxya.coin.verticle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVerticleWithdrawalApiKeyTest {

    @Test
    void prefersDedicatedWithdrawalCallbackKeyWhenPresent() {
        String resolved = ApiVerticle.resolveInternalWithdrawalApiKey("withdraw-callback-key", "deposit-scanner-key");

        assertThat(resolved).isEqualTo("withdraw-callback-key");
    }

    @Test
    void fallsBackToDepositScannerKeyDuringRollout() {
        String resolved = ApiVerticle.resolveInternalWithdrawalApiKey("", "deposit-scanner-key");

        assertThat(resolved).isEqualTo("deposit-scanner-key");
    }
}
