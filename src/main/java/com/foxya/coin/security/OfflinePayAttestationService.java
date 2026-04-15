package com.foxya.coin.security;

import com.foxya.coin.security.dto.OfflinePayAttestationChallengeDto;
import com.foxya.coin.security.dto.OfflinePayAttestationEvidenceDto;
import com.foxya.coin.security.dto.OfflinePayAttestationVerificationResultDto;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Primitive;

@Slf4j
public class OfflinePayAttestationService {

    private static final String ATTESTATION_EXTENSION_OID = "1.3.6.1.4.1.11129.2.1.17";
    private static final String PURPOSE = "OFFLINE_PAY_TRUST_ATTESTATION";
    private static final int CHALLENGE_TTL_SECONDS = 300;
    private static final String MODE_ANDROID_CHAIN = "ANDROID_KEY_ATTESTATION_CHAIN";
    private static final String MODE_IOS_APP_ATTEST = "IOS_APP_ATTEST";

    private static final String GOOGLE_ROOT_RSA = """
        -----BEGIN CERTIFICATE-----
        MIIFHDCCAwSgAwIBAgIJAPHBcqaZ6vUdMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
        BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMjIwMzIwMTgwNzQ4WhcNNDIwMzE1MTgw
        NzQ4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
        AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
        Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
        tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
        nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
        C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
        oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
        JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
        sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
        igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
        RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
        aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
        AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
        IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
        VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQB8cMqTllHc8U+qCrOlg3H7
        174lmaCsbo/bJ0C17JEgMLb4kvrqsXZs01U3mB/qABg/1t5Pd5AORHARs1hhqGIC
        W/nKMav574f9rZN4PC2ZlufGXb7sIdJpGiO9ctRhiLuYuly10JccUZGEHpHSYM2G
        tkgYbZba6lsCPYAAP83cyDV+1aOkTf1RCp/lM0PKvmxYN10RYsK631jrleGdcdkx
        oSK//mSQbgcWnmAEZrzHoF1/0gso1HZgIn0YLzVhLSA/iXCX4QT2h3J5z3znluKG
        1nv8NQdxei2DIIhASWfu804CA96cQKTTlaae2fweqXjdN1/v2nqOhngNyz1361mF
        mr4XmaKH/ItTwOe72NI9ZcwS1lVaCvsIkTDCEXdm9rCNPAY10iTunIHFXRh+7KPz
        lHGewCq/8TOohBRn0/NNfh7uRslOSZ/xKbN9tMBtw37Z8d2vvnXq/YWdsm1+JLVw
        n6yYD/yacNJBlwpddla8eaVMjsF6nBnIgQOf9zKSe06nSTqvgwUHosgOECZJZ1Eu
        zbH4yswbt02tKtKEFhx+v+OTge/06V+jGsqTWLsfrOCNLuA8H++z+pUENmpqnnHo
        vaI47gC+TNpkgYGkkBT6B/m/U01BuOBBTzhIlMEZq9qkDWuM2cA5kW5V3FJUcfHn
        w1IdYIg2Wxg7yHcQZemFQg==
        -----END CERTIFICATE-----
        """;

    private static final String GOOGLE_ROOT_EC = """
        -----BEGIN CERTIFICATE-----
        MIICIjCCAaigAwIBAgIRAISp0Cl7DrWK5/8OgN52BgUwCgYIKoZIzj0EAwMwUjEc
        MBoGA1UEAwwTS2V5IEF0dGVzdGF0aW9uIENBMTEQMA4GA1UECwwHQW5kcm9pZDET
        MBEGA1UECgwKR29vZ2xlIExMQzELMAkGA1UEBhMCVVMwHhcNMjUwNzE3MjIzMjE4
        WhcNMzUwNzE1MjIzMjE4WjBSMRwwGgYDVQQDDBNLZXkgQXR0ZXN0YXRpb24gQ0Ex
        MRAwDgYDVQQLDAdBbmRyb2lkMRMwEQYDVQQKDApHb29nbGUgTExDMQswCQYDVQQG
        EwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABCPaI3FO3z5bBQo8cuiEas4HjqCt
        G/mLFfRT0MsIssPBEEU5Cfbt6sH5yOAxqEi5QagpU1yX4HwnGb7OtBYpDTB57uH5
        Eczm34A5FNijV3s0/f0UPl7zbJcTx6xwqMIRq6NCMEAwDwYDVR0TAQH/BAUwAwEB
        /zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFFIyuyz7RkOb3NaBqQ5lZuA0QepA
        MAoGCCqGSM49BAMDA2gAMGUCMETfjPO/HwqReR2CS7p0ZWoD/LHs6hDi422opifH
        EUaYLxwGlT9SLdjkVpz0UUOR5wIxAIoGyxGKRHVTpqpGRFiJtQEOOTp/+s1GcxeY
        uR2zh/80lQyu9vAFCj6E4AXc+osmRg==
        -----END CERTIFICATE-----
        """;

