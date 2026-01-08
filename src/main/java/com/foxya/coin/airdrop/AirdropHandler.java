package com.foxya.coin.airdrop;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import com.foxya.coin.airdrop.dto.AirdropTransferRequestDto;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;

@Slf4j
public class AirdropHandler extends BaseHandler {
    
    private final AirdropService airdropService;
    private final JWTAuth jwtAuth;
    
    public AirdropHandler(Vertx vertx, AirdropService airdropService, JWTAuth jwtAuth) {
        super(vertx);
        this.airdropService = airdropService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 모든 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));
        router.route().handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN));
        
        // 에어드랍 상태 조회
        router.get("/status")
            .handler(this::getAirdropStatus);
        
        // 에어드랍 전송
        router.post("/transfer")
            .handler(transferValidation(parser))
            .handler(this::transferAirdrop);
        
        return router;
    }
    
    /**
     * 전송 Validation
     */
    private ValidationHandler transferValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("amount", numberSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 에어드랍 상태 조회
     */
    private void getAirdropStatus(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("에어드랍 상태 조회 요청 - userId: {}", userId);
        response(ctx, airdropService.getAirdropStatus(userId));
    }
    
    /**
     * 에어드랍 전송
     */
    private void transferAirdrop(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        AirdropTransferRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            AirdropTransferRequestDto.class
        );
        
        log.info("에어드랍 전송 요청 - userId: {}, amount: {}", userId, dto.getAmount());
        response(ctx, airdropService.transferAirdrop(userId, dto));
    }
}

