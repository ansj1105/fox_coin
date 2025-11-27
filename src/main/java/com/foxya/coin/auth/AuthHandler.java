package com.foxya.coin.auth;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.auth.dto.LoginDto;
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
        router.post("/login").handler(this.loginValidation(parser)).handler(this::login);
        router.post("/register").handler(this.registerValidation(parser)).handler(this::register);
        router.post("/api-key").handler(this.apiKeyValidation(parser)).handler(this::apiKey);
        
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
        
        return router;
    }
    
    /**
     * 로그인 Validation
     */
    private Handler<RoutingContext> loginValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("username", stringSchema().with(minLength(3), maxLength(50)))
                    .requiredProperty("password", stringSchema().with(minLength(8), maxLength(20)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 회원가입 Validation
     */
    private Handler<RoutingContext> registerValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("username", stringSchema().with(minLength(3), maxLength(50)))
                    .requiredProperty("email", emailSchema())
                    .requiredProperty("password", passwordSchema())
                    .requiredProperty("phone", phoneSchema())
                    .optionalProperty("referralCode", stringSchema().with(minLength(6), maxLength(20)))
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
     * 로그인
     */
    private void login(RoutingContext ctx) {
        LoginDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            LoginDto.class
        );
        
        log.info("Login attempt for user: {}", dto.getUsername());
        response(ctx, authService.login(dto));
    }
    
    /**
     * 회원가입
     */
    private void register(RoutingContext ctx) {
        com.foxya.coin.user.dto.CreateUserDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            com.foxya.coin.user.dto.CreateUserDto.class
        );
        
        log.info("Register attempt for user: {}", dto.getUsername());
        response(ctx, authService.register(dto));
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
        
        log.info("Logout request from user: {}", userId);
        response(ctx, authService.logout(userId));
    }
}

