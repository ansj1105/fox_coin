package com.foxya.coin.wallet;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.ForbiddenException;
import com.foxya.coin.common.utils.AuthUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public class OfflinePayCollateralProxyHandler extends BaseHandler {

    private final WebClient webClient;
    private final String offlinePayBaseUrl;

    public OfflinePayCollateralProxyHandler(Vertx vertx, WebClient webClient, String offlinePayBaseUrl) {
        super(vertx);
        this.webClient = webClient;
        this.offlinePayBaseUrl = offlinePayBaseUrl == null ? "" : offlinePayBaseUrl.trim();
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.get("/operations")
                .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
                .handler(this::getOperations);
        return router;
    }

    private void getOperations(RoutingContext ctx) {
        if (offlinePayBaseUrl.isBlank()) {
            ctx.response()
                    .setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("{\"status\":\"ERROR\",\"message\":\"offline_pay base url is not configured\"}");
            return;
        }

        Long authenticatedUserId = AuthUtils.getUserIdOf(ctx.user());
        long requestedUserId = parseUserId(ctx.request().getParam("userId"));
        if (authenticatedUserId == null || requestedUserId <= 0 || !authenticatedUserId.equals(requestedUserId)) {
            throw new ForbiddenException("본인 데이터만 조회할 수 있습니다.");
        }

        String assetCode = ctx.request().getParam("assetCode");
        String size = ctx.request().getParam("size");

        webClient.getAbs(offlinePayBaseUrl + buildOperationsPath(requestedUserId, assetCode, size))
                .send()
                .onSuccess(response -> relayJsonResponse(ctx, response))
                .onFailure(ctx::fail);
    }

    private String buildOperationsPath(long userId, String assetCode, String size) {
        StringBuilder path = new StringBuilder("/api/collateral/operations?userId=").append(userId);
        if (assetCode != null && !assetCode.isBlank()) {
            path.append("&assetCode=").append(urlEncode(assetCode));
        }
        if (size != null && !size.isBlank()) {
            path.append("&size=").append(urlEncode(size));
        }
        return path.toString();
    }

    private void relayJsonResponse(RoutingContext ctx, HttpResponse<io.vertx.core.buffer.Buffer> response) {
        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE.toString());
        ctx.response()
                .setStatusCode(response.statusCode())
                .putHeader(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/json")
                .end(response.body());
    }

    private long parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(rawUserId.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
