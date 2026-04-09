package com.foxya.coin.security;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.user.UserService;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.validation.ValidationHandler;
import io.vertx.json.schema.SchemaParser;
import lombok.extern.slf4j.Slf4j;

import static io.vertx.ext.web.validation.builder.Bodies.json;
import static io.vertx.json.schema.common.dsl.Schemas.objectSchema;
import static io.vertx.json.schema.common.dsl.Schemas.stringSchema;
import static io.vertx.json.schema.common.dsl.Schemas.booleanSchema;
import static io.vertx.json.schema.common.dsl.Schemas.intSchema;
import static io.vertx.json.schema.common.dsl.Keywords.minLength;
import static io.vertx.json.schema.common.dsl.Keywords.maxLength;

/**
 * 보안 관련 API (거래 비밀번호 등)
 */
@Slf4j
public class SecurityHandler extends BaseHandler {

    private final UserService userService;
    private final JWTAuth jwtAuth;

    public SecurityHandler(Vertx vertx, UserService userService, JWTAuth jwtAuth) {
        super(vertx);
        this.userService = userService;
        this.jwtAuth = jwtAuth;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());

        SchemaParser parser = createSchemaParser();

        // 모든 보안 API에 JWT 인증 적용
        router.route().handler(JWTAuthHandler.create(jwtAuth));

        // 거래 비밀번호 설정/변경
        router.post("/transaction-password")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(setTransactionPasswordValidation(parser))
            .handler(this::setTransactionPassword);

