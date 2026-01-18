package com.foxya.coin.common.utils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.buffer.Buffer;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;

/**
 * Google OAuth 2.0 유틸리티 클래스
 * Google OAuth 인증 관련 공통 로직을 제공합니다.
 */
@Slf4j
public class GoogleOAuthUtil {
    
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo";
    
    /**
     * Google OAuth Authorization Code를 Access Token으로 교환
     * popup 모드와 redirect 모드를 모두 지원합니다.
     * 
     * @param webClient WebClient 인스턴스
     * @param code Authorization code
     * @param clientId Google OAuth client ID
     * @param clientSecret Google OAuth client secret
     * @param redirectUri Redirect URI (redirect 모드) 또는 null/"postmessage" (popup 모드)
     * @return Access Token과 ID Token을 포함한 JsonObject
     */
    public static Future<JsonObject> exchangeToken(
            WebClient webClient,
            String code,
            String clientId,
            String clientSecret,
            String redirectUri,
            String codeVerifier) {
        
        JsonObject tokenRequest = new JsonObject()
            .put("code", code)
            .put("client_id", clientId)
            .put("grant_type", "authorization_code");

        if (clientSecret != null && !clientSecret.isEmpty()) {
            tokenRequest.put("client_secret", clientSecret);
        }

        if (codeVerifier != null && !codeVerifier.isEmpty()) {
            tokenRequest.put("code_verifier", codeVerifier);
        }
        
        // redirectUri가 null이 아니면 추가 (popup 모드에서는 "postmessage" 사용)
        if (redirectUri != null) {
            tokenRequest.put("redirect_uri", redirectUri);
        }
        
        log.debug("Exchanging Google authorization code for token. redirectUri: {}", redirectUri);
        
        return webClient.postAbs(TOKEN_ENDPOINT)
            .sendJsonObject(tokenRequest)
            .compose(response -> {
                if (response.statusCode() == 200) {
                    JsonObject tokenResponse = response.bodyAsJsonObject();
                    String accessToken = tokenResponse.getString("access_token");
                    
                    if (accessToken == null) {
                        log.error("Access token not found in Google response");
                        return Future.failedFuture(new UnauthorizedException("Failed to verify Google token"));
                    }
                    
                    return Future.succeededFuture(tokenResponse);
                } else {
                    String body = response.bodyAsString();
                    log.error("Failed to exchange Google authorization code. Status: {}, Body: {}", 
                        response.statusCode(), body);
                    
                    // redirect_uri_mismatch 오류인 경우 "postmessage"로 재시도 (popup 모드)
                    if (response.statusCode() == 400 && body != null && body.contains("redirect_uri_mismatch")) {
                        if (!"postmessage".equals(redirectUri)) {
                            log.info("Retrying with postmessage redirect_uri for popup mode");
                            return exchangeToken(webClient, code, clientId, clientSecret, "postmessage", codeVerifier);
                        }
                    }
                    
                    return Future.failedFuture(new UnauthorizedException("Invalid authorization code"));
                }
            })
            .recover(throwable -> {
                // redirect_uri_mismatch 오류인 경우 "postmessage"로 재시도 (popup 모드)
                String errorMsg = throwable.getMessage();
                if (errorMsg != null && errorMsg.contains("redirect_uri_mismatch")) {
                    if (!"postmessage".equals(redirectUri)) {
                        log.info("Retrying with postmessage redirect_uri for popup mode (from exception)");
                        return exchangeToken(webClient, code, clientId, clientSecret, "postmessage", codeVerifier);
                    }
                }
                return Future.failedFuture(throwable);
            });
    }
    
    /**
     * Google Access Token으로 사용자 정보 조회
     * 
     * @param webClient WebClient 인스턴스
     * @param accessToken Google Access Token
     * @return 사용자 정보 (id, email, name, picture 등)
     */
    public static Future<JsonObject> getUserInfo(WebClient webClient, String accessToken) {
        log.debug("Fetching Google user info");
        
        return webClient.getAbs(USERINFO_ENDPOINT)
            .putHeader("Authorization", "Bearer " + accessToken)
            .send()
            .compose(response -> {
                if (response.statusCode() != 200) {
                    log.error("Failed to get Google user info. Status: {}, Body: {}", 
                        response.statusCode(), response.bodyAsString());
                    return Future.failedFuture(new UnauthorizedException("Failed to verify Google token"));
                }
                
                JsonObject userInfo = response.bodyAsJsonObject();
                String googleId = userInfo.getString("id");
                String email = userInfo.getString("email");
                
                if (email == null || googleId == null) {
                    log.error("Email or Google ID not found in user info");
                    return Future.failedFuture(new UnauthorizedException("Failed to verify Google token"));
                }
                
                return Future.succeededFuture(userInfo);
            });
    }
    
    /**
     * Google OAuth 전체 플로우 실행 (토큰 교환 + 사용자 정보 조회)
     * popup 모드와 redirect 모드를 자동으로 처리합니다.
     * 
     * @param webClient WebClient 인스턴스
     * @param code Authorization code
     * @param clientId Google OAuth client ID
     * @param clientSecret Google OAuth client secret
     * @param redirectUri Redirect URI (redirect 모드) 또는 null (popup 모드)
     * @return 사용자 정보 JsonObject (id, email, name, picture 등)
     */
    public static Future<JsonObject> authenticate(
            WebClient webClient,
            String code,
            String clientId,
            String clientSecret,
            String redirectUri,
            String codeVerifier) {
        
        return exchangeToken(webClient, code, clientId, clientSecret, redirectUri, codeVerifier)
            .compose(tokenResponse -> {
                String accessToken = tokenResponse.getString("access_token");
                return getUserInfo(webClient, accessToken);
            });
    }
}
