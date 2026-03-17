package com.foxya.coin.user;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.FileUpload;
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
import com.foxya.coin.device.DeviceRepository;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import com.foxya.coin.user.dto.MeResponseDto;
import com.foxya.coin.user.dto.ExternalLinkCodeRequestDto;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.*;
import static com.foxya.coin.common.jsonschema.Schemas.*;

@Slf4j
public class UserHandler extends BaseHandler {
    private static final Pattern APNS_DEVICE_TOKEN_PATTERN = Pattern.compile("^[A-Fa-f0-9]{64}$");

    private final UserService userService;
    private final JWTAuth jwtAuth;
    private final DeviceRepository deviceRepository;
    private final io.vertx.pgclient.PgPool pool;

    public UserHandler(Vertx vertx, UserService userService, JWTAuth jwtAuth) {
        this(vertx, userService, jwtAuth, null, null);
    }

    public UserHandler(Vertx vertx, UserService userService, JWTAuth jwtAuth,
                       DeviceRepository deviceRepository, io.vertx.pgclient.PgPool pool) {
        super(vertx);
        this.userService = userService;
        this.jwtAuth = jwtAuth;
        this.deviceRepository = deviceRepository;
        this.pool = pool;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 공개 API (인증 불필요)
        // Legacy direct register route disabled; use /api/v1/auth/register only.
        router.post("/login").handler(loginValidation(parser)).handler(this::login);

        // 프로필 이미지 조회 (공개)
        router.get("/profile-images/:userId/:variant")
            .handler(this::getProfileImage);
        
        // 인증 필요 API - 구체적인 경로를 먼저 등록 (파라미터 경로보다 우선)

        // 내 정보 조회/수정 (/me는 /:id 보다 먼저)
        router.get("/me")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getMe);

        router.patch("/me")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(patchMeValidation(parser))
            .handler(this::patchMe);

        router.patch("/me/review-prompt")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(reviewPromptValidation(parser))
            .handler(this::patchReviewPrompt);

