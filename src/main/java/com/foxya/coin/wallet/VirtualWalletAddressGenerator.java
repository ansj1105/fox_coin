package com.foxya.coin.wallet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class VirtualWalletAddressGenerator {

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final byte TRON_PREFIX = 0x41;

    private VirtualWalletAddressGenerator() {
    }

    public static String generateTronAddress(String hotWalletAddress, String mappingSeed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] seed = digest.digest((hotWalletAddress.trim() + ":" + mappingSeed.trim()).getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[21];
            payload[0] = TRON_PREFIX;
            System.arraycopy(seed, seed.length - 20, payload, 1, 20);

            byte[] checksum = checksum(payload);
            byte[] addressBytes = new byte[payload.length + 4];
            System.arraycopy(payload, 0, addressBytes, 0, payload.length);
            System.arraycopy(checksum, 0, addressBytes, payload.length, 4);

            return encodeBase58(addressBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate virtual TRON address", e);
        }
    }

    private static byte[] checksum(byte[] value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] first = digest.digest(value);
        byte[] second = digest.digest(first);
        byte[] checksum = new byte[4];
        System.arraycopy(second, 0, checksum, 0, 4);
        return checksum;
    }

    private static String encodeBase58(byte[] input) {
        if (input.length == 0) {
            return "";
        }

        byte[] copy = input.clone();
        StringBuilder encoded = new StringBuilder();
        int zeroCount = 0;
        while (zeroCount < copy.length && copy[zeroCount] == 0) {
            zeroCount += 1;
        }

        int startAt = zeroCount;
        while (startAt < copy.length) {
            int mod = divmod58(copy, startAt);
            encoded.append(BASE58_ALPHABET.charAt(mod));
            while (startAt < copy.length && copy[startAt] == 0) {
                startAt += 1;
            }
        }

        for (int i = 0; i < zeroCount; i += 1) {
            encoded.append(BASE58_ALPHABET.charAt(0));
        }

        return encoded.reverse().toString();
    }

    private static int divmod58(byte[] number, int startAt) {
        int remainder = 0;
        for (int i = startAt; i < number.length; i += 1) {
            int digit256 = number[i] & 0xFF;
            int temp = remainder * 256 + digit256;
            number[i] = (byte) (temp / 58);
            remainder = temp % 58;
        }
        return remainder;
    }
}
