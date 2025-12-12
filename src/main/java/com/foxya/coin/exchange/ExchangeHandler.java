package com.foxya.coin.exchange;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.exchange.dto.ExchangeRequestDto;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;

@Slf4j
public class ExchangeHandler extends BaseHandler {
    
    private final ExchangeService exchangeService;
    private final JWTAuth jwtAuth;
    
    public ExchangeHandler(Vertx vertx, ExchangeService exchangeService, JWTAuth jwtAuth) {
        super(vertx);
        this.exchangeService = exchangeService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 모든 환전 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        
        // 환전 실행
        router.post("/")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(exchangeValidation(parser))
            .handler(this::executeExchange);
        
        // 환전 상세 조회
        router.get("/:exchangeId")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getExchange);
        
        return router;
    }
    
    /**
     * 환전 Validation
     */
    private Handler<RoutingContext> exchangeValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("fromAmount", numberSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 환전 실행
     */
    private void executeExchange(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String requestIp = ctx.request().remoteAddress() != null 
            ? ctx.request().remoteAddress().host() 
            : null;
        
        ExchangeRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            ExchangeRequestDto.class
        );
        
        log.info("환전 실행 요청 - userId: {}, fromAmount: {}", userId, dto.getFromAmount());
        
        response(ctx, exchangeService.executeExchange(userId, dto, requestIp));
    }
    
    /**
     * 환전 상세 조회
     */
    private void getExchange(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String exchangeId = ctx.pathParam("exchangeId");
        
        log.info("환전 상세 조회 - userId: {}, exchangeId: {}", userId, exchangeId);
        
        response(ctx, exchangeService.getExchange(userId, exchangeId));
    }
}

