package com.foxya.coin.client;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import com.foxya.coin.client.dto.RefreshTokenRequestDto;
import com.foxya.coin.client.dto.LinkExternalUserRequestDto;
import com.foxya.coin.client.dto.TokenRequestDto;
import com.foxya.coin.client.dto.UserTokenRequestDto;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.exceptions.ForbiddenException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;

@Slf4j
public class ClientHandler extends BaseHandler {
    
    private final ClientService clientService;
    private final JWTAuth jwtAuth;
    private static final long CLIENT_USER_ID = 0L;
    
    public ClientHandler(Vertx vertx, ClientService clientService, JWTAuth jwtAuth) {
        super(vertx);
        this.clientService = clientService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 공개 API (인증 불필요)
        router.post("/token").handler(tokenValidation(parser)).handler(this::issueToken);
        router.post("/refresh").handler(refreshTokenValidation(parser)).handler(this::refreshToken);
        
        // 인증 필요 API (JWT 토큰 필요)
        router.post("/user-token")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this::requireClientToken)
            .handler(userTokenValidation(parser))
            .handler(this::issueUserToken);
        router.post("/user-data")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(userDataValidation(parser))
            .handler(this::receiveUserData);

        // 외부 서비스 연동 (클라이언트 토큰 필요)
        router.post("/external-ids/link")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(this::requireClientToken)
            .handler(linkExternalUserValidation(parser))
            .handler(this::linkExternalUser);
        
        return router;
    }
    
    /**
     * 토큰 발급 Validation
     */
    private Handler<RoutingContext> tokenValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("apiKey", stringSchema().with(minLength(1)))
                    .requiredProperty("apiSecret", stringSchema().with(minLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * Refresh Token Validation
     */
    private Handler<RoutingContext> refreshTokenValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("refreshToken", stringSchema().with(minLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 유저 데이터 수신 Validation (모든 필드 허용)
     */
    private Handler<RoutingContext> userDataValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("userData", objectSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 외부 사용자 토큰 발급 Validation
     */
    private Handler<RoutingContext> userTokenValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("provider", stringSchema().with(minLength(1)))
                    .requiredProperty("externalId", stringSchema().with(minLength(1)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 외부 연동 Validation
     */
    private Handler<RoutingContext> linkExternalUserValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("provider", stringSchema().with(minLength(1), maxLength(50)))
                    .requiredProperty("externalId", stringSchema().with(minLength(1), maxLength(255)))
                    .requiredProperty("linkCode", stringSchema().with(minLength(1), maxLength(32)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * API Key로 토큰 발급
     */
    private void issueToken(RoutingContext ctx) {
        TokenRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            TokenRequestDto.class
        );
        
        log.info("Token issue request for API Key: {}", dto.getApiKey());
        response(ctx, clientService.issueToken(dto));
    }
    
    /**
     * Refresh Token으로 새 토큰 발급
     */
    private void refreshToken(RoutingContext ctx) {
        RefreshTokenRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            RefreshTokenRequestDto.class
        );
        
        log.info("Token refresh request");
        response(ctx, clientService.refreshToken(dto));
    }
    
    /**
     * 클라이언트 토큰인지 확인 (userId = 0)
     */
    private void requireClientToken(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        if (userId == null || userId != CLIENT_USER_ID) {
            throw new ForbiddenException("클라이언트 토큰이 필요합니다.");
        }
        ctx.next();
    }
    
    /**
     * 외부 사용자 식별자로 유저 토큰 발급
     */
    private void issueUserToken(RoutingContext ctx) {
        UserTokenRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            UserTokenRequestDto.class
        );
        
        log.info("User token issue request - provider: {}, externalId: {}", dto.getProvider(), dto.getExternalId());
        response(ctx, clientService.issueUserToken(dto));
    }
    
    /**
     * 유저 데이터 수신
     */
    private void receiveUserData(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        JsonObject userData = body.getJsonObject("userData");
        
        if (userData == null) {
            log.warn("userData is missing in request");
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("userData 필드가 필요합니다."));
            return;
        }
        
        log.info("Received user data: {}", userData.encodePrettily());
        
        // 여기서 유저 데이터를 처리할 수 있습니다
        // 현재는 로그만 남기고 성공 응답을 반환합니다
        // 나중에 DB 저장 등의 로직을 추가할 수 있습니다
        
        JsonObject responseData = new JsonObject()
            .put("message", "유저 데이터가 성공적으로 수신되었습니다.")
            .put("receivedData", userData);
        
        success(ctx, responseData);
    }

    /**
     * 외부 서비스 연동 (linkCode 기반)
     */
    private void linkExternalUser(RoutingContext ctx) {
        LinkExternalUserRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            LinkExternalUserRequestDto.class
        );

        log.info("External link request - provider: {}, externalId: {}", dto.getProvider(), dto.getExternalId());
        response(ctx, clientService.linkExternalUser(dto));
    }
}
