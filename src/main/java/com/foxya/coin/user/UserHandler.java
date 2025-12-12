package com.foxya.coin.user;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.ext.web.validation.builder.Parameters;
import io.vertx.json.schema.SchemaParser;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;
import static com.foxya.coin.common.jsonschema.Schemas.*;

@Slf4j
public class UserHandler extends BaseHandler {
    
    private final UserService userService;
    private final JWTAuth jwtAuth;
    
    public UserHandler(Vertx vertx, UserService userService, JWTAuth jwtAuth) {
        super(vertx);
        this.userService = userService;
        this.jwtAuth = jwtAuth;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 공개 API (인증 불필요)
        router.post("/register").handler(registerValidation(parser)).handler(this::register);
        router.post("/login").handler(loginValidation(parser)).handler(this::login);
        
        // 인증 필요 API
        // 본인 레퍼럴 코드 생성 (USER)
        router.post("/generate/referral-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::generateReferralCode);
        
        // 사용자의 추천인 코드 조회 (구체적인 라우트를 먼저 등록)
        router.get("/referral-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getReferralCode);
        
        // ADMIN이 특정 유저에게 레퍼럴 코드 생성
        router.post("/:id/generate/referral-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN))
            .handler(this::generateReferralCodeForUser);
        
        // 사용자 조회 (파라미터 라우트는 구체적인 라우트보다 나중에 등록)
        router.get("/:id")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.USER))
            .handler(this::getUser);
        
        return router;
    }
    
    /**
     * 회원가입 Validation
     */
    private Handler<RoutingContext> registerValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("loginId", stringSchema().with(minLength(3), maxLength(50)))
                    .requiredProperty("password", passwordSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    /**
     * 로그인 Validation
     */
    private Handler<RoutingContext> loginValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("loginId", stringSchema().with(minLength(3), maxLength(50)))
                    .requiredProperty("password", stringSchema().with(minLength(8), maxLength(20)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }
    
    private void register(RoutingContext ctx) {
        CreateUserDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            CreateUserDto.class
        );
        
        response(ctx, userService.createUser(dto));
    }
    
    private void login(RoutingContext ctx) {
        LoginDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            LoginDto.class
        );
        
        response(ctx, userService.login(dto));
    }
    
    private void getUser(RoutingContext ctx) {
        Long id = Long.valueOf(ctx.pathParam("id"));
        response(ctx, userService.getUserById(id));
    }
    
    /**
     * 레퍼럴 코드 생성 (본인)
     */
    private void generateReferralCode(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Generating referral code for user: {}", userId);
        response(ctx, userService.generateReferralCode(userId));
    }
    
    /**
     * 레퍼럴 코드 생성 (ADMIN이 특정 유저에게)
     */
    private void generateReferralCodeForUser(RoutingContext ctx) {
        Long adminId = AuthUtils.getUserIdOf(ctx.user());
        Long targetUserId = Long.valueOf(ctx.pathParam("id"));
        log.info("Admin {} generating referral code for user: {}", adminId, targetUserId);
        response(ctx, userService.generateReferralCode(targetUserId));
    }
    
    /**
     * 사용자의 추천인 코드 조회
     */
    private void getReferralCode(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting referral code for user: {}", userId);
        response(ctx, userService.getReferralCode(userId));
    }
}

