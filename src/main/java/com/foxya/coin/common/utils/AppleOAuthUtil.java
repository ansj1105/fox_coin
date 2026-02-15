package com.foxya.coin.common.utils;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Sign in with Apple OAuth 유틸리티
 */
@Slf4j
public class AppleOAuthUtil {
    private static final String TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token";
    private static final String JWKS_ENDPOINT = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";

    public static Future<JsonObject> exchangeToken(
            WebClient webClient,
            String code,
            String clientId,
            String clientSecret,
            String redirectUri) {

        MultiMap form = MultiMap.caseInsensitiveMultiMap()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", clientId)
            .add("client_secret", clientSecret);

        if (redirectUri != null && !redirectUri.isBlank()) {
            form.add("redirect_uri", redirectUri);
        }

        return webClient.postAbs(TOKEN_ENDPOINT)
            .sendForm(form)
            .compose(response -> {
                if (response.statusCode() == 200) {
                    return Future.succeededFuture(response.bodyAsJsonObject());
                }
                log.error("Apple token exchange failed. Status: {}, Body: {}", response.statusCode(), response.bodyAsString());
                return Future.failedFuture(new UnauthorizedException("Invalid authorization code"));
            });
    }

    public static Future<JsonObject> verifyIdToken(
            WebClient webClient,
            String idToken,
            String clientId) {
        if (idToken == null || idToken.isBlank()) {
            return Future.failedFuture(new UnauthorizedException("Missing id_token"));
        }
        return webClient.getAbs(JWKS_ENDPOINT)
            .send()
            .compose(response -> {
                if (response.statusCode() != 200) {
                    log.error("Failed to fetch Apple JWKS. Status: {}, Body: {}", response.statusCode(), response.bodyAsString());
                    return Future.failedFuture(new UnauthorizedException("Failed to verify Apple token"));
                }
                try {
                    JsonWebKeySet jwks = new JsonWebKeySet(response.bodyAsString());
                    String kid = JsonWebSignature.fromCompactSerialization(idToken).getKeyIdHeaderValue();
                    JsonWebKey key = selectKey(jwks.getJsonWebKeys(), kid);
                    if (key == null) {
                        return Future.failedFuture(new UnauthorizedException("Failed to verify Apple token"));
                    }

                    JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                        .setRequireExpirationTime()
                        .setExpectedIssuer(ISSUER)
                        .setExpectedAudience(clientId)
                        .setVerificationKey(key.getKey())
                        .build();

                    JwtClaims claims = jwtConsumer.processToClaims(idToken);
                    Object emailVerified = null;
                    try {
                        emailVerified = claims.getClaimValue("email_verified");
                    } catch (Exception ignore) {
                        // ignore claim parsing issues
                    }
                    JsonObject json = new JsonObject()
                        .put("sub", claims.getSubject())
                        .put("email", claims.getStringClaimValue("email"))
                        .put("email_verified", emailVerified != null ? String.valueOf(emailVerified) : null);
                    return Future.succeededFuture(json);
                } catch (Exception e) {
                    log.error("Apple id_token verification failed", e);
                    return Future.failedFuture(new UnauthorizedException("Failed to verify Apple token"));
                }
            });
    }

    public static String createClientSecret(String teamId, String keyId, String clientId, String privateKeyPath) {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyPath);
            long now = Instant.now().getEpochSecond();

            JwtClaims claims = new JwtClaims();
            claims.setIssuer(teamId);
            claims.setIssuedAt(NumericDate.fromSeconds(now));
            claims.setExpirationTime(NumericDate.fromSeconds(now + 300)); // 5분
            claims.setAudience(ISSUER);
            claims.setSubject(clientId);

            JsonWebSignature jws = new JsonWebSignature();
            jws.setHeader("kid", keyId);
            jws.setAlgorithmHeaderValue("ES256");
            jws.setPayload(claims.toJson());
            jws.setKey(privateKey);

            return jws.getCompactSerialization();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Apple client_secret", e);
        }
    }

    private static PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(keySpec);
    }

    private static JsonWebKey selectKey(List<JsonWebKey> keys, String kid) {
        if (kid == null) {
            return keys.isEmpty() ? null : keys.get(0);
        }
        for (JsonWebKey key : keys) {
            if (kid.equals(key.getKeyId())) {
                return key;
            }
        }
        return null;
    }
}
