package com.foxya.coin.common.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PrivateKeyEncryptionUtil {

    private static final String ENV_KEY = "ENCRYPTION_KEY";
    private static final String ENV_LEGACY_KEY = "ENCRYPTION_KEY_LEGACY";
    private static final String ENV_KEYS = "ENCRYPTION_KEYS";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 16;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;

    private PrivateKeyEncryptionUtil() {
    }

    public static String encrypt(String privateKey) {
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalArgumentException("Private key must be provided.");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);
            byte[] encrypted = cipher.doFinal(privateKey.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return bytesToHex(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt private key: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException("Encrypted private key must be provided.");
        }
        if (!isEncryptedPayload(encryptedValue)) {
            return encryptedValue;
        }
        try {
            byte[] data = hexToBytes(encryptedValue);
            Exception lastError = null;
            for (SecretKeySpec key : getSecretKeys()) {
                try {
                    return decryptJavaFormat(data, key);
                } catch (Exception e) {
                    lastError = e;
                }
                try {
                    return decryptLegacyNodeFormat(data, key);
                } catch (Exception e) {
                    lastError = e;
                }
            }
            throw new IllegalStateException("Failed to decrypt private key: " + (lastError != null ? lastError.getMessage() : "unknown format"), lastError);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt private key: " + e.getMessage(), e);
        }
    }

    private static SecretKeySpec getSecretKey() throws Exception {
        return getSecretKeys().get(0);
    }

    private static List<SecretKeySpec> getSecretKeys() throws Exception {
        Set<String> rawKeys = new LinkedHashSet<>();
        addKey(rawKeys, getConfigValue(ENV_KEY));
        addKey(rawKeys, getConfigValue(ENV_LEGACY_KEY));
        String keyList = getConfigValue(ENV_KEYS);
        if (keyList != null && !keyList.isBlank()) {
            for (String key : keyList.split(",")) {
                addKey(rawKeys, key);
            }
        }
        if (rawKeys.isEmpty()) {
            throw new IllegalStateException("ENCRYPTION_KEY environment variable is required.");
        }
        List<SecretKeySpec> keys = new ArrayList<>();
        for (String key : rawKeys) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            keys.add(new SecretKeySpec(hashed, "AES"));
        }
        return keys;
    }

    private static void addKey(Set<String> keys, String value) {
        if (value != null && !value.isBlank()) {
            keys.add(value.trim());
        }
    }

    private static String getConfigValue(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value;
    }

    private static String decryptJavaFormat(byte[] data, SecretKeySpec key) throws Exception {
        if (data.length <= IV_LENGTH_BYTES + TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("Encrypted private key format is invalid.");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] cipherText = new byte[data.length - IV_LENGTH_BYTES];
        System.arraycopy(data, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(data, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] decrypted = cipher.doFinal(cipherText);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static String decryptLegacyNodeFormat(byte[] data, SecretKeySpec key) throws Exception {
        if (data.length <= IV_LENGTH_BYTES + TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("Encrypted private key format is invalid.");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] authTag = new byte[TAG_LENGTH_BYTES];
        byte[] cipherText = new byte[data.length - IV_LENGTH_BYTES - TAG_LENGTH_BYTES];
        System.arraycopy(data, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(data, IV_LENGTH_BYTES, authTag, 0, TAG_LENGTH_BYTES);
        System.arraycopy(data, IV_LENGTH_BYTES + TAG_LENGTH_BYTES, cipherText, 0, cipherText.length);

        byte[] javaCompatibleCipher = new byte[cipherText.length + authTag.length];
        System.arraycopy(cipherText, 0, javaCompatibleCipher, 0, cipherText.length);
        System.arraycopy(authTag, 0, javaCompatibleCipher, cipherText.length, authTag.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] decrypted = cipher.doFinal(javaCompatibleCipher);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static boolean isEncryptedPayload(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        int minEncryptedHexLength = (IV_LENGTH_BYTES + TAG_LENGTH_BYTES + 1) * 2;
        if (normalized.length() < minEncryptedHexLength || (normalized.length() % 2 != 0)) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i += 1) {
            if (Character.digit(normalized.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexToBytes(String value) {
        int len = value.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string has invalid length.");
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(value.charAt(i), 16);
            int lo = Character.digit(value.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Hex string contains invalid characters.");
            }
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
    }
}
