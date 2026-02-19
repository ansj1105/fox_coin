package com.foxya.coin.app;

import com.foxya.coin.common.BaseHandler;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * coin_publish 등 내부 서비스용 설정 조회 API.
 * GET /api/v1/internal/config?keys=a,b,c
 */
@Slf4j
public class InternalConfigHandler extends BaseHandler {

    private static final String HEADER_API_KEY = "X-Internal-Api-Key";

    private final PgPool pool;
    private final AppConfigRepository appConfigRepository;
    private final String internalApiKey;

    public InternalConfigHandler(Vertx vertx, PgPool pool, AppConfigRepository appConfigRepository, String internalApiKey) {
        super(vertx);
        this.pool = pool;
        this.appConfigRepository = appConfigRepository;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.route().handler(this::checkInternalApiKey);
        router.get("/").handler(this::getConfigs);
        return router;
    }

    private void checkInternalApiKey(RoutingContext ctx) {
        if (internalApiKey == null || internalApiKey.isEmpty()) {
            log.warn("Internal config API key not configured");
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Internal API not configured\"}");
            return;
        }
        String key = ctx.request().getHeader(HEADER_API_KEY);
        if (!internalApiKey.equals(key)) {
            log.warn("Invalid or missing internal API key");
            ctx.response().setStatusCode(401).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end("{\"error\":\"Unauthorized\"}");
            return;
        }
        ctx.next();
    }

    private void getConfigs(RoutingContext ctx) {
        String keysParam = ctx.request().getParam("keys");
        if (keysParam == null || keysParam.isBlank()) {
            ctx.fail(400, new IllegalArgumentException("keys required"));
            return;
        }
        String[] parts = keysParam.split(",");
        Set<String> keySet = new LinkedHashSet<>();
        for (String raw : parts) {
            if (raw == null) continue;
            String key = raw.trim();
            if (!key.isBlank()) {
                keySet.add(key);
            }
        }
        if (keySet.isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("keys required"));
            return;
        }
        List<String> keys = new ArrayList<>(keySet);
        List<Future> futures = new ArrayList<>();
        for (String key : keys) {
            futures.add(appConfigRepository.getByKey(pool, key));
        }
        Future<JsonObject> future = CompositeFuture.all(futures)
            .map(res -> {
                JsonObject data = new JsonObject();
                for (int i = 0; i < keys.size(); i++) {
                    String value = (String) res.resultAt(i);
                    data.put(keys.get(i), value);
                }
                return data;
            });
        response(ctx, future);
    }
}
