package com.foxya.coin.admin;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.utils.AuthUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OfflinePayAdminProxyHandler extends BaseHandler {

    private final WebClient webClient;
    private final JWTAuth jwtAuth;
    private final String offlinePayBaseUrl;

    public OfflinePayAdminProxyHandler(Vertx vertx, WebClient webClient, JWTAuth jwtAuth, String offlinePayBaseUrl) {
        super(vertx);
        this.webClient = webClient;
        this.jwtAuth = jwtAuth;
        this.offlinePayBaseUrl = offlinePayBaseUrl == null ? "" : offlinePayBaseUrl.trim();
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());

        router.route().handler(JWTAuthHandler.create(jwtAuth));
        router.route().handler(AuthUtils.hasRole(UserRole.ADMIN, UserRole.SUPER_ADMIN));
        router.route().handler(BodyHandler.create());

        registerGet(router, "/metrics/offline-pay/overview", "/api/admin/metrics/offline-pay/overview");

        registerGet(router, "/ops/dead-letters", "/api/admin/ops/dead-letters");
        registerPost(router, "/ops/dead-letters/:batchId/retry", "/api/admin/ops/dead-letters/{batchId}/retry");
        registerGet(router, "/ops/collateral-operations", "/api/admin/ops/collateral-operations");
        registerGet(router, "/ops/collateral-operations/overview", "/api/admin/ops/collateral-operations/overview");
        registerGet(router, "/ops/offline-events", "/api/admin/ops/offline-events");
        registerGet(router, "/ops/offline-events/overview", "/api/admin/ops/offline-events/overview");

        registerGet(router, "/proofs", "/api/admin/proofs");
        registerGet(router, "/proofs/overview", "/api/admin/proofs/overview");

        registerGet(router, "/devices", "/api/admin/devices");
        registerGet(router, "/devices/overview", "/api/admin/devices/overview");

        registerGet(router, "/anomalies/conflicts", "/api/admin/anomalies/conflicts");
        registerGet(router, "/anomalies/reconciliation-cases", "/api/admin/anomalies/reconciliation-cases");
        registerPost(router, "/anomalies/reconciliation-cases/:caseId/retry", "/api/admin/anomalies/reconciliation-cases/{caseId}/retry");
        registerGet(router, "/anomalies/workflow-states", "/api/admin/anomalies/workflow-states");
        registerGet(router, "/anomalies/sagas", "/api/admin/anomalies/sagas");
        registerGet(router, "/anomalies/overview", "/api/admin/anomalies/overview");

        registerGet(router, "/sync/outbox", "/api/admin/sync/outbox");
        registerPost(router, "/sync/outbox/:eventId/retry", "/api/admin/sync/outbox/{eventId}/retry");

        registerPost(router, "/collateral/operations/:operationId/retry", "/api/admin/collateral/operations/{operationId}/retry");

        return router;
    }

    private void registerGet(Router router, String routePath, String upstreamTemplate) {
        router.get(routePath).handler(ctx -> proxy(ctx, "GET", upstreamTemplate));
    }

    private void registerPost(Router router, String routePath, String upstreamTemplate) {
        router.post(routePath).handler(ctx -> proxy(ctx, "POST", upstreamTemplate));
    }

    private void proxy(RoutingContext ctx, String method, String upstreamTemplate) {
        if (offlinePayBaseUrl.isBlank()) {
            ctx.response()
                    .setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("{\"status\":\"ERROR\",\"message\":\"offline_pay base url is not configured\"}");
            return;
        }

        String upstreamPath = resolvePath(upstreamTemplate, ctx.pathParams());
        String upstreamUrl = offlinePayBaseUrl + appendQueryString(upstreamPath, ctx.request().query());
        HttpRequest<Buffer> request = switch (method) {
            case "POST" -> webClient.postAbs(upstreamUrl);
            default -> webClient.getAbs(upstreamUrl);
        };

        request.putHeader(HttpHeaders.ACCEPT.toString(), "application/json");

        if ("POST".equals(method)) {
            Buffer body = ctx.body() == null ? Buffer.buffer() : ctx.body().buffer();
            request.sendBuffer(body)
                    .onSuccess(response -> relayJsonResponse(ctx, response))
                    .onFailure(ctx::fail);
            return;
        }

        request.send()
                .onSuccess(response -> relayJsonResponse(ctx, response))
                .onFailure(ctx::fail);
    }

    private String resolvePath(String template, Map<String, String> pathParams) {
        String resolved = template;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            resolved = resolved.replace(
                    "{" + entry.getKey() + "}",
                    java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
            );
        }
        return resolved;
    }

    private String appendQueryString(String path, String query) {
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + query;
    }

    private void relayJsonResponse(RoutingContext ctx, HttpResponse<Buffer> response) {
        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE.toString());
        ctx.response()
                .setStatusCode(response.statusCode())
                .putHeader(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/json")
                .end(response.body());
    }
}
