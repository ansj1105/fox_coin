package com.foxya.coin.wallet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualWalletAddressGeneratorTest {

    private static final String TEST_HOT_WALLET_ADDRESS = "TTestOnlyHotWalletAddressForSpec123";

    @Test
    void generateTronAddress_isDeterministicAndLooksLikeTronAddress() {
        String mappingSeed = "user:123:TRON";

        String first = VirtualWalletAddressGenerator.generateTronAddress(TEST_HOT_WALLET_ADDRESS, mappingSeed);
        String second = VirtualWalletAddressGenerator.generateTronAddress(TEST_HOT_WALLET_ADDRESS, mappingSeed);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("T");
        assertThat(first).hasSizeBetween(34, 34);
    }

    @Test
    void generateTronAddress_changesWhenSeedChanges() {
        String first = VirtualWalletAddressGenerator.generateTronAddress(TEST_HOT_WALLET_ADDRESS, "user:123:TRON");
        String second = VirtualWalletAddressGenerator.generateTronAddress(TEST_HOT_WALLET_ADDRESS, "user:124:TRON");

        assertThat(first).isNotEqualTo(second);
    }
}
