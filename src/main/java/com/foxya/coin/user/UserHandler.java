package com.foxya.coin.user;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
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
    
    public UserHandler(Vertx vertx, UserService userService) {
        super(vertx);
        this.userService = userService;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        SchemaParser parser = createSchemaParser();
        
        // 공개 API (인증 불필요)
        router.post("/register").handler(registerValidation(parser)).handler(this::register);
        router.post("/login").handler(loginValidation(parser)).handler(this::login);
        
        // 인증 필요 API
        router.get("/:id")
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
                    .requiredProperty("username", stringSchema().with(minLength(3), maxLength(50)))
                    .requiredProperty("email", emailSchema())
                    .requiredProperty("password", passwordSchema())
                    .requiredProperty("phone", phoneSchema())
                    .optionalProperty("referralCode", stringSchema().with(minLength(6), maxLength(20)))
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
                    .requiredProperty("username", stringSchema().with(minLength(3), maxLength(50)))
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
}

