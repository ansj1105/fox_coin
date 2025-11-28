package com.foxya.coin.referral;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.referral.dto.RegisterReferralDto;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;

@Slf4j
public class ReferralHandler extends BaseHandler {
    
    private final ReferralService referralService;
    private final JWTAuth jwtAuth;
    
    public ReferralHandler(Vertx vertx, ReferralService referralService, JWTAuth jwtAuth) {
        super(vertx);
        this.referralService = referralService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 레퍼럴 코드 등록
        router.post("/register")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(registerValidation(parser))
            .handler(this::registerReferralCode);
        
        // 레퍼럴 통계 조회
        router.get("/:id/stats")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getReferralStats);
        
        // 레퍼럴 관계 삭제
        router.delete("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::deleteReferralRelation);
        
        return router;
    }
    
    /**
     * 레퍼럴 코드 등록 Validation
     */
    private Handler<RoutingContext> registerValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("referralCode", stringSchema().with(minLength(6), maxLength(20)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 레퍼럴 코드 등록
     */
    private void registerReferralCode(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        
        RegisterReferralDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            RegisterReferralDto.class
        );
        
        log.info("User {} registering referral code: {}", userId, dto.getReferralCode());
        response(ctx, referralService.registerReferralCode(userId, dto.getReferralCode()));
    }
    
    /**
     * 레퍼럴 통계 조회
     */
    private void getReferralStats(RoutingContext ctx) {
        Long userId = Long.valueOf(ctx.pathParam("id"));
        log.info("Getting referral stats for user: {}", userId);
        response(ctx, referralService.getReferralStats(userId));
    }
    
    /**
     * 레퍼럴 관계 삭제
     */
    private void deleteReferralRelation(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("User {} deleting referral relation", userId);
        response(ctx, referralService.deleteReferralRelation(userId));
    }
}