        router.post("/me/profile-image")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::uploadProfileImage);

        router.delete("/me/profile-image")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::deleteProfileImage);

        // 이메일 관련 API (가장 구체적인 경로 우선)
        router.get("/email")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getEmailInfo);

        router.post("/email/confirm-password")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(confirmPasswordForEmailValidation(parser))
            .handler(this::confirmPasswordForEmail);

        router.post("/email/send-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(sendEmailCodeValidation(parser))
            .handler(this::sendEmailCode);

        router.post("/email/verify")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(verifyEmailValidation(parser))
            .handler(this::verifyEmail);

        // 외부 연동 코드 발급
        router.post("/external-ids/link-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(externalLinkCodeValidation(parser))
            .handler(this::issueExternalLinkCode);

        router.get("/external-ids/status")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getExternalLinkStatus);
        
        // 레퍼럴 코드 관련 API
        router.get("/referral-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getReferralCode);

        // FCM 푸시 토큰 등록/갱신 (앱에서 로그인 후 호출)
        router.patch("/fcm-token")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(fcmTokenValidation(parser))
            .handler(this::updateFcmToken);

        // 디바이스별 알림 설정 조회/수정
        router.get("/notification-settings")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getNotificationSettings);

        router.patch("/notification-settings")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(notificationSettingsValidation(parser))
            .handler(this::updateNotificationSettings);
        
        router.post("/generate/referral-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::generateReferralCode);
        
        // ADMIN이 특정 유저에게 레퍼럴 코드 생성
        router.post("/:id/generate/referral-code")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN))
            .handler(this::generateReferralCodeForUser);
        
        // 사용자 조회 (파라미터 라우트는 모든 구체적인 라우트보다 나중에 등록)
        router.get("/:id")
            .handler(JWTAuthHandler.create(jwtAuth))
            .handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.USER))
            .handler(this::getUser);
        
        return router;
    }
    
    /**
     * PATCH /me: name, nickname, phone 모두 선택. 빈 문자열 허용 (name·nickname은 미변경, phone은 삭제).
     */
    private Handler<RoutingContext> patchMeValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .optionalProperty("name", stringSchema().with(maxLength(50)))
                    .optionalProperty("nickname", stringSchema().with(maxLength(20)))
                    .optionalProperty("phone", stringSchema().with(maxLength(20)))
                    .optionalProperty("gender", stringSchema().with(maxLength(1)))
                    .optionalProperty("country", stringSchema().with(minLength(2), maxLength(3)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> reviewPromptValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .optionalProperty("dismissed", booleanSchema())
                    .optionalProperty("markShown", booleanSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> externalLinkCodeValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("provider", stringSchema().with(minLength(1), maxLength(50)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> fcmTokenValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("deviceId", stringSchema().with(minLength(1), maxLength(128)))
                    .requiredProperty("fcmToken", stringSchema().with(minLength(1), maxLength(512)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> notificationSettingsValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("pushEnabled", booleanSchema())
                    .optionalProperty("deviceId", stringSchema().with(minLength(1), maxLength(128)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private void updateFcmToken(RoutingContext ctx) {
        if (deviceRepository == null || pool == null) {
            ctx.fail(503, new IllegalStateException("FCM token API not configured"));
            return;
        }
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        io.vertx.core.json.JsonObject body = ctx.body().asJsonObject();
        String deviceId = resolveDeviceId(ctx, body);
        if (deviceId == null || deviceId.isBlank()) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("deviceId가 필요합니다."));
            return;
        }
        String fcmToken = body.getString("fcmToken");
        if (fcmToken == null || fcmToken.isBlank()) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("fcmToken 값이 필요합니다."));
            return;
        }
        // iOS APNs device token(64-hex)이 잘못 전달되는 경우를 조기 차단한다.
        if (APNS_DEVICE_TOKEN_PATTERN.matcher(fcmToken.trim()).matches()) {
            log.warn("Rejected APNs token for FCM endpoint - userId={}, deviceId={}", userId, deviceId);
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("iOS APNs 토큰이 아니라 FCM registration token을 보내주세요."));
            return;
        }
        response(ctx, deviceRepository.updateFcmToken(pool, userId, deviceId, fcmToken)
            .map(updated -> io.vertx.core.json.JsonObject.of("success", updated)));
    }

    private void getNotificationSettings(RoutingContext ctx) {
        if (deviceRepository == null || pool == null) {
            ctx.fail(503, new IllegalStateException("Notification settings API not configured"));
            return;
        }
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String deviceId = resolveDeviceId(ctx, null);
        if (deviceId == null || deviceId.isBlank()) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("deviceId가 필요합니다."));
            return;
        }

        response(ctx, deviceRepository.getPushEnabledByUserAndDeviceId(pool, userId, deviceId)
            .map(pushEnabled -> io.vertx.core.json.JsonObject.of(
                "deviceId", deviceId,
                "pushEnabled", pushEnabled == null ? Boolean.TRUE : pushEnabled
            )));
    }

    private void updateNotificationSettings(RoutingContext ctx) {
        if (deviceRepository == null || pool == null) {
            ctx.fail(503, new IllegalStateException("Notification settings API not configured"));
            return;
        }

        Long userId = AuthUtils.getUserIdOf(ctx.user());
        io.vertx.core.json.JsonObject body = ctx.body().asJsonObject();
        String deviceId = resolveDeviceId(ctx, body);
        if (deviceId == null || deviceId.isBlank()) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("deviceId가 필요합니다."));
            return;
        }
        Boolean pushEnabled = body.getBoolean("pushEnabled");
        if (pushEnabled == null) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("pushEnabled 값이 필요합니다."));
            return;
        }

        response(ctx, deviceRepository.updatePushEnabledByUserAndDeviceId(pool, userId, deviceId, pushEnabled)
            .map(updated -> io.vertx.core.json.JsonObject.of(
                "success", updated,
                "deviceId", deviceId,
                "pushEnabled", pushEnabled
            )));
    }

    private String resolveDeviceId(RoutingContext ctx, io.vertx.core.json.JsonObject body) {
        String deviceId = ctx.request().getHeader("X-Device-Id");
        if ((deviceId == null || deviceId.isBlank()) && body != null) {
            deviceId = body.getString("deviceId");
        }
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = ctx.request().getParam("deviceId");
        }
        return deviceId;
    }

    private void getMe(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.getMe(userId));
    }

    private void patchMe(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.updateMe(userId, ctx.getBodyAsJson()), v -> null);
    }

    private void patchReviewPrompt(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.updateReviewPromptState(userId, ctx.getBodyAsJson()));
    }

    private void uploadProfileImage(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        if (ctx.fileUploads() == null || ctx.fileUploads().isEmpty()) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("업로드할 이미지 파일을 선택해주세요."));
            return;
        }
        FileUpload upload = ctx.fileUploads().iterator().next();
        Path uploadedTempPath = Path.of(upload.uploadedFileName());
        response(ctx, userService.uploadMyProfileImage(
            userId,
            uploadedTempPath,
            upload.contentType(),
            upload.fileName(),
            upload.size()
        ));
    }

    private void deleteProfileImage(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.deleteMyProfileImage(userId), v -> null);
    }

    private void getProfileImage(RoutingContext ctx) {
        String userIdParam = ctx.pathParam("userId");
        Long userId;
        try {
            userId = Long.parseLong(userIdParam);
        } catch (Exception e) {
            ctx.fail(400, new com.foxya.coin.common.exceptions.BadRequestException("유효하지 않은 userId 입니다."));
            return;
        }
        String variant = ctx.pathParam("variant");
        userService.getProfileImagePath(userId, variant)
            .onSuccess(path -> ctx.response()
                .putHeader("Content-Type", "image/png")
                .putHeader("Cache-Control", "public, max-age=86400")
                .sendFile(path.toString(), ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                    }
                }))
            .onFailure(ctx::fail);
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
                    .optionalProperty("deviceId", anyOf(stringSchema().with(minLength(8), maxLength(128)), schema().withKeyword("type", "null")))
                    .optionalProperty("deviceType", anyOf(enumStringSchema(new String[]{"WEB", "MOBILE"}), schema().withKeyword("type", "null")))
                    .optionalProperty("deviceOs", anyOf(enumStringSchema(new String[]{"WEB", "IOS", "ANDROID"}), schema().withKeyword("type", "null")))
                    .optionalProperty("appVersion", anyOf(stringSchema().with(minLength(0), maxLength(32)), schema().withKeyword("type", "null")))
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
        if (idParam == null || idParam.isEmpty()) {
            log.warn("User ID parameter is missing");
            ctx.fail(404, new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
            return;
        }
        
        // 숫자가 아닌 경우 (예: "email") 404 반환
        if (!idParam.matches("^\\d+$")) {
            log.warn("Invalid user ID format (not numeric): {}", idParam);
            ctx.fail(404, new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
            return;
        }
        
        try {
            Long id = Long.valueOf(idParam);
            response(ctx, userService.getUserProfile(id));
        } catch (NumberFormatException e) {
            log.warn("Invalid user ID format: {}", idParam, e);
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
     * 이메일 설정 시 비밀번호 확인 Validation
     */
    private Handler<RoutingContext> confirmPasswordForEmailValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("password", passwordSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 이메일 설정 시 비밀번호 확인 (성공 시 프론트에서 인증 코드 발송 버튼 활성화 등에 사용)
     */
    private void confirmPasswordForEmail(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String password = ctx.getBodyAsJson().getString("password");
        log.info("Confirm password for email - userId: {}", userId);
        response(ctx, userService.confirmPasswordForEmail(userId, password));
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
     * 이메일 인증 및 등록 (성공 시 login_id를 새 이메일로 변경)
     */
    private void verifyEmail(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String email = ctx.getBodyAsJson().getString("email");
        String code = ctx.getBodyAsJson().getString("code");
        log.info("Verifying email - userId: {}, email: {}", userId, email);
        response(ctx, userService.verifyEmail(userId, email, code));
    }

    /**
     * 외부 연동 코드 발급
     */
    private void issueExternalLinkCode(RoutingContext ctx) {
        ExternalLinkCodeRequestDto dto = getObjectMapper().convertValue(
            Utils.getMapFromJsonObject(ctx.getBodyAsJson()),
            ExternalLinkCodeRequestDto.class
        );

        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("External link code issue - userId: {}, provider: {}", userId, dto.getProvider());
        response(ctx, userService.issueExternalLinkCode(userId, dto.getProvider()));
    }

    /**
     * 외부 연동 상태 조회
     */
    private void getExternalLinkStatus(RoutingContext ctx) {
        String provider = ctx.request().getParam("provider");
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        log.info("External link status - userId: {}, provider: {}", userId, provider);
        response(ctx, userService.getExternalLinkStatus(userId, provider));
    }
}
