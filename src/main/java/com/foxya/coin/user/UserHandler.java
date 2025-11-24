package com.foxya.coin.user;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.utils.Utils;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserHandler extends BaseHandler {
    
    private final UserService userService;
    
    public UserHandler(Vertx vertx, UserService userService) {
        super(vertx);
        this.userService = userService;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(vertx);
        
        router.post("/register").handler(this::register);
        router.post("/login").handler(this::login);
        router.get("/:id").handler(this::getUser);
        
        return router;
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

