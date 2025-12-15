package com.foxya.coin.security;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.user.UserService;
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
import static io.vertx.json.schema.common.dsl.Schemas.objectSchema;
import static io.vertx.json.schema.common.dsl.Schemas.stringSchema;
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
}


