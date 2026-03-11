package com.foxya.coin.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateKeyEncryptionUtilTest {

    private static final String PRIMARY_KEY = "primary-test-key";
    private static final String LEGACY_KEY = "legacy-test-key";

    @AfterEach
    void clearProperties() {
        System.clearProperty("ENCRYPTION_KEY");
        System.clearProperty("ENCRYPTION_KEY_LEGACY");
        System.clearProperty("ENCRYPTION_KEYS");
    }

    @Test
    void encryptAndDecrypt_withPrimaryKey() {
        System.setProperty("ENCRYPTION_KEY", PRIMARY_KEY);

        String encrypted = PrivateKeyEncryptionUtil.encrypt("test-private-key");

        assertThat(PrivateKeyEncryptionUtil.decrypt(encrypted)).isEqualTo("test-private-key");
    }

    @Test
    void decrypt_supportsLegacyNodeFormat() {
        System.setProperty("ENCRYPTION_KEY", PRIMARY_KEY);
        System.setProperty("ENCRYPTION_KEY_LEGACY", LEGACY_KEY);

        String legacyEncrypted = encryptLegacyNodeFormat("legacy", LEGACY_KEY);

        assertThat(PrivateKeyEncryptionUtil.decrypt(legacyEncrypted)).isEqualTo("legacy");
    }

    private String encryptLegacyNodeFormat(String plainText, String key) {
        try {
            byte[] iv = new byte[16];
            for (int i = 0; i < iv.length; i += 1) {
                iv[i] = (byte) (i + 1);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKey = new SecretKeySpec(hashed, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));

            byte[] encryptedWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] cipherText = new byte[encryptedWithTag.length - 16];
            byte[] authTag = new byte[16];
            System.arraycopy(encryptedWithTag, 0, cipherText, 0, cipherText.length);
            System.arraycopy(encryptedWithTag, cipherText.length, authTag, 0, authTag.length);
            return toHex(iv) + toHex(authTag) + toHex(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
