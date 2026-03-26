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
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OfflinePaySnapshotProxyHandler extends BaseHandler {

    private final WebClient webClient;
    private final String offlinePayBaseUrl;

    public OfflinePaySnapshotProxyHandler(Vertx vertx, WebClient webClient, String offlinePayBaseUrl) {
        super(vertx);
        this.webClient = webClient;
        this.offlinePayBaseUrl = offlinePayBaseUrl == null ? "" : offlinePayBaseUrl.trim();
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());

        router.get("/current")
                .handler(AuthUtils.hasRole(UserRole.USER, UserRole.ADMIN))
                .handler(this::getCurrentSnapshot);

        return router;
    }

    private void getCurrentSnapshot(RoutingContext ctx) {
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

        String deviceId = ctx.request().getParam("deviceId");
        String assetCode = ctx.request().getParam("assetCode");

        webClient.getAbs(offlinePayBaseUrl + buildCurrentSnapshotPath(requestedUserId, deviceId, assetCode))
                .send()
                .onSuccess(response -> relayJsonResponse(ctx, response))
                .onFailure(ctx::fail);
    }

    private String buildCurrentSnapshotPath(long userId, String deviceId, String assetCode) {
        StringBuilder path = new StringBuilder("/api/snapshots/current?userId=").append(userId);
        if (deviceId != null && !deviceId.isBlank()) {
            path.append("&deviceId=").append(urlEncode(deviceId));
        }
        if (assetCode != null && !assetCode.isBlank()) {
            path.append("&assetCode=").append(urlEncode(assetCode));
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