        router.get("/offline-pay/status")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getOfflinePayStatus);

        router.post("/offline-pay/pin/verify")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(verifyOfflinePayPinValidation(parser))
            .handler(this::verifyOfflinePayPin);

        router.get("/offline-pay/settings")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getOfflinePaySettings);

        router.put("/offline-pay/settings")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(updateOfflinePaySettingsValidation(parser))
            .handler(this::updateOfflinePaySettings);

        router.get("/offline-pay/trust-center")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getOfflinePayTrustCenter);

        router.put("/offline-pay/trust-center")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(updateOfflinePayTrustCenterValidation(parser))
            .handler(this::updateOfflinePayTrustCenter);

        router.get("/offline-pay/notification-center")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getOfflinePayNotificationCenter);

        router.put("/offline-pay/notification-center")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(updateOfflinePayActivityCenterValidation(parser))
            .handler(this::updateOfflinePayNotificationCenter);

        router.get("/offline-pay/settlement-center")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(this::getOfflinePaySettlementCenter);

        router.put("/offline-pay/settlement-center")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(updateOfflinePayActivityCenterValidation(parser))
            .handler(this::updateOfflinePaySettlementCenter);

        router.post("/offline-pay/shared-details")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(createOfflinePaySharedDetailValidation(parser))
            .handler(this::createOfflinePaySharedDetail);

        // 로그인 비밀번호 변경 (이메일 인증 코드 기반)
        router.post("/login-password")
            .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
            .handler(changeLoginPasswordValidation(parser))
            .handler(this::changeLoginPassword);

        return router;
    }

    private Handler<RoutingContext> setTransactionPasswordValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("code", stringSchema().with(minLength(6), maxLength(6)))
                    .requiredProperty("newPassword", stringSchema().with(minLength(6), maxLength(6)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> changeLoginPasswordValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("code", stringSchema().with(minLength(6), maxLength(6)))
                    .requiredProperty("newPassword", stringSchema().with(minLength(8)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> verifyOfflinePayPinValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("pin", stringSchema().with(minLength(6), maxLength(6)))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> updateOfflinePaySettingsValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .property("securityLevelHighEnabled", booleanSchema())
                    .property("faceIdSettingEnabled", booleanSchema())
                    .property("fingerprintSettingEnabled", booleanSchema())
                    .property("paymentOfflineEnabled", booleanSchema())
                    .property("paymentBleEnabled", booleanSchema())
                    .property("paymentNfcEnabled", booleanSchema())
                    .property("paymentApprovalMode", stringSchema())
                    .property("settlementAutoEnabled", booleanSchema())
                    .property("settlementCycleMinutes", intSchema())
                    .property("storeOfflineEnabled", booleanSchema())
                    .property("storeBleEnabled", booleanSchema())
                    .property("storeNfcEnabled", booleanSchema())
                    .property("storeMerchantLabel", stringSchema())
                    .property("paymentCompletedAlertEnabled", booleanSchema())
                    .property("incomingRequestAlertEnabled", booleanSchema())
                    .property("failedAlertEnabled", booleanSchema())
                    .property("settlementCompletedAlertEnabled", booleanSchema())
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> createOfflinePaySharedDetailValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .requiredProperty("itemId", stringSchema().with(minLength(1), maxLength(255)))
                    .requiredProperty("payload", objectSchema().allowAdditionalProperties(true))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> updateOfflinePayTrustCenterValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .property("platform", stringSchema())
                    .property("deviceName", stringSchema())
                    .property("teeAvailable", booleanSchema())
                    .property("keySigningActive", booleanSchema())
                    .property("keyProvider", stringSchema())
                    .property("hardwareBackedKey", booleanSchema())
                    .property("userPresenceProtected", booleanSchema())
                    .property("secureHardwareLevel", stringSchema())
                    .property("attestationClass", stringSchema())
                    .property("attestationVerdict", stringSchema())
                    .property("serverVerifiedTrustLevel", stringSchema())
                    .property("deviceRegistrationId", stringSchema())
                    .property("sourceDeviceId", stringSchema())
                    .property("deviceBindingKey", stringSchema())
                    .property("appVersion", stringSchema())
                    .property("collectedAt", stringSchema())
                    .property("faceAvailable", booleanSchema())
                    .property("fingerprintAvailable", booleanSchema())
                    .property("authBindingKey", stringSchema())
                    .property("lastVerifiedAuthMethod", stringSchema())
                    .property("lastVerifiedAt", stringSchema())
                    .property("lastSyncedAt", stringSchema())
                    .property("syncStatus", stringSchema())
                    .property("proofLogs", io.vertx.json.schema.common.dsl.Schemas.arraySchema().items(
                        objectSchema()
                            .requiredProperty("id", stringSchema())
                            .property("eventType", stringSchema())
                            .property("eventStatus", stringSchema())
                            .property("message", stringSchema())
                            .property("reasonCode", stringSchema())
                            .property("metadata", objectSchema().allowAdditionalProperties(true))
                            .property("createdAt", stringSchema())
                            .allowAdditionalProperties(false)
                    ))
                    .property("statusLogs", io.vertx.json.schema.common.dsl.Schemas.arraySchema().items(
                        objectSchema()
                            .requiredProperty("id", stringSchema())
                            .property("eventType", stringSchema())
                            .property("eventStatus", stringSchema())
                            .property("message", stringSchema())
                            .property("reasonCode", stringSchema())
                            .property("metadata", objectSchema().allowAdditionalProperties(true))
                            .property("createdAt", stringSchema())
                            .allowAdditionalProperties(false)
                    ))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    private Handler<RoutingContext> updateOfflinePayActivityCenterValidation(SchemaParser parser) {
        return ValidationHandler.builder(parser)
            .body(json(
                objectSchema()
                    .property("logs", io.vertx.json.schema.common.dsl.Schemas.arraySchema().items(
                        objectSchema()
                            .requiredProperty("id", stringSchema())
                            .property("category", stringSchema())
                            .property("eventStatus", stringSchema())
                            .property("title", stringSchema())
                            .property("message", stringSchema())
                            .property("reasonCode", stringSchema())
                            .property("requestId", stringSchema())
                            .property("settlementId", stringSchema())
                            .property("metadata", objectSchema().allowAdditionalProperties(true))
                            .property("createdAt", stringSchema())
                            .allowAdditionalProperties(false)
                    ))
                    .allowAdditionalProperties(false)
            ))
            .build();
    }

    /**
     * 거래 비밀번호 설정/변경
     */
    private void setTransactionPassword(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());

        var bodyMap = Utils.getMapFromJsonObject(ctx.getBodyAsJson());
        String code = (String) bodyMap.get("code");
        String newPassword = (String) bodyMap.get("newPassword");

        log.info("Setting transaction password - userId: {}", userId);
        response(ctx, userService.setTransactionPassword(userId, code, newPassword));
    }

    private void getOfflinePayStatus(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        String deviceId = ctx.request().getHeader("X-Device-Id");
        response(ctx, userService.getOfflinePaySecurityStatus(userId, deviceId));
    }

    private void verifyOfflinePayPin(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        var bodyMap = Utils.getMapFromJsonObject(ctx.getBodyAsJson());
        String pin = (String) bodyMap.get("pin");
        response(ctx, userService.verifyOfflinePayPin(userId, pin));
    }

    private void getOfflinePaySettings(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.getOfflinePaySettings(userId));
    }

    private void updateOfflinePaySettings(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        JsonObject body = ctx.getBodyAsJson();
        response(ctx, userService.updateOfflinePaySettings(userId, BaseHandler.getObjectMapper().convertValue(
            body.getMap(),
            com.foxya.coin.security.dto.OfflinePaySettingsDto.class
        )));
    }

    private void getOfflinePayTrustCenter(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.getOfflinePayTrustCenter(userId));
    }

    private void updateOfflinePayTrustCenter(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        JsonObject body = ctx.getBodyAsJson();
        response(ctx, userService.updateOfflinePayTrustCenter(
            userId,
            BaseHandler.getObjectMapper().convertValue(body.getMap(), com.foxya.coin.security.dto.OfflinePayTrustCenterDto.class)
        ));
    }

    private void getOfflinePayNotificationCenter(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.getOfflinePayNotificationCenter(userId));
    }

    private void updateOfflinePayNotificationCenter(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        JsonObject body = ctx.getBodyAsJson();
        response(ctx, userService.updateOfflinePayNotificationCenter(
            userId,
            BaseHandler.getObjectMapper().convertValue(body.getMap(), com.foxya.coin.security.dto.OfflinePayNotificationCenterDto.class)
        ));
    }

    private void getOfflinePaySettlementCenter(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        response(ctx, userService.getOfflinePaySettlementCenter(userId));
    }

    private void updateOfflinePaySettlementCenter(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        JsonObject body = ctx.getBodyAsJson();
        response(ctx, userService.updateOfflinePaySettlementCenter(
            userId,
            BaseHandler.getObjectMapper().convertValue(body.getMap(), com.foxya.coin.security.dto.OfflinePaySettlementCenterDto.class)
        ));
    }

    private void createOfflinePaySharedDetail(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());
        JsonObject body = ctx.getBodyAsJson();
        response(ctx, userService.createOfflinePaySharedDetail(
            userId,
            body.getString("itemId"),
            body.getJsonObject("payload")
        ));
    }

    /**
     * 로그인 비밀번호 변경 (이메일 인증 코드 기반)
     */
    private void changeLoginPassword(RoutingContext ctx) {
        Long userId = AuthUtils.getUserIdOf(ctx.user());

        var bodyMap = Utils.getMapFromJsonObject(ctx.getBodyAsJson());
        String code = (String) bodyMap.get("code");
        String newPassword = (String) bodyMap.get("newPassword");

        log.info("Changing login password - userId: {}", userId);
        response(ctx, userService.changeLoginPassword(userId, code, newPassword));
    }
}