    private final JWTAuth jwtAuth;
    private final List<X509Certificate> trustedRoots;

    public OfflinePayAttestationService(JWTAuth jwtAuth) {
        this.jwtAuth = jwtAuth;
        this.trustedRoots = loadTrustedRoots();
    }

    public OfflinePayAttestationChallengeDto issueChallenge(Long userId, String platform, String sourceDeviceId) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(CHALLENGE_TTL_SECONDS);
        JsonObject payload = new JsonObject()
            .put("userId", String.valueOf(userId))
            .put("purpose", PURPOSE)
            .put("platform", platform)
            .put("sourceDeviceId", sourceDeviceId);
        String token = jwtAuth.generateToken(payload, new JWTOptions().setExpiresInSeconds(CHALLENGE_TTL_SECONDS));
        return OfflinePayAttestationChallengeDto.builder()
            .token(token)
            .platform(platform)
            .sourceDeviceId(sourceDeviceId)
            .expiresAt(expiresAt)
            .build();
    }

    public Future<OfflinePayAttestationVerificationResultDto> verify(
        Long userId,
        String platform,
        String sourceDeviceId,
        OfflinePayAttestationEvidenceDto evidence
    ) {
        if (evidence == null) {
            return Future.succeededFuture(OfflinePayAttestationVerificationResultDto.skipped("NONE", "ATTESTATION_NOT_PROVIDED"));
        }
        if ("android".equalsIgnoreCase(platform)) {
            return verifyAndroid(userId, sourceDeviceId, evidence);
        }
        if ("ios".equalsIgnoreCase(platform)) {
            return Future.succeededFuture(OfflinePayAttestationVerificationResultDto.skipped(MODE_IOS_APP_ATTEST, "IOS_APP_ATTEST_NOT_IMPLEMENTED"));
        }
        return Future.succeededFuture(OfflinePayAttestationVerificationResultDto.skipped("UNSUPPORTED_PLATFORM", "ATTESTATION_UNSUPPORTED_PLATFORM"));
    }

    private Future<OfflinePayAttestationVerificationResultDto> verifyAndroid(
        Long userId,
        String sourceDeviceId,
        OfflinePayAttestationEvidenceDto evidence
    ) {
        if (evidence.getChallengeToken() == null || evidence.getChallengeToken().isBlank()) {
            return Future.succeededFuture(OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "MISSING_CHALLENGE_TOKEN"));
        }
        if (evidence.getAndroidCertificateChain() == null || evidence.getAndroidCertificateChain().isEmpty()) {
            return Future.succeededFuture(OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "MISSING_ANDROID_CERTIFICATE_CHAIN"));
        }

        return jwtAuth.authenticate(new TokenCredentials(evidence.getChallengeToken()))
            .map(user -> {
                JsonObject principal = user.principal();
                String purpose = principal.getString("purpose", "");
                String tokenUserId = principal.getString("userId", "");
                String tokenDeviceId = principal.getString("sourceDeviceId", "");
                if (!PURPOSE.equals(purpose)) {
                    return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "INVALID_CHALLENGE_PURPOSE");
                }
                if (!String.valueOf(userId).equals(tokenUserId)) {
                    return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "CHALLENGE_USER_MISMATCH");
                }
                if (sourceDeviceId != null && !sourceDeviceId.isBlank() && !sourceDeviceId.equals(tokenDeviceId)) {
                    return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "CHALLENGE_DEVICE_MISMATCH");
                }
                try {
                    List<X509Certificate> certificates = decodeCertificates(evidence.getAndroidCertificateChain());
                    if (certificates.isEmpty()) {
                        return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "EMPTY_ANDROID_CERTIFICATE_CHAIN");
                    }
                    verifyChain(certificates);
                    X509Certificate leaf = certificates.get(0);
                    AndroidAttestationRecord record = parseAttestationRecord(leaf);
                    byte[] expectedChallenge = sha256(evidence.getChallengeToken());
                    if (!MessageDigest.isEqual(expectedChallenge, record.challenge())) {
                        return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "ATTESTATION_CHALLENGE_MISMATCH");
                    }
                    if (!record.hardwareBacked()) {
                        return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "ATTESTATION_NOT_HARDWARE_BACKED");
                    }
                    return OfflinePayAttestationVerificationResultDto.verified(
                        MODE_ANDROID_CHAIN,
                        "ANDROID_KEYSTORE_ATTESTATION",
                        "HARDWARE_BACKED_VERIFIED",
                        "SERVER_VERIFIED"
                    );
                } catch (Exception exception) {
                    log.warn("Android key attestation verification failed - userId: {}, reason: {}", userId, exception.getMessage());
                    return OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, exception.getMessage());
                }
            })
            .otherwise(throwable -> OfflinePayAttestationVerificationResultDto.failed(MODE_ANDROID_CHAIN, "CHALLENGE_TOKEN_INVALID"));
    }

    private List<X509Certificate> decodeCertificates(List<String> encodedCertificates) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        for (String encoded : encodedCertificates) {
            if (encoded == null || encoded.isBlank()) {
                continue;
            }
            byte[] der = decodeCertificateBytes(encoded);
            certificates.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
        }
        return certificates;
    }

    private void verifyChain(List<X509Certificate> certificates) throws Exception {
        for (int index = 0; index < certificates.size() - 1; index += 1) {
            certificates.get(index).verify(certificates.get(index + 1).getPublicKey());
        }
        X509Certificate root = certificates.get(certificates.size() - 1);
        root.verify(root.getPublicKey());
        boolean trusted = trustedRoots.stream().anyMatch(candidate -> candidate.equals(root));
        if (!trusted) {
            throw new IllegalArgumentException("ATTESTATION_ROOT_NOT_TRUSTED");
        }
    }

    private AndroidAttestationRecord parseAttestationRecord(X509Certificate certificate) throws Exception {
        byte[] extension = certificate.getExtensionValue(ATTESTATION_EXTENSION_OID);
        if (extension == null || extension.length == 0) {
            throw new IllegalArgumentException("ATTESTATION_EXTENSION_MISSING");
        }
        ASN1OctetString outer = ASN1OctetString.getInstance(ASN1Primitive.fromByteArray(extension));
        ASN1Sequence sequence = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(outer.getOctets()));
        if (sequence.size() < 6) {
            throw new IllegalArgumentException("ATTESTATION_EXTENSION_MALFORMED");
        }
        int attestationSecurityLevel = ASN1Enumerated.getInstance(sequence.getObjectAt(1)).getValue().intValue();
        int keymasterSecurityLevel = ASN1Enumerated.getInstance(sequence.getObjectAt(3)).getValue().intValue();
        byte[] challenge = ASN1OctetString.getInstance(sequence.getObjectAt(4)).getOctets();
        boolean hardwareBacked = attestationSecurityLevel >= 1 || keymasterSecurityLevel >= 1;
        return new AndroidAttestationRecord(challenge, hardwareBacked);
    }

    private List<X509Certificate> loadTrustedRoots() {
        try {
            return decodeCertificates(List.of(GOOGLE_ROOT_RSA, GOOGLE_ROOT_EC));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load Android attestation roots", exception);
        }
    }

    private byte[] decodeCertificateBytes(String encoded) {
        String normalized = encoded
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(normalized);
    }

    private byte[] sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private record AndroidAttestationRecord(byte[] challenge, boolean hardwareBacked) {
    }
}
