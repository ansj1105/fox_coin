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

        // 이메일 설정 조회
        router.get("/email")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getEmailInfo);

        // 이메일 인증 코드 발송
        router.post("/email/send-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(sendEmailCodeValidation(parser))
            .handler(this::sendEmailCode);

        // 이메일 인증 및 등록
        router.post("/email/verify")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(verifyEmailValidation(parser))
            .handler(this::verifyEmail);
        
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
        String idParam = ctx.pathParam("id");
        try {
            Long id = Long.valueOf(idParam);
            response(ctx, userService.getUserById(id));
        } catch (NumberFormatException e) {
            log.warn("Invalid user ID format: {}", idParam);
            ctx.fail(404, new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
        }
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

    /**
     * 이메일 설정 조회
     */
    private void getEmailInfo(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("Getting email info for user: {}", userId);
        response(ctx, userService.getEmailInfo(userId));
    }

    /**
     * 이메일 인증 코드 발송 Validation
     */
    private Handler<RoutingContext> sendEmailCodeValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", emailSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 이메일 인증 코드 발송
     */
    private void sendEmailCode(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String email = ctx.getBodyAsJson().getString("email");
        log.info("Sending email verification code - userId: {}, email: {}", userId, email);
        response(ctx, userService.sendEmailVerificationCode(userId, email));
    }

    /**
     * 이메일 인증 요청 Validation
     */
    private Handler<RoutingContext> verifyEmailValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("email", emailSchema())
                    .requiredProperty("code", stringSchema().with(minLength(4), maxLength(10)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 이메일 인증 및 등록
     */
    private void verifyEmail(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String email = ctx.getBodyAsJson().getString("email");
        String code = ctx.getBodyAsJson().getString("code");
        log.info("Verifying email - userId: {}, email: {}", userId, email);
        response(ctx, userService.verifyEmail(userId, email, code));
    }
}

