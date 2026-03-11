package com.foxya.coin.wallet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualWalletAddressGeneratorTest {

    @Test
    void generateTronAddress_isDeterministicAndLooksLikeTronAddress() {
        String hotWalletAddress = "TEcBR2zfPGCLGsoGpMFLpGTEwsC8jB72Hf";
        String mappingSeed = "user:123:TRON";

        String first = VirtualWalletAddressGenerator.generateTronAddress(hotWalletAddress, mappingSeed);
        String second = VirtualWalletAddressGenerator.generateTronAddress(hotWalletAddress, mappingSeed);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("T");
        assertThat(first).hasSizeBetween(34, 34);
    }

    @Test
    void generateTronAddress_changesWhenSeedChanges() {
        String hotWalletAddress = "TEcBR2zfPGCLGsoGpMFLpGTEwsC8jB72Hf";

        String first = VirtualWalletAddressGenerator.generateTronAddress(hotWalletAddress, "user:123:TRON");
        String second = VirtualWalletAddressGenerator.generateTronAddress(hotWalletAddress, "user:124:TRON");

        assertThat(first).isNotEqualTo(second);
    }
}
