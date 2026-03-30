package com.foxya.coin.security;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.user.UserService;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class OfflinePayPublicShareHandler extends BaseHandler {

    private final UserService userService;

    public OfflinePayPublicShareHandler(Vertx vertx, UserService userService) {
        super(vertx);
        this.userService = userService;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.get("/shared-details/:token").handler(this::getSharedDetail);
        return router;
    }

    private void getSharedDetail(RoutingContext ctx) {
        String token = ctx.pathParam("token");
        response(ctx, userService.getOfflinePaySharedDetail(token));
    }
}
