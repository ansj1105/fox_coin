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
import com.foxya.coin.agency.AgencyHandler;
import com.foxya.coin.agency.AgencyRepository;
import com.foxya.coin.agency.AgencyService;
import com.foxya.coin.auth.AuthHandler;
import com.foxya.coin.auth.AuthService;
import com.foxya.coin.auth.PhoneVerificationRepository;
import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.auth.SocialLinkRepository;
import com.foxya.coin.banner.BannerHandler;
import com.foxya.coin.banner.BannerRepository;
import com.foxya.coin.banner.BannerService;
import com.foxya.coin.bonus.BonusHandler;
import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.bonus.BonusService;
import com.foxya.coin.deposit.TokenDepositHandler;
import com.foxya.coin.deposit.TokenDepositRepository;
import com.foxya.coin.deposit.TokenDepositService;
import com.foxya.coin.exchange.ExchangeHandler;
import com.foxya.coin.exchange.ExchangeRepository;
import com.foxya.coin.exchange.ExchangeService;
import com.foxya.coin.level.LevelHandler;
import com.foxya.coin.level.LevelService;
import com.foxya.coin.mining.MiningHandler;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.mining.MiningService;
import com.foxya.coin.notice.NoticeHandler;
import com.foxya.coin.notice.NoticeRepository;
import com.foxya.coin.notice.NoticeService;
import com.foxya.coin.payment.PaymentDepositHandler;
import com.foxya.coin.payment.PaymentDepositRepository;
import com.foxya.coin.payment.PaymentDepositService;
import com.foxya.coin.ranking.RankingHandler;
import com.foxya.coin.ranking.RankingRepository;
import com.foxya.coin.ranking.RankingService;
import com.foxya.coin.review.ReviewHandler;
import com.foxya.coin.review.ReviewRepository;
import com.foxya.coin.review.ReviewService;
import com.foxya.coin.subscription.SubscriptionHandler;
import com.foxya.coin.subscription.SubscriptionRepository;
import com.foxya.coin.subscription.SubscriptionService;
import com.foxya.coin.swap.SwapHandler;
import com.foxya.coin.swap.SwapRepository;
import com.foxya.coin.swap.SwapService;
import com.foxya.coin.common.utils.EmailService;
import com.foxya.coin.currency.CurrencyHandler;
import com.foxya.coin.currency.CurrencyService;
import com.foxya.coin.security.SecurityHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.JWTAuthHandler;
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
        JsonObject frontendConfig = config().getJsonObject("frontend", new JsonObject());
        
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
        BonusRepository bonusRepository = new BonusRepository();
        MiningRepository miningRepository = new MiningRepository();
        NoticeRepository noticeRepository = new NoticeRepository();
        SocialLinkRepository socialLinkRepository = new SocialLinkRepository();
        PhoneVerificationRepository phoneVerificationRepository = new PhoneVerificationRepository();
        EmailVerificationRepository emailVerificationRepository = new EmailVerificationRepository();
        SubscriptionRepository subscriptionRepository = new SubscriptionRepository();
        ReviewRepository reviewRepository = new ReviewRepository();
        AgencyRepository agencyRepository = new AgencyRepository();
        
        // Service 초기화
        AuthService authService = new AuthService(
            pool, userRepository, jwtAuth, jwtConfig, socialLinkRepository, phoneVerificationRepository);
        // 이메일 서비스 (SMTP 설정은 선택 사항)
        EmailService emailService = new EmailService(config().getJsonObject("smtp", new JsonObject()));

        UserService userService = new UserService(
            pool, userRepository, jwtAuth, jwtConfig, frontendConfig, emailVerificationRepository, emailService);
        WalletService walletService = new WalletService(pool, walletRepository);
        ReferralService referralService = new ReferralService(pool, referralRepository, userRepository);
        TransferService transferService = new TransferService(pool, transferRepository, userRepository, currencyRepository, null); // EventPublisher는 EventVerticle에서 주입
        BonusService bonusService = new BonusService(
            pool, bonusRepository, referralRepository, subscriptionRepository, reviewRepository, 
            agencyRepository, socialLinkRepository, phoneVerificationRepository);
        MiningService miningService = new MiningService(
            pool, miningRepository, userRepository);
        LevelService levelService = new LevelService(
            pool, userRepository, miningRepository);
        NoticeService noticeService = new NoticeService(
            pool, noticeRepository);
        SubscriptionService subscriptionService = new SubscriptionService(
            pool, subscriptionRepository);
        ReviewService reviewService = new ReviewService(
            pool, reviewRepository);
        AgencyService agencyService = new AgencyService(
            pool, agencyRepository);
        RankingRepository rankingRepository = new RankingRepository();
        RankingService rankingService = new RankingService(
            pool, rankingRepository);
        BannerRepository bannerRepository = new BannerRepository();
        BannerService bannerService = new BannerService(
            pool, bannerRepository);
        
        // WebClient 초기화 (외부 API 호출용)
        WebClient webClient = WebClient.create(vertx);
        
        // CurrencyService 초기화 (다른 서비스에서 사용)
        CurrencyService currencyService = new CurrencyService(pool, currencyRepository, webClient);
        
        SwapRepository swapRepository = new SwapRepository();
        SwapService swapService = new SwapService(
            pool, swapRepository, currencyRepository, currencyService, transferRepository);
        ExchangeRepository exchangeRepository = new ExchangeRepository();
        ExchangeService exchangeService = new ExchangeService(
            pool, exchangeRepository, currencyRepository, transferRepository);
        PaymentDepositRepository paymentDepositRepository = new PaymentDepositRepository();
        PaymentDepositService paymentDepositService = new PaymentDepositService(
            pool, paymentDepositRepository, currencyRepository, transferRepository);
        TokenDepositRepository tokenDepositRepository = new TokenDepositRepository();
        TokenDepositService tokenDepositService = new TokenDepositService(
            pool, tokenDepositRepository, currencyRepository, transferRepository);
        
        // Handler 초기화
        AuthHandler authHandler = new AuthHandler(vertx, authService, jwtAuth);
        UserHandler userHandler = new UserHandler(vertx, userService, jwtAuth);
        WalletHandler walletHandler = new WalletHandler(vertx, walletService);
        ReferralHandler referralHandler = new ReferralHandler(vertx, referralService, jwtAuth);
        TransferHandler transferHandler = new TransferHandler(vertx, transferService, jwtAuth);
        BonusHandler bonusHandler = new BonusHandler(vertx, bonusService, jwtAuth);
        MiningHandler miningHandler = new MiningHandler(vertx, miningService, jwtAuth);
        LevelHandler levelHandler = new LevelHandler(vertx, levelService, jwtAuth);
        NoticeHandler noticeHandler = new NoticeHandler(vertx, noticeService, jwtAuth);
        SubscriptionHandler subscriptionHandler = new SubscriptionHandler(vertx, subscriptionService, jwtAuth);
        ReviewHandler reviewHandler = new ReviewHandler(vertx, reviewService, jwtAuth);
        AgencyHandler agencyHandler = new AgencyHandler(vertx, agencyService, jwtAuth);
        RankingHandler rankingHandler = new RankingHandler(vertx, rankingService, jwtAuth);
        BannerHandler bannerHandler = new BannerHandler(vertx, bannerService, jwtAuth);
        SwapHandler swapHandler = new SwapHandler(vertx, swapService, jwtAuth);
        ExchangeHandler exchangeHandler = new ExchangeHandler(vertx, exchangeService, jwtAuth);
        PaymentDepositHandler paymentDepositHandler = new PaymentDepositHandler(vertx, paymentDepositService, jwtAuth);
        TokenDepositHandler tokenDepositHandler = new TokenDepositHandler(vertx, tokenDepositService, jwtAuth);
        CurrencyHandler currencyHandler = new CurrencyHandler(vertx, currencyService, jwtAuth);
        SecurityHandler securityHandler = new SecurityHandler(vertx, userService, jwtAuth);
        
        // Router 생성
        Router mainRouter = Router.router(vertx);
        
        // 전역 핸들러
        setupGlobalHandlers(mainRouter);
        
        // 공개 API (인증 불필요)
        mainRouter.mountSubRouter("/api/v1/auth", authHandler.getRouter());
        
        // 레벨 API를 먼저 등록 (구체적인 경로 우선)
        mainRouter.mountSubRouter("/api/v1/levels", levelHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/user", levelHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/users", levelHandler.getRouter());
        
        // 사용자 API (레벨 API 이후에 등록하여 /:id가 나중에 매칭되도록)
        mainRouter.mountSubRouter("/api/v1/users", userHandler.getRouter());
        // /api/v1/user도 지원 (단수형 경로)
        mainRouter.mountSubRouter("/api/v1/user", userHandler.getRouter());
        
        // 레퍼럴 API (인증 필요, 핸들러 내부에서 JWT 처리)
        mainRouter.mountSubRouter("/api/v1/referrals", referralHandler.getRouter());
        
        // 전송 API (인증 필요, 핸들러 내부에서 JWT 처리)
        mainRouter.mountSubRouter("/api/v1/transfers", transferHandler.getRouter());
        
        // 보너스 API
        mainRouter.mountSubRouter("/api/v1/bonus", bonusHandler.getRouter());
        
        // 채굴 API
        mainRouter.mountSubRouter("/api/v1/mining", miningHandler.getRouter());
        
        // 공지사항 API
        mainRouter.mountSubRouter("/api/v1/notices", noticeHandler.getRouter());
        
        // 구독 API
        mainRouter.mountSubRouter("/api/v1/subscription", subscriptionHandler.getRouter());
        
        // 리뷰 API
        mainRouter.mountSubRouter("/api/v1/review", reviewHandler.getRouter());
        
        // 에이전시 API
        mainRouter.mountSubRouter("/api/v1/agency", agencyHandler.getRouter());
        
        // 랭킹 API
        mainRouter.mountSubRouter("/api/v1/ranking", rankingHandler.getRouter());
        
        // 배너 API
        mainRouter.mountSubRouter("/api/v1/banners", bannerHandler.getRouter());
        
        // 스왑 API
        mainRouter.mountSubRouter("/api/v1/swap", swapHandler.getRouter());
        
        // 환전 API
        mainRouter.mountSubRouter("/api/v1/exchange", exchangeHandler.getRouter());
        
        // 결제 입금 API
        mainRouter.mountSubRouter("/api/v1/payment", paymentDepositHandler.getRouter());
        
        // 토큰 입금 API
        mainRouter.mountSubRouter("/api/v1/deposits", tokenDepositHandler.getRouter());
        
        // 보안 API (거래 비밀번호 등)
        mainRouter.mountSubRouter("/api/v1/security", securityHandler.getRouter());
        
        // 통화 API (환율 조회는 공개 API)
        mainRouter.mountSubRouter("/api/v1/currencies", currencyHandler.getRouter());
        
        // JWT 인증이 필요한 API
        Router protectedRouter = Router.router(vertx);
        protectedRouter.route().handler(JWTAuthHandler.create(jwtAuth));
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
            .addRelativeOrigin(".*")
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.DELETE)
            .allowedMethod(HttpMethod.PATCH)
            .allowedMethod(HttpMethod.OPTIONS)
            .allowedHeader("Content-Type")
            .allowedHeader("Authorization")
            .allowedHeader("Accept")
            .allowedHeader("Origin")
            .allowedHeader("X-Requested-With")
            .exposedHeader("Content-Length")
            .exposedHeader("Content-Type"));
        
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

