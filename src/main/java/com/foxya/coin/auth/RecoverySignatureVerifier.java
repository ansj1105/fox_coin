package com.foxya.coin.auth;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.core.SegwitAddress;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class RecoverySignatureVerifier {
    private RecoverySignatureVerifier() {}

    public static boolean verifyEthSignature(String message, String signatureHex, String expectedAddress) {
        String recovered = recoverEthAddress(message, signatureHex);
        return recovered != null && recovered.equalsIgnoreCase(expectedAddress);
    }

    public static boolean verifyTronSignature(String message, String signatureHex, String expectedAddress) {
        BigInteger publicKey = recoverEthPublicKey(message, signatureHex);
        if (publicKey == null) {
            return false;
        }
        String tronAddress = publicKeyToTronAddress(publicKey);
        return tronAddress != null && tronAddress.equals(expectedAddress);
    }

    public static boolean verifyBtcSignature(String message, String signatureBase64, String expectedAddress) {
        try {
            ECKey key = ECKey.signedMessageToKey(message, signatureBase64);
            String legacy = LegacyAddress.fromKey(MainNetParams.get(), key).toString();
            String segwit = SegwitAddress.fromKey(MainNetParams.get(), key).toString();
            return expectedAddress.equals(legacy) || expectedAddress.equals(segwit);
        } catch (Exception e) {
            return false;
        }
    }

    private static String recoverEthAddress(String message, String signatureHex) {
        BigInteger publicKey = recoverEthPublicKey(message, signatureHex);
        if (publicKey == null) {
            return null;
        }
        return "0x" + Keys.getAddress(publicKey);
    }

    private static BigInteger recoverEthPublicKey(String message, String signatureHex) {
        try {
            byte[] signatureBytes = Numeric.hexStringToByteArray(signatureHex);
            if (signatureBytes.length != 65) {
                return null;
            }
            byte v = signatureBytes[64];
            if (v < 27) {
                v += 27;
            }
            byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
            byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
            Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            return Sign.signedPrefixedMessageToKey(messageBytes, signatureData);
        } catch (Exception e) {
            return null;
        }
    }

    private static String publicKeyToTronAddress(BigInteger publicKey) {
        try {
            String ethAddress = Keys.getAddress(publicKey);
            byte[] addressBytes = Numeric.hexStringToByteArray(ethAddress);
            byte[] tron = new byte[addressBytes.length + 1];
            tron[0] = 0x41;
            System.arraycopy(addressBytes, 0, tron, 1, addressBytes.length);
            byte[] checksum = Sha256Hash.hashTwice(tron);
            byte[] withChecksum = new byte[tron.length + 4];
            System.arraycopy(tron, 0, withChecksum, 0, tron.length);
            System.arraycopy(checksum, 0, withChecksum, tron.length, 4);
            return Base58.encode(withChecksum);
        } catch (Exception e) {
            return null;
        }
    }
}
