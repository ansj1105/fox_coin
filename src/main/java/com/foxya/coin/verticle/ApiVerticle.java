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
import com.foxya.coin.user.UserExternalIdRepository;
import com.foxya.coin.device.DeviceRepository;
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
import com.foxya.coin.auth.SignupEmailCodeRepository;
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
import com.foxya.coin.notification.NotificationHandler;
import com.foxya.coin.notification.NotificationRepository;
import com.foxya.coin.notification.NotificationService;
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
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.retry.EmailRetryJob;
import com.foxya.coin.retry.RetryQueuePublisher;
import com.foxya.coin.common.DeviceGuard;
import com.foxya.coin.currency.CurrencyHandler;
import com.foxya.coin.currency.CurrencyService;
import com.foxya.coin.security.SecurityHandler;
import com.foxya.coin.inquiry.InquiryHandler;
import com.foxya.coin.inquiry.InquiryRepository;
import com.foxya.coin.inquiry.InquiryService;
import com.foxya.coin.mission.MissionHandler;
import com.foxya.coin.mission.MissionRepository;
import com.foxya.coin.mission.MissionService;
import com.foxya.coin.airdrop.AirdropHandler;
import com.foxya.coin.airdrop.AirdropRepository;
import com.foxya.coin.airdrop.AirdropService;
import com.foxya.coin.client.ClientHandler;
import com.foxya.coin.client.ClientRepository;
import com.foxya.coin.client.ClientService;
import com.foxya.coin.monitoring.MonitoringHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.JWTAuthHandler;
import com.foxya.coin.common.metrics.MetricsCollector;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisReplicas;
import io.vertx.core.json.JsonArray;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiVerticle extends AbstractVerticle {
    
    static {
        DatabindCodec.mapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    private MetricsCollector metricsCollector;
    
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting ApiVerticle...");
        
        // 메트릭 수집기 초기화
        metricsCollector = new MetricsCollector();
        
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
        NotificationRepository notificationRepository = new NotificationRepository();
        SocialLinkRepository socialLinkRepository = new SocialLinkRepository();
        PhoneVerificationRepository phoneVerificationRepository = new PhoneVerificationRepository();
        EmailVerificationRepository emailVerificationRepository = new EmailVerificationRepository();
        SignupEmailCodeRepository signupEmailCodeRepository = new SignupEmailCodeRepository();
        DeviceRepository deviceRepository = new DeviceRepository();
        SubscriptionRepository subscriptionRepository = new SubscriptionRepository();
        ReviewRepository reviewRepository = new ReviewRepository();
        AgencyRepository agencyRepository = new AgencyRepository();
        InquiryRepository inquiryRepository = new InquiryRepository();
        MissionRepository missionRepository = new MissionRepository();
        AirdropRepository airdropRepository = new AirdropRepository();
        ClientRepository clientRepository = new ClientRepository();
        UserExternalIdRepository userExternalIdRepository = new UserExternalIdRepository();
        
        // Redis 초기화 (토큰 블랙리스트·재시도 큐용)
        JsonObject redisConfig = config().getJsonObject("redis", new JsonObject());
        RedisAPI redisApi = initializeRedis(redisConfig);
        RetryQueuePublisher retryQueuePublisher = redisApi != null ? new RetryQueuePublisher(redisApi) : null;

        // 이메일 서비스 (SMTP 설정은 선택 사항, 실패 시 Redis 재시도 큐 적재)
        EmailService emailService = new EmailService(
            vertx,
            config().getJsonObject("smtp", new JsonObject()),
            frontendConfig,
            retryQueuePublisher
        );
        
        // UserService를 먼저 생성 (AuthService에서 사용)
        UserService userService = new UserService(
            pool, userRepository, jwtAuth, jwtConfig, frontendConfig, emailVerificationRepository, emailService, redisApi, userExternalIdRepository);
        
        // WebClient 초기화 (외부 API 호출용)
        WebClient webClient = WebClient.create(vertx);
        
        // TRON 서비스 URL 가져오기
        JsonObject blockchainConfig = config().getJsonObject("blockchain", new JsonObject());
        JsonObject tronConfig = blockchainConfig.getJsonObject("tron", new JsonObject());
        String tronServiceUrl = tronConfig.getString("serviceUrl", "");
        
        WalletService walletService = new WalletService(pool, walletRepository, currencyRepository, webClient, tronServiceUrl, redisApi);
        TransferService transferService = new TransferService(pool, transferRepository, userRepository, currencyRepository, null); // EventPublisher는 EventVerticle에서 주입
        ReferralService referralService = new ReferralService(pool, referralRepository, userRepository, emailVerificationRepository, transferService);
        BonusService bonusService = new BonusService(
            pool, bonusRepository, referralRepository, subscriptionRepository, reviewRepository,
            agencyRepository, socialLinkRepository, phoneVerificationRepository);
        LevelService levelService = new LevelService(
            pool, userRepository, miningRepository);
        MiningService miningService = new MiningService(
            pool, miningRepository, userRepository, bonusService, bonusRepository, walletRepository,
            referralService, transferRepository, currencyRepository, emailVerificationRepository, levelService);
        NoticeService noticeService = new NoticeService(
            pool, noticeRepository);
        NotificationService notificationService = new NotificationService(
            pool, notificationRepository);
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
        
        // CurrencyService 초기화 (다른 서비스에서 사용)
        CurrencyService currencyService = new CurrencyService(pool, currencyRepository, webClient);
        
        SwapRepository swapRepository = new SwapRepository();
        SwapService swapService = new SwapService(
            pool, swapRepository, currencyRepository, currencyService, transferRepository);
        ExchangeRepository exchangeRepository = new ExchangeRepository();
        ExchangeService exchangeService = new ExchangeService(
            pool, exchangeRepository, currencyRepository, transferRepository, userRepository);
        PaymentDepositRepository paymentDepositRepository = new PaymentDepositRepository();
        PaymentDepositService paymentDepositService = new PaymentDepositService(
            pool, paymentDepositRepository, currencyRepository, transferRepository);
        TokenDepositRepository tokenDepositRepository = new TokenDepositRepository();
        TokenDepositService tokenDepositService = new TokenDepositService(
            pool, tokenDepositRepository, currencyRepository, transferRepository);
        
        // Google OAuth 설정 (환경 변수 우선)
        JsonObject googleConfig = applyGoogleEnvOverrides(config().getJsonObject("google", new JsonObject()));
        
        // Service 초기화 (AuthService는 다른 서비스들 이후에 초기화)
        AuthService authService = new AuthService(
            pool, userRepository, userService, jwtAuth, jwtConfig, socialLinkRepository, phoneVerificationRepository,
            redisApi, walletRepository, transferRepository, bonusRepository, miningRepository, missionRepository,
            notificationRepository, subscriptionRepository, reviewRepository, agencyRepository,
            swapRepository, exchangeRepository, paymentDepositRepository,
            tokenDepositRepository, airdropRepository, inquiryRepository, emailVerificationRepository,
            signupEmailCodeRepository, deviceRepository, referralRepository, emailService, webClient, googleConfig);
        AuthUtils.configureDeviceGuard(new DeviceGuard(pool, deviceRepository, authService));
        InquiryService inquiryService = new InquiryService(
            pool, inquiryRepository, userService);
        MissionService missionService = new MissionService(
            pool, missionRepository);
        AirdropService airdropService = new AirdropService(
            pool, airdropRepository, currencyRepository, transferRepository, walletRepository);
        ClientService clientService = new ClientService(
            pool, clientRepository, userExternalIdRepository, userRepository, jwtAuth, redisApi);
        
        // 이메일 재시도 큐 소비 (Redis 있을 때만, 주기적으로 RPOP → 재시도 → 최종 실패 시 DLQ)
        if (redisApi != null && retryQueuePublisher != null) {
            startEmailRetryProcessor(emailService, retryQueuePublisher);
        }

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
        NotificationHandler notificationHandler = new NotificationHandler(vertx, notificationService, jwtAuth);
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
        InquiryHandler inquiryHandler = new InquiryHandler(vertx, inquiryService, jwtAuth);
        MissionHandler missionHandler = new MissionHandler(vertx, missionService, jwtAuth);
        AirdropHandler airdropHandler = new AirdropHandler(vertx, airdropService, jwtAuth);
        ClientHandler clientHandler = new ClientHandler(vertx, clientService, jwtAuth);
        
        // 모니터링 API 키 (환경 변수 또는 config에서 가져오기)
        String monitoringApiKey = System.getenv("MONITORING_API_KEY");
        if (monitoringApiKey == null || monitoringApiKey.isEmpty()) {
            monitoringApiKey = config().getString("monitoring.apiKey", "default-monitoring-key-change-in-production");
            log.info("Monitoring API key loaded from config.json: {}", monitoringApiKey);
        } else {
            log.info("Monitoring API key loaded from environment variable MONITORING_API_KEY");
        }
        MonitoringHandler monitoringHandler = new MonitoringHandler(vertx, monitoringApiKey);
        
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
        
        // 알림 API
        mainRouter.mountSubRouter("/api/v1/notifications", notificationHandler.getRouter());
        
        // 문의하기 API
        mainRouter.mountSubRouter("/api/v1/inquiries", inquiryHandler.getRouter());
        
        // 미션 API
        mainRouter.mountSubRouter("/api/v1/missions", missionHandler.getRouter());
        
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
        
        // 에어드랍 API
        mainRouter.mountSubRouter("/api/v1/airdrop", airdropHandler.getRouter());
        
        // 클라이언트 API (API Key 기반 토큰 발급 및 유저 데이터 수신)
        mainRouter.mountSubRouter("/api/v1/client", clientHandler.getRouter());
        
        // 보안 API (거래 비밀번호 등)
        mainRouter.mountSubRouter("/api/v1/security", securityHandler.getRouter());
        
        // 통화 API (환율 조회는 공개 API)
        mainRouter.mountSubRouter("/api/v1/currencies", currencyHandler.getRouter());
        
        // 모니터링 페이지 (role 2, 3만 접근 가능)
        mainRouter.mountSubRouter("/", monitoringHandler.getRouter());
        
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
        // CORS - 웹 사용 시 널널하게 (preflight 캐시, 노출 헤더 확대)
        router.route().handler(CorsHandler.create()
            .addRelativeOrigin(".*")
            .allowCredentials(true)
            .maxAgeSeconds(86400)
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
            .allowedHeader("X-Device-Id")
            .allowedHeader("X-Device-Type")
            .allowedHeader("X-Device-Os")
            .allowedHeader("X-App-Version")
            .allowedHeader("X-Platform")
            .allowedHeader("X-Client-Version")
            .allowedHeader("X-Client-Type")
            .allowedHeader("X-App-Build")
            .allowedHeader("User-Agent")
            .allowedHeader("Referer")
            .allowedHeader("X-Forwarded-For")
            .allowedHeader("X-Real-IP")
            .allowedHeader("X-Forwarded-Proto")
            .allowedHeader("Cache-Control")
            .allowedHeader("Pragma")
            .allowedHeader("If-Modified-Since")
            .allowedHeader("If-None-Match")
            .allowedHeader("Accept-Language")
            .allowedHeader("Accept-Encoding")
            .allowedHeader("Access-Control-Request-Method")
            .allowedHeader("Access-Control-Request-Headers")
            .exposedHeader("Content-Length")
            .exposedHeader("Content-Type")
            .exposedHeader("Authorization"));
        
        // Body Handler
        router.route().handler(BodyHandler.create());
        
        // Request 로깅 및 메트릭 수집 (/health, /metrics는 로그 생략해 다른 로그 확인 용이)
        router.route().handler(ctx -> {
            long startTime = System.currentTimeMillis();
            String method = ctx.request().method().toString();
            String path = ctx.request().path();
            boolean skipRequestLog = "/health".equals(path) || "/metrics".equals(path);
            if (!skipRequestLog) {
                log.info("[REQUEST] {} {} from {}", method, path, ctx.request().remoteAddress());
            }
            // 시작 시간을 컨텍스트에 저장 (failure handler에서 사용)
            ctx.put("startTime", startTime);
            
            // 응답 완료 시 메트릭 기록
            ctx.response().endHandler(v -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = ctx.response().getStatusCode();
                metricsCollector.recordRequest(method, path, statusCode, duration);
            });
            
            ctx.next();
        });
        
        // Failure Handler (에러 발생 시 메트릭 기록)
        router.route().failureHandler(ctx -> {
            Long startTime = ctx.get("startTime");
            if (startTime == null) {
                startTime = System.currentTimeMillis();
            }
            String method = ctx.request().method().toString();
            String path = ctx.request().path();
            int statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : 500;
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(method, path, statusCode, duration);
            ErrorHandler.handle(ctx);
        });
        
        // Health check
        router.get("/health").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(Json.encode(new JsonObject()
                    .put("status", "UP")
                    .put("timestamp", System.currentTimeMillis())));
        });
        
        // Prometheus metrics endpoint
        setupMetricsEndpoint(router);
        
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
    
    private void setupMetricsEndpoint(Router router) {
        // Prometheus 메트릭 엔드포인트
        router.get("/metrics").handler(ctx -> {
            try {
                String prometheusMetrics = metricsCollector.scrape();
                ctx.response()
                    .putHeader("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                    .end(prometheusMetrics);
            } catch (Exception e) {
                log.error("Failed to generate metrics", e);
                ctx.response()
                    .setStatusCode(500)
                    .end("Failed to generate metrics: " + e.getMessage());
            }
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
    
    /**
     * 이메일 재시도 큐 주기 소비: RPOP → 재시도 → 최종 실패 시 DLQ
     */
    private void startEmailRetryProcessor(EmailService emailService, RetryQueuePublisher retryQueuePublisher) {
        long intervalMs = 15_000;
        vertx.setPeriodic(intervalMs, id -> {
            retryQueuePublisher.popEmailRetry()
                .onSuccess(job -> {
                    if (job == null) return;
                    emailService.processRetryJob(job)
                        .onSuccess(v -> log.debug("Email retry succeeded. type={}, email={}", job.getType(), job.getEmail()))
                        .onFailure(throwable -> {
                            EmailRetryJob next = job.withIncrementedRetry();
                            if (next.getRetryCount() >= job.getMaxRetries()) {
                                retryQueuePublisher.enqueueEmailDlq(job, throwable.getMessage());
                            } else {
                                retryQueuePublisher.enqueueEmailRetry(next);
                            }
                        });
                });
        });
        log.info("Email retry processor started (interval {} ms)", intervalMs);
    }

    /**
     * Redis 초기화 (토큰 블랙리스트·재시도 큐용)
     * Redis가 없어도 서비스는 동작하도록 null을 반환할 수 있음
     */
    private RedisAPI initializeRedis(JsonObject redisConfig) {
        try {
            String mode = redisConfig.getString("mode", "standalone");
            RedisOptions options = createRedisOptions(redisConfig, mode);
            Redis redisClient = Redis.createClient(vertx, options);
            
            // 비동기 연결은 나중에 처리되므로, 연결 실패해도 서비스는 계속 동작
            redisClient.connect()
                .onSuccess(conn -> {
                    log.info("Redis connected successfully for token blacklist (mode: {})", mode);
                })
                .onFailure(throwable -> {
                    log.warn("Failed to connect to Redis for token blacklist. Logout will work but tokens won't be blacklisted: {}", throwable.getMessage());
                });
            
            // RedisAPI는 연결이 완료되기 전에도 생성 가능 (연결 실패 시 null 반환)
            return RedisAPI.api(redisClient);
        } catch (Exception e) {
            log.warn("Failed to initialize Redis for token blacklist: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Redis 모드에 따른 옵션 생성 (EventVerticle과 동일한 로직)
     */
    private RedisOptions createRedisOptions(JsonObject redisConfig, String mode) {
        RedisOptions options = new RedisOptions();
        
        String password = redisConfig.getString("password");
        if (password != null && !password.isEmpty()) {
            options.setPassword(password);
        }
        
        switch (mode) {
            case "cluster" -> {
                options.setType(RedisClientType.CLUSTER);
                options.setUseReplicas(RedisReplicas.SHARE);
                
                JsonArray nodes = redisConfig.getJsonArray("nodes", new JsonArray());
                if (nodes.isEmpty()) {
                    options.addConnectionString("redis://localhost:7001");
                    options.addConnectionString("redis://localhost:7002");
                    options.addConnectionString("redis://localhost:7003");
                    options.addConnectionString("redis://localhost:7004");
                    options.addConnectionString("redis://localhost:7005");
                    options.addConnectionString("redis://localhost:7006");
                } else {
                    for (int i = 0; i < nodes.size(); i++) {
                        options.addConnectionString(nodes.getString(i));
                    }
                }
                log.info("Redis Cluster mode configured with {} nodes", 
                    nodes.isEmpty() ? 6 : nodes.size());
            }
            
            case "sentinel" -> {
                options.setType(RedisClientType.SENTINEL);
                options.setMasterName(redisConfig.getString("masterName", "mymaster"));
                options.setRole(io.vertx.redis.client.RedisRole.MASTER);
                
                JsonArray sentinels = redisConfig.getJsonArray("sentinels", new JsonArray());
                if (sentinels.isEmpty()) {
                    options.addConnectionString("redis://localhost:26379");
                } else {
                    for (int i = 0; i < sentinels.size(); i++) {
                        options.addConnectionString(sentinels.getString(i));
                    }
                }
                log.info("Redis Sentinel mode configured with master: {}", 
                    redisConfig.getString("masterName", "mymaster"));
            }
            
            default -> {
                options.setType(RedisClientType.STANDALONE);
                String host = redisConfig.getString("host", "localhost");
                int port = redisConfig.getInteger("port", 6379);
                options.setConnectionString("redis://" + host + ":" + port);
                log.info("Redis Standalone mode configured: {}:{}", host, port);
            }
        }
        
        options.setMaxPoolSize(redisConfig.getInteger("maxPoolSize", 8));
        options.setMaxPoolWaiting(redisConfig.getInteger("maxPoolWaiting", 32));
        options.setPoolRecycleTimeout(redisConfig.getInteger("poolRecycleTimeout", 15000));
        
        return options;
    }

    private static JsonObject applyGoogleEnvOverrides(JsonObject baseConfig) {
        JsonObject config = baseConfig.copy();
        putIfEnvSet(config, "clientId", "GOOGLE_CLIENT_ID");
        putIfEnvSet(config, "clientSecret", "GOOGLE_CLIENT_SECRET");
        putIfEnvSet(config, "redirectUri", "GOOGLE_REDIRECT_URI");
        putIfEnvSet(config, "androidClientId", "GOOGLE_ANDROID_CLIENT_ID");
        putIfEnvSet(config, "androidRedirectUri", "GOOGLE_ANDROID_REDIRECT_URI");
        return config;
    }

    private static void putIfEnvSet(JsonObject config, String key, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}
