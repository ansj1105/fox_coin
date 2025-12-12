package com.foxya.coin.swap;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.swap.dto.SwapRequestDto;
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
public class SwapHandler extends BaseHandler {
    
    private final SwapService swapService;
    private final JWTAuth jwtAuth;
    
    public SwapHandler(Vertx vertx, SwapService swapService, JWTAuth jwtAuth) {
        super(vertx);
        this.swapService = swapService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 모든 스왑 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        
        // 스왑 실행
        router.post("/")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(swapValidation(parser))
            .handler(this::executeSwap);
        
        // 스왑 상세 조회
        router.get("/:swapId")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getSwap);
        
        return router;
    }
    
    /**
     * 스왑 Validation
     */
    private Handler<RoutingContext> swapValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("fromCurrencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .requiredProperty("toCurrencyCode", stringSchema().with(minLength(1), maxLength(10)))
                    .requiredProperty("fromAmount", numberSchema())
                    .requiredProperty("network", stringSchema().with(minLength(1), maxLength(20)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 스왑 실행
     */
    private void executeSwap(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String requestIp = ctx.request().remoteAddress() != null 
            ? ctx.request().remoteAddress().host() 
            : null;
        
        SwapRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            SwapRequestDto.class
        );
        
        log.info("스왑 실행 요청 - userId: {}, fromCurrency: {}, toCurrency: {}, fromAmount: {}", 
            userId, dto.getFromCurrencyCode(), dto.getToCurrencyCode(), dto.getFromAmount());
        
        response(ctx, swapService.executeSwap(userId, dto, requestIp));
    }
    
    /**
     * 스왑 상세 조회
     */
    private void getSwap(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String swapId = ctx.pathParam("swapId");
        
        log.info("스왑 상세 조회 - userId: {}, swapId: {}", userId, swapId);
        
        response(ctx, swapService.getSwap(userId, swapId));
    }
}

