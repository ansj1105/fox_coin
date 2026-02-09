package com.foxya.coin.common.utils;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;

/**
 * Kakao OAuth 2.0 유틸리티 클래스
 * Kakao OAuth 인증 관련 공통 로직을 제공합니다.
 */
@Slf4j
public class KakaoOAuthUtil {

    private static final String TOKEN_ENDPOINT = "https://kauth.kakao.com/oauth/token";
    private static final String USERINFO_ENDPOINT = "https://kapi.kakao.com/v2/user/me";

    /**
     * Kakao OAuth Authorization Code를 Access Token으로 교환
     * 
     * @param webClient WebClient 인스턴스
     * @param code Authorization code
     * @param clientId Kakao REST API Key
     * @param clientSecret Kakao Client Secret (선택)
     * @param redirectUri Redirect URI
     * @return Access Token을 포함한 JsonObject
     */
    public static Future<JsonObject> exchangeToken(
            WebClient webClient,
            String code,
            String clientId,
            String clientSecret,
            String redirectUri) {

        MultiMap form = MultiMap.caseInsensitiveMultiMap()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code);

        if (redirectUri != null && !redirectUri.isBlank()) {
            form.add("redirect_uri", redirectUri);
        }
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        log.debug("Exchanging Kakao authorization code for token. redirectUri: {}", redirectUri);

        return webClient.postAbs(TOKEN_ENDPOINT)
            .sendForm(form)
            .compose(response -> {
                if (response.statusCode() == 200) {
                    JsonObject tokenResponse = response.bodyAsJsonObject();
                    String accessToken = tokenResponse.getString("access_token");
                    if (accessToken == null) {
                        log.error("Access token not found in Kakao response");
                        return Future.failedFuture(new UnauthorizedException("Failed to verify Kakao token"));
                    }
                    return Future.succeededFuture(tokenResponse);
                } else {
                    log.error("Failed to exchange Kakao authorization code. Status: {}, Body: {}",
                        response.statusCode(), response.bodyAsString());
                    return Future.failedFuture(new UnauthorizedException("Invalid authorization code"));
                }
            });
    }

    /**
     * Kakao Access Token으로 사용자 정보 조회
     * 
     * @param webClient WebClient 인스턴스
     * @param accessToken Kakao Access Token
     * @return 사용자 정보 (id, email, nickname 등)
     */
    public static Future<JsonObject> getUserInfo(WebClient webClient, String accessToken) {
        log.debug("Fetching Kakao user info");

        return webClient.getAbs(USERINFO_ENDPOINT)
            .putHeader("Authorization", "Bearer " + accessToken)
            .send()
            .compose(response -> {
                if (response.statusCode() != 200) {
                    log.error("Failed to get Kakao user info. Status: {}, Body: {}",
                        response.statusCode(), response.bodyAsString());
                    return Future.failedFuture(new UnauthorizedException("Failed to verify Kakao token"));
                }

                JsonObject userInfo = response.bodyAsJsonObject();
                Object idValue = userInfo.getValue("id");
                String kakaoId = idValue != null ? String.valueOf(idValue) : null;
                JsonObject account = userInfo.getJsonObject("kakao_account");
                String email = account != null ? account.getString("email") : null;
                JsonObject profile = account != null ? account.getJsonObject("profile") : null;
                String nickname = profile != null ? profile.getString("nickname") : null;

                if (kakaoId == null || email == null) {
                    log.error("Email or Kakao ID not found in user info");
                    return Future.failedFuture(new UnauthorizedException("Failed to verify Kakao token"));
                }

                return Future.succeededFuture(new JsonObject()
                    .put("id", kakaoId)
                    .put("email", email)
                    .put("nickname", nickname));
            });
    }

    /**
     * Kakao OAuth 전체 플로우 실행 (토큰 교환 + 사용자 정보 조회)
     * 
     * @param webClient WebClient 인스턴스
     * @param code Authorization code
     * @param clientId Kakao REST API Key
     * @param clientSecret Kakao Client Secret (선택)
     * @param redirectUri Redirect URI
     * @return 사용자 정보 JsonObject (id, email, nickname 등)
     */
    public static Future<JsonObject> authenticate(
            WebClient webClient,
            String code,
            String clientId,
            String clientSecret,
            String redirectUri) {

        return exchangeToken(webClient, code, clientId, clientSecret, redirectUri)
            .compose(tokenResponse -> {
                String accessToken = tokenResponse.getString("access_token");
                return getUserInfo(webClient, accessToken);
            });
    }
}
