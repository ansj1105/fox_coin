package com.foxya.coin.app;

import com.foxya.coin.common.BaseHandler;
import com.foxya.coin.common.cache.RedisJsonCache;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

/**
 * 앱 공통 공개 API (인증 불필요).
 * - GET /config: 앱 최소 필요 버전 등. DB app_config에서 조회, 없으면 fallback(env) 사용.
 */
@Slf4j
public class AppHandler extends BaseHandler {

    private final PgPool pool;
    private final AppConfigRepository appConfigRepository;
    private final String fallbackMinAppVersion;
    private final RedisJsonCache cache;
    private static final int APP_CONFIG_CACHE_TTL_SECONDS = 60;

    public AppHandler(Vertx vertx, PgPool pool, AppConfigRepository appConfigRepository, String fallbackMinAppVersion) {
        this(vertx, pool, appConfigRepository, fallbackMinAppVersion, null);
    }

    public AppHandler(Vertx vertx, PgPool pool, AppConfigRepository appConfigRepository, String fallbackMinAppVersion, RedisAPI redisApi) {
        super(vertx);
        this.pool = pool;
        this.appConfigRepository = appConfigRepository;
        this.fallbackMinAppVersion = fallbackMinAppVersion;
        this.cache = new RedisJsonCache(redisApi, "foxya:cache:app");
    }

    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        router.get("/config").handler(this::getConfig);
        return router;
    }

    private void getConfig(RoutingContext ctx) {
        // X-Device-Os: IOS / ANDROID / WEB 또는 query platform=ios 등. 없으면 Android(config_value) 기준.
        String deviceOs = ctx.request().getHeader("X-Device-Os");
        if (deviceOs == null || deviceOs.isBlank()) {
            String platform = ctx.request().getParam("platform");
            if (platform != null && !platform.isBlank()) {
                deviceOs = "ios".equalsIgnoreCase(platform.trim()) ? "IOS" : platform.trim().toUpperCase();
            }
        }
        boolean isIos = deviceOs != null && "IOS".equalsIgnoreCase(deviceOs.trim());
        boolean finalIsIos = isIos;
        Future<JsonObject> future = cache.getOrLoad(
            "config:" + (finalIsIos ? "ios" : "default"),
            APP_CONFIG_CACHE_TTL_SECONDS,
            JsonObject.class,
            () -> appConfigRepository.getMinAppVersion(pool, finalIsIos)
                .map(dbMin -> {
                    String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : fallbackMinAppVersion;
                    JsonObject data = new JsonObject();
                    if (min != null && !min.isBlank()) {
                        data.put("minAppVersion", min);
                    }
                    return data;
                })
        );
        response(ctx, future);
    }
}
