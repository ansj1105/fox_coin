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
        
        // 레퍼럴 관계 삭제 (Soft Delete)
        router.delete("/")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::deleteReferralRelation);
        
        // 레퍼럴 관계 완전 삭제 (Hard Delete)
        router.delete("/hard")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::hardDeleteReferralRelation);
        
        // 팀 정보 조회
        router.get("/team")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getTeamInfo);
        
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
     * 레퍼럴 관계 삭제 (Soft Delete)
     */
    private void deleteReferralRelation(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("User {} soft deleting referral relation", userId);
        response(ctx, referralService.deleteReferralRelation(userId));
    }
    
    /**
     * 레퍼럴 관계 완전 삭제 (Hard Delete)
     */
    private void hardDeleteReferralRelation(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("User {} hard deleting referral relation", userId);
        response(ctx, referralService.hardDeleteReferralRelation(userId));
    }
    
    /**
     * 팀 정보 조회
     */
    private void getTeamInfo(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String tab = ctx.request().getParam("tab");
        String period = ctx.request().getParam("period");
        String limitStr = ctx.request().getParam("limit");
        String offsetStr = ctx.request().getParam("offset");
        
        if (tab == null || tab.isEmpty()) {
            tab = "MEMBERS";
        }
        if (period == null || period.isEmpty()) {
            period = "TODAY";
        }
        Integer limit = limitStr != null && !limitStr.isEmpty() ? Integer.parseInt(limitStr) : 20;
        Integer offset = offsetStr != null && !offsetStr.isEmpty() ? Integer.parseInt(offsetStr) : 0;
        
        log.info("Getting team info for user: {}, tab: {}, period: {}, limit: {}, offset: {}", userId, tab, period, limit, offset);
        response(ctx, referralService.getTeamInfo(userId, tab, period, limit, offset));
    }
}

