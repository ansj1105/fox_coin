package com.foxya.coin.auth;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.auth.dto.EmailRequestDto;
import com.foxya.coin.auth.dto.EmailVerifyRequestDto;
import com.foxya.coin.auth.dto.LoginDto;
import com.foxya.coin.auth.dto.RefreshRequestDto;
import com.foxya.coin.auth.dto.RegisterRequestDto;
import com.foxya.coin.auth.dto.ApiKeyDto;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;
import static com.foxya.coin.common.jsonschema.Schemas.*;

@Slf4j
public class AuthHandler extends BaseHandler {
    
    private final AuthService authService;
    private final JWTAuth jwtAuth;
    
    public AuthHandler(Vertx vertx, AuthService authService, JWTAuth jwtAuth) {
        super(vertx);
        this.authService = authService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 공개 API
        router.post("/check-email").handler(this.checkEmailValidation(parser)).handler(this::checkEmail);
        router.post("/email/send-code").handler(this.sendSignupCodeValidation(parser)).handler(this::sendSignupCode);
        router.post("/email/verify").handler(this.verifySignupEmailValidation(parser)).handler(this::verifySignupEmail);
        router.post("/register").handler(this.registerValidation(parser)).handler(this::register);
        router.post("/login").handler(this.loginValidation(parser)).handler(this::login);
        router.post("/find-login-id").handler(this.findLoginIdValidation(parser)).handler(this::findLoginId);
        router.post("/send-temp-password").handler(this.sendTempPasswordValidation(parser)).handler(this::sendTempPassword);
        router.post("/api-key").handler(this.apiKeyValidation(parser)).handler(this::apiKey);
        router.post("/google").handler(this.googleLoginValidation(parser)).handler(this::googleLogin);
        router.post("/refresh").handler(this.refreshValidation(parser)).handler(this::refresh);

        // 인증 필요 API
        router.get("/access-token")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this::getAccessToken);
        router.get("/refresh-token")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this::getRefreshToken);
        router.get("/api-key/reissue")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this::reissueApiKey);
        router.post("/logout")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this::logout);
        router.delete("/account")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this.deleteAccountValidation(parser))
            .handler(this::deleteAccount);
        
        // 소셜 연동
        router.post("/link-social")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this.linkSocialValidation(parser))
            .handler(this::linkSocial);
        
        // 본인인증
        router.post("/verify-phone")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this.verifyPhoneValidation(parser))
            .handler(this::verifyPhone);
        
        return router;
    }
    
    /**
     * 로그인 Validation.
     * password: 일반 회원 8자 이상. 소셜 전용은 빈 문자열 "" 허용 (프론트가 항상 password 키 포함).
     */
    private Handler<RoutingContext> loginValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("loginId", stringSchema().with(minLength(3), maxLength(50)))
                    .requiredProperty("password", stringSchema().with(minLength(0), maxLength(256)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 아이디 찾기 Validation
     */
    private Handler<RoutingContext> findLoginIdValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", stringSchema().with(minLength(5), maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 임시 비밀번호 발송 Validation
     */
    private Handler<RoutingContext> sendTempPasswordValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", stringSchema().with(minLength(5), maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> checkEmailValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", stringSchema().with(minLength(5), maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> sendSignupCodeValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", stringSchema().with(minLength(5), maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> verifySignupEmailValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", stringSchema().with(minLength(5), maxLength(255)))
                    .requiredProperty("code", stringSchema().with(minLength(6), maxLength(6)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 회원가입 Validation (이메일 인증 기반: email, code, password, nickname, name, country, gender?, referralCode?)
     */
    private Handler<RoutingContext> registerValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", stringSchema().with(minLength(5), maxLength(255)))
                    .requiredProperty("code", stringSchema().with(minLength(6), maxLength(6)))
                    .requiredProperty("password", passwordSchema())
                    .optionalProperty("referralCode", stringSchema().with(minLength(6), maxLength(20)))
                    .requiredProperty("nickname", nicknameSchema())
                    .requiredProperty("name", stringSchema().with(minLength(1), maxLength(50)))
                    .requiredProperty("country", stringSchema().with(minLength(2), maxLength(3)))
                    .optionalProperty("country_code", stringSchema().with(minLength(2), maxLength(3)))
                    .optionalProperty("gender", stringSchema().with(maxLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * API Key 생성 Validation
     */
    private Handler<RoutingContext> apiKeyValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("name", stringSchema().with(minLength(1), maxLength(100)))
                    .optionalProperty("description", stringSchema().with(maxLength(255)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 소셜 연동 Validation
     */
    private Handler<RoutingContext> linkSocialValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("provider", com.foxya.coin.common.jsonschema.Schemas.enumStringSchema(new String[]{"KAKAO", "GOOGLE", "EMAIL"}))
                    .requiredProperty("token", stringSchema().with(minLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 본인인증 Validation
     */
    private Handler<RoutingContext> verifyPhoneValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("phoneNumber", stringSchema().with(minLength(10), maxLength(20)))
                    .requiredProperty("verificationCode", stringSchema().with(minLength(4), maxLength(10)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 회원 탈퇴 Validation
     */
    private Handler<RoutingContext> deleteAccountValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("password", stringSchema().with(minLength(8), maxLength(20)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * refreshToken으로 access/refresh 재발급 Validation
     */
    private Handler<RoutingContext> refreshValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("refreshToken", stringSchema().with(minLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * Google 로그인 Validation
     */
    private Handler<RoutingContext> googleLoginValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("code", stringSchema().with(minLength(1)))
                    .property("platform", stringSchema().with(minLength(1)))
                    .property("code_verifier", stringSchema().with(minLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    private void checkEmail(RoutingContext ctx) {
        EmailRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            EmailRequestDto.class
        );
        response(ctx, authService.checkEmailAvailable(dto.getEmail()));
    }

    private void sendSignupCode(RoutingContext ctx) {
        EmailRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            EmailRequestDto.class
        );
        log.info("Send signup code for email: {}", dto.getEmail());
        response(ctx, authService.sendSignupEmailCode(dto.getEmail()), v -> null);
    }

    private void verifySignupEmail(RoutingContext ctx) {
        EmailVerifyRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            EmailVerifyRequestDto.class
        );
        response(ctx, authService.verifySignupEmail(dto.getEmail(), dto.getCode()), v -> null);
    }

    /**
     * 로그인
     */
    private void login(RoutingContext ctx) {
        LoginDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            LoginDto.class
        );
        
        log.info("Login attempt for user: {}", dto.getLoginId());
        response(ctx, authService.login(dto));
    }

    /**
     * 아이디 찾기
     */
    private void findLoginId(RoutingContext ctx) {
        EmailRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            EmailRequestDto.class
        );
        log.info("Find login ID request for email: {}", dto.getEmail());
        response(ctx, authService.findLoginId(dto.getEmail()));
    }

    /**
     * 임시 비밀번호 발송
     */
    private void sendTempPassword(RoutingContext ctx) {
        EmailRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            EmailRequestDto.class
        );
        log.info("Send temp password request for email: {}", dto.getEmail());
        response(ctx, authService.sendTempPassword(dto.getEmail()), v -> null);
    }
    
    /**
     * 회원가입 (이메일 인증 완료 후. loginId=email)
     */
    private void register(RoutingContext ctx) {
        RegisterRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            RegisterRequestDto.class
        );
        log.info("Register attempt for email: {}", dto.getEmail());
        response(ctx, authService.registerWithEmail(dto));
    }
    
    /**
     * API Key 생성
     */
    private void apiKey(RoutingContext ctx) {
        ApiKeyDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            ApiKeyDto.class
        );
        
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("API Key creation request from user: {}", userId);
        response(ctx, authService.createApiKey(userId, dto));
    }
    
    /**
     * Access Token 재발급
     */
    private void getAccessToken(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String role = AuthUtils.getUserRoleOf(ctx.user());
        
        log.info("Access token refresh for user: {}", userId);
        response(ctx, authService.refreshAccessToken(userId, role));
    }
    
    /**
     * Refresh Token 재발급
     */
    private void getRefreshToken(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String role = AuthUtils.getUserRoleOf(ctx.user());
        
        log.info("Refresh token refresh for user: {}", userId);
        response(ctx, authService.refreshRefreshToken(userId, role));
    }
    
    /**
     * API Key 재발급
     */
    private void reissueApiKey(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        log.info("API Key reissue request from user: {}", userId);
        response(ctx, authService.reissueApiKey(userId));
    }
    
    /**
     * 로그아웃
     */
    private void logout(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        // Authorization 헤더에서 토큰 추출
        String authHeader = ctx.request().getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        
        // 모든 디바이스 로그아웃 여부 확인 (쿼리 파라미터)
        boolean allDevices = Boolean.parseBoolean(ctx.request().getParam("allDevices", "false"));
        
        log.info("Logout request from user: {}, allDevices: {}", userId, allDevices);
        response(ctx, authService.logout(userId, token, allDevices));
    }
    
    /**
     * 소셜 계정 연동
     */
    private void linkSocial(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        com.foxya.coin.auth.dto.LinkSocialRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            com.foxya.coin.auth.dto.LinkSocialRequestDto.class
        );
        
        log.info("Social link request from user: {}, provider: {}", userId, dto.getProvider());
        response(ctx, authService.linkSocial(userId, dto.getProvider(), dto.getToken()));
    }
    
    /**
     * 본인인증(휴대폰) 등록
     */
    private void verifyPhone(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        com.foxya.coin.auth.dto.VerifyPhoneRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            com.foxya.coin.auth.dto.VerifyPhoneRequestDto.class
        );
        
        log.info("Phone verification request from user: {}", userId);
        response(ctx, authService.verifyPhone(userId, dto.getPhoneNumber(), dto.getVerificationCode()));
    }
    
    /**
     * Google 로그인
     */
    private void googleLogin(RoutingContext ctx) {
        com.foxya.coin.auth.dto.GoogleLoginRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            com.foxya.coin.auth.dto.GoogleLoginRequestDto.class
        );
        
        log.info("Google login attempt");
        response(ctx, authService.googleLogin(dto));
    }

    /**
     * refreshToken으로 accessToken(및 refreshToken 로테이션) 재발급.
     * Authorization 없이 body의 refreshToken만으로 호출. 응답은 { status, accessToken, refreshToken? }.
     */
    private void refresh(RoutingContext ctx) {
        RefreshRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            RefreshRequestDto.class
        );
        log.info("Refresh token request");
        authService.refresh(dto.getRefreshToken())
            .onSuccess(data -> {
                JsonObject body = new JsonObject().put("status", "OK").put("accessToken", data.getAccessToken());
                if (data.getRefreshToken() != null) {
                    body.put("refreshToken", data.getRefreshToken());
                }
                ctx.response()
                    .setStatusCode(200)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(body.encode());
            })
            .onFailure(ctx::fail);
    }
    
    /**
     * 회원 탈퇴
     */
    private void deleteAccount(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        com.foxya.coin.auth.dto.DeleteAccountRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            com.foxya.coin.auth.dto.DeleteAccountRequestDto.class
        );
        
        log.info("Account deletion request from user: {}", userId);
        response(ctx, authService.deleteAccount(userId, dto.getPassword()));
    }
}
