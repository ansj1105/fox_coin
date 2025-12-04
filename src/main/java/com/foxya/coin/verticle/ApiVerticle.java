package com.foxya.coin.verticle;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import com.foxya.coin.common.utils.ErrorHandler;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.referral.ReferralHandler;
import com.foxya.coin.referral.ReferralRepository;
import com.foxya.coin.referral.ReferralService;
import com.foxya.coin.transfer.TransferHandler;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.TransferService;
import com.foxya.coin.user.UserHandler;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.UserService;
import com.foxya.coin.wallet.WalletHandler;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.WalletService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiVerticle extends AbstractVerticle {
    
    static {
        DatabindCodec.mapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting ApiVerticle...");
        
        JsonObject httpConfig = config().getJsonObject("http", new JsonObject());
        JsonObject databaseConfig = config().getJsonObject("database", new JsonObject());
        JsonObject jwtConfig = config().getJsonObject("jwt", new JsonObject());
        
        int port = httpConfig.getInteger("port", 8080);
        
        // PostgreSQL 연결 풀
        PgPool pool = createPgPool(databaseConfig);
        
        // JWT 인증
        JWTAuth jwtAuth = createJwtAuth(jwtConfig);
        
        // Repository 초기화
        UserRepository userRepository = new UserRepository();
        WalletRepository walletRepository = new WalletRepository();
        CurrencyRepository currencyRepository = new CurrencyRepository();
        ReferralRepository referralRepository = new ReferralRepository();
        TransferRepository transferRepository = new TransferRepository();
        
        // Service 초기화
        com.foxya.coin.auth.AuthService authService = new com.foxya.coin.auth.AuthService(pool, userRepository, jwtAuth, jwtConfig);
        UserService userService = new UserService(pool, userRepository, jwtAuth, jwtConfig);
        WalletService walletService = new WalletService(pool, walletRepository);
        ReferralService referralService = new ReferralService(pool, referralRepository, userRepository);
        TransferService transferService = new TransferService(pool, transferRepository, userRepository, currencyRepository, null); // EventPublisher는 EventVerticle에서 주입
        
        // Handler 초기화
        com.foxya.coin.auth.AuthHandler authHandler = new com.foxya.coin.auth.AuthHandler(vertx, authService, jwtAuth);
        UserHandler userHandler = new UserHandler(vertx, userService, jwtAuth);
        WalletHandler walletHandler = new WalletHandler(vertx, walletService);
        ReferralHandler referralHandler = new ReferralHandler(vertx, referralService, jwtAuth);
        TransferHandler transferHandler = new TransferHandler(vertx, transferService, jwtAuth);
        
        // Router 생성
        Router mainRouter = Router.router(vertx);
        
        // 전역 핸들러
        setupGlobalHandlers(mainRouter);
        
        // 공개 API (인증 불필요)
        mainRouter.mountSubRouter("/api/v1/auth", authHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/users", userHandler.getRouter());
        
        // 레퍼럴 API (인증 필요, 핸들러 내부에서 JWT 처리)
        mainRouter.mountSubRouter("/api/v1/referrals", referralHandler.getRouter());
        
        // 전송 API (인증 필요, 핸들러 내부에서 JWT 처리)
        mainRouter.mountSubRouter("/api/v1/transfers", transferHandler.getRouter());
        
        // JWT 인증이 필요한 API
        Router protectedRouter = Router.router(vertx);
        protectedRouter.route().handler(io.vertx.ext.web.handler.JWTAuthHandler.create(jwtAuth));
        protectedRouter.mountSubRouter("/api/v1/wallets", walletHandler.getRouter());
        
        mainRouter.mountSubRouter("/", protectedRouter);
        
        // HTTP 서버 시작
        HttpServerOptions serverOptions = new HttpServerOptions().setCompressionSupported(true);
        
        vertx.createHttpServer(serverOptions)
            .requestHandler(mainRouter)
            .listen(port, http -> {
                if (http.succeeded()) {
                    log.info("HTTP API server started on port {}", port);
                    startPromise.complete();
                } else {
                    log.error("Failed to start HTTP server", http.cause());
                    startPromise.fail(http.cause());
                }
            });
    }
    
    private PgPool createPgPool(JsonObject config) {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost(config.getString("host"))
            .setPort(config.getInteger("port"))
            .setDatabase(config.getString("database"))
            .setUser(config.getString("user"))
            .setPassword(config.getString("password"));
        
        PoolOptions poolOptions = new PoolOptions()
            .setMaxSize(config.getInteger("pool_size", 10))
            .setIdleTimeout(config.getInteger("idle_timeout", 60))
            .setPoolCleanerPeriod(config.getInteger("pool_cleaner_period", 60));
        
        return PgPool.pool(vertx, connectOptions, poolOptions);
    }
    
    private JWTAuth createJwtAuth(JsonObject config) {
        String secret = config.getString("secret");
        
        return JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer(secret)));
    }
    
    private void setupGlobalHandlers(Router router) {
        // CORS
        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.DELETE)
            .allowedMethod(HttpMethod.OPTIONS)
            .allowCredentials(true)
            .allowedHeader("Content-Type")
            .allowedHeader("Authorization"));
        
        // Body Handler
        router.route().handler(BodyHandler.create());
        
        // Request 로깅
        router.route().handler(ctx -> {
            log.info("[REQUEST] {} {} from {}", 
                ctx.request().method(), ctx.request().path(), ctx.request().remoteAddress());
            ctx.next();
        });
        
        // Failure Handler
        router.route().failureHandler(ErrorHandler::handle);
        
        // Health check
        router.get("/health").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(Json.encode(new JsonObject()
                    .put("status", "UP")
                    .put("timestamp", System.currentTimeMillis())));
        });
        
        // Swagger UI
        router.get("/api-docs").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(getSwaggerHtml());
        });
        
        // OpenAPI Spec
        router.get("/openapi.yaml").handler(ctx -> {
            // Docker 환경: openapi.yaml, 개발 환경: src/main/resources/openapi.yaml
            vertx.fileSystem().readFile("openapi.yaml")
                .recover(err -> vertx.fileSystem().readFile("src/main/resources/openapi.yaml"))
                .onSuccess(buffer -> {
                    ctx.response()
                        .putHeader("Content-Type", "application/x-yaml; charset=utf-8")
                        .end(buffer);
                })
                .onFailure(err -> {
                    log.error("Failed to load openapi.yaml", err);
                    ctx.response()
                        .setStatusCode(404)
                        .end("OpenAPI spec not found");
                });
        });
    }
    
    private String getSwaggerHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Foxya Coin Service API Documentation</title>
                <link rel="stylesheet" type="text/css" href="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui.css">
                <style>
                    html { box-sizing: border-box; overflow: -moz-scrollbars-vertical; overflow-y: scroll; }
                    *, *:before, *:after { box-sizing: inherit; }
                    body { margin:0; padding:0; }
                </style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui-bundle.js"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5.10.0/swagger-ui-standalone-preset.js"></script>
                <script>
                window.onload = function() {
                    window.ui = SwaggerUIBundle({
                        url: "/openapi.yaml",
                        dom_id: '#swagger-ui',
                        deepLinking: true,
                        presets: [
                            SwaggerUIBundle.presets.apis,
                            SwaggerUIStandalonePreset
                        ],
                        plugins: [
                            SwaggerUIBundle.plugins.DownloadUrl
                        ],
                        layout: "StandaloneLayout"
                    });
                };
                </script>
            </body>
            </html>
            """;
    }
}

