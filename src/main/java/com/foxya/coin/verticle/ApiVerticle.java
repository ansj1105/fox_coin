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
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import com.foxya.coin.common.utils.ErrorHandler;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.referral.ReferralHandler;
import com.foxya.coin.referral.ReferralRepository;
import com.foxya.coin.referral.ReferralRevenueTierRepository;
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
import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.app.AppHandler;
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
import com.foxya.coin.deposit.InternalDepositHandler;
import com.foxya.coin.deposit.TokenDepositHandler;
import com.foxya.coin.transfer.InternalWithdrawalHandler;
import com.foxya.coin.deposit.TokenDepositRepository;
import com.foxya.coin.deposit.TokenDepositService;
import com.foxya.coin.event.EventPublisher;
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
import com.foxya.coin.notification.FcmService;
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
import com.foxya.coin.currency.ExchangeRateRepository;
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
        
        // 硫뷀듃由??섏쭛湲?珥덇린??
        metricsCollector = new MetricsCollector();
        
        JsonObject httpConfig = config().getJsonObject("http", new JsonObject());
        JsonObject databaseConfig = config().getJsonObject("database", new JsonObject());
        JsonObject jwtConfig = config().getJsonObject("jwt", new JsonObject());
        JsonObject frontendConfig = config().getJsonObject("frontend", new JsonObject());
        
        int port = httpConfig.getInteger("port", 8080);
        
        // PostgreSQL ?곌껐 ?
        PgPool pool = createPgPool(databaseConfig);
        
        // JWT ?몄쬆
        JWTAuth jwtAuth = createJwtAuth(jwtConfig);
        
        // Repository 珥덇린??
        UserRepository userRepository = new UserRepository();
        WalletRepository walletRepository = new WalletRepository();
        CurrencyRepository currencyRepository = new CurrencyRepository();
        ReferralRepository referralRepository = new ReferralRepository();
        ReferralRevenueTierRepository referralRevenueTierRepository = new ReferralRevenueTierRepository();
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

        FcmService fcmService = new FcmService(vertx, pool, deviceRepository);
        NotificationService notificationService = new NotificationService(pool, notificationRepository, fcmService);
        
        // Redis 珥덇린??(?좏겙 釉붾옓由ъ뒪?맞룹옱?쒕룄 ?먯슜)
        JsonObject redisConfig = config().getJsonObject("redis", new JsonObject());
        RedisAPI redisApi = initializeRedis(redisConfig);
        RetryQueuePublisher retryQueuePublisher = redisApi != null ? new RetryQueuePublisher(redisApi) : null;
        EventPublisher eventPublisher = redisApi != null ? new EventPublisher(redisApi) : null;

        // ?대찓???쒕퉬??(SMTP ?ㅼ젙? ?좏깮 ?ы빆, ?ㅽ뙣 ??Redis ?ъ떆?????곸옱)
        EmailService emailService = new EmailService(
            vertx,
            config().getJsonObject("smtp", new JsonObject()),
            frontendConfig,
            retryQueuePublisher
        );
        
        // UserService瑜?癒쇱? ?앹꽦 (AuthService?먯꽌 ?ъ슜)
        UserService userService = new UserService(
            pool, userRepository, jwtAuth, jwtConfig, frontendConfig, emailVerificationRepository, emailService, redisApi, userExternalIdRepository);
        
        // WebClient 珥덇린??(?몃? API ?몄텧??
        WebClient webClient = WebClient.create(vertx);
        
        // TRON ?쒕퉬??URL 媛?몄삤湲?
        JsonObject blockchainConfig = config().getJsonObject("blockchain", new JsonObject());
        JsonObject tronConfig = blockchainConfig.getJsonObject("tron", new JsonObject());
        String tronServiceUrl = tronConfig.getString("serviceUrl", "");
        
        WalletService walletService = new WalletService(pool, walletRepository, currencyRepository, webClient, tronServiceUrl, redisApi);
        TransferService transferService = new TransferService(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, notificationService, airdropRepository);
        ReferralService referralService = new ReferralService(pool, referralRepository, userRepository, emailVerificationRepository, transferService, referralRevenueTierRepository);
        BonusService bonusService = new BonusService(
            pool, bonusRepository, referralRepository, subscriptionRepository, reviewRepository,
            agencyRepository, socialLinkRepository, phoneVerificationRepository);
        LevelService levelService = new LevelService(
            pool, userRepository, miningRepository, notificationService);
        MiningService miningService = new MiningService(
            pool, miningRepository, userRepository, bonusService, bonusRepository, walletRepository,
            referralService, transferRepository, currencyRepository, emailVerificationRepository, levelService, notificationService);
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
        
        // CurrencyService 珥덇린??(?ㅻⅨ ?쒕퉬?ㅼ뿉???ъ슜)
        ExchangeRateRepository exchangeRateRepository = new ExchangeRateRepository();
        CurrencyService currencyService = new CurrencyService(pool, currencyRepository, exchangeRateRepository, webClient);
        
        SwapRepository swapRepository = new SwapRepository();
        SwapService swapService = new SwapService(
            pool, swapRepository, currencyRepository, currencyService, transferRepository, notificationService);
        ExchangeRepository exchangeRepository = new ExchangeRepository();
        ExchangeService exchangeService = new ExchangeService(
            pool, exchangeRepository, currencyRepository, transferRepository, userRepository, notificationService);
        PaymentDepositRepository paymentDepositRepository = new PaymentDepositRepository();
        PaymentDepositService paymentDepositService = new PaymentDepositService(
            pool, paymentDepositRepository, currencyRepository, transferRepository, notificationService);
        TokenDepositRepository tokenDepositRepository = new TokenDepositRepository();
        TokenDepositService tokenDepositService = new TokenDepositService(
            pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, eventPublisher, notificationService);
        String depositScannerApiKey = System.getenv("DEPOSIT_SCANNER_API_KEY");
        if (depositScannerApiKey == null || depositScannerApiKey.isEmpty()) {
            depositScannerApiKey = config().getString("depositScanner.apiKey");
        }
        InternalDepositHandler internalDepositHandler = new InternalDepositHandler(
            vertx, pool, walletRepository, tokenDepositService, depositScannerApiKey);
        InternalWithdrawalHandler internalWithdrawalHandler = new InternalWithdrawalHandler(
            vertx, transferService, depositScannerApiKey);
        
        // Google OAuth ?ㅼ젙 (?섍꼍 蹂???곗꽑)
        JsonObject googleConfig = applyGoogleEnvOverrides(config().getJsonObject("google", new JsonObject()));
        // Kakao OAuth ?ㅼ젙 (?섍꼍 蹂???곗꽑)
        JsonObject kakaoConfig = applyKakaoEnvOverrides(config().getJsonObject("kakao", new JsonObject()));
        // Apple OAuth ?ㅼ젙 (?섍꼍 蹂???곗꽑)
        JsonObject appleConfig = applyAppleEnvOverrides(config().getJsonObject("apple", new JsonObject()));
        
        // Service 珥덇린??(AuthService???ㅻⅨ ?쒕퉬?ㅻ뱾 ?댄썑??珥덇린??
        String minAppVersion = System.getenv("MIN_APP_VERSION");
        if (minAppVersion == null || minAppVersion.isBlank()) {
            minAppVersion = config().getJsonObject("frontend", new JsonObject()).getString("minAppVersion");
        }
        AppConfigRepository appConfigRepository = new AppConfigRepository();
        AuthService authService = new AuthService(
            pool, userRepository, userService, jwtAuth, jwtConfig, socialLinkRepository, phoneVerificationRepository,
            redisApi, walletRepository, transferRepository, bonusRepository, miningRepository, missionRepository,
            notificationRepository, subscriptionRepository, reviewRepository, agencyRepository,
            swapRepository, exchangeRepository, paymentDepositRepository,
            tokenDepositRepository, airdropRepository, inquiryRepository, emailVerificationRepository,
            signupEmailCodeRepository, deviceRepository, referralRepository, emailService, webClient, googleConfig, kakaoConfig, appleConfig,
            minAppVersion, appConfigRepository);
        AuthUtils.configureDeviceGuard(new DeviceGuard(pool, deviceRepository, authService));
        InquiryService inquiryService = new InquiryService(
            pool, inquiryRepository, userService, notificationService);
        MissionService missionService = new MissionService(
            pool, missionRepository, notificationService);
        AirdropService airdropService = new AirdropService(
            pool, airdropRepository, currencyRepository, transferRepository, walletRepository);
        ClientService clientService = new ClientService(
            pool, clientRepository, userExternalIdRepository, userRepository, jwtAuth, redisApi);
        
        // ?대찓???ъ떆?????뚮퉬 (Redis ?덉쓣 ?뚮쭔, 二쇨린?곸쑝濡?RPOP ???ъ떆????理쒖쥌 ?ㅽ뙣 ??DLQ)
        if (redisApi != null && retryQueuePublisher != null) {
            startEmailRetryProcessor(emailService, retryQueuePublisher);
        }

        // 梨꾧뎬 ?뺤궛 諛곗튂: 1?쒓컙留덈떎 誘몄젙???몄뀡??mining_history쨌internal_transfers??諛섏쁺 (API 誘명샇異??좎? ???
        startMiningSettlementBatch(miningService);

        // Exchange rate refresh scheduler: external providers -> DB(upsert). API reads from DB only.
        startExchangeRateRefreshScheduler(currencyService);

        // Re-dispatch pending withdrawals so external settlement is eventually processed.
        startWithdrawalRedispatchScheduler(transferService);

        // Handler 珥덇린??
        AuthHandler authHandler = new AuthHandler(vertx, authService, jwtAuth);
        AppHandler appHandler = new AppHandler(vertx, pool, appConfigRepository, minAppVersion);
        UserHandler userHandler = new UserHandler(vertx, userService, jwtAuth, deviceRepository, pool);
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
        
        // 紐⑤땲?곕쭅 API ??(?섍꼍 蹂???먮뒗 config?먯꽌 媛?몄삤湲?
        String monitoringApiKey = System.getenv("MONITORING_API_KEY");
        if (monitoringApiKey == null || monitoringApiKey.isEmpty()) {
            monitoringApiKey = config().getString("monitoring.apiKey", "default-monitoring-key-change-in-production");
            log.info("Monitoring API key loaded from config.json: {}", monitoringApiKey);
        } else {
            log.info("Monitoring API key loaded from environment variable MONITORING_API_KEY");
        }
        MonitoringHandler monitoringHandler = new MonitoringHandler(vertx, monitoringApiKey);
        
        // Router ?앹꽦
        Router mainRouter = Router.router(vertx);
        
        // ?꾩뿭 ?몃뱾??
        setupGlobalHandlers(mainRouter);
        
        // 怨듦컻 API (?몄쬆 遺덊븘??
        mainRouter.mountSubRouter("/api/v1/auth", authHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/app", appHandler.getRouter());
        
        // ?덈꺼 API瑜?癒쇱? ?깅줉 (援ъ껜?곸씤 寃쎈줈 ?곗꽑)
        mainRouter.mountSubRouter("/api/v1/levels", levelHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/user", levelHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/users", levelHandler.getRouter());
        
        // ?ъ슜??API (?덈꺼 API ?댄썑???깅줉?섏뿬 /:id媛 ?섏쨷??留ㅼ묶?섎룄濡?
        mainRouter.mountSubRouter("/api/v1/users", userHandler.getRouter());
        // /api/v1/user??吏??(?⑥닔??寃쎈줈)
        mainRouter.mountSubRouter("/api/v1/user", userHandler.getRouter());
        
        // ?덊띁??API (?몄쬆 ?꾩슂, ?몃뱾???대??먯꽌 JWT 泥섎━)
        mainRouter.mountSubRouter("/api/v1/referrals", referralHandler.getRouter());
        
        // ?꾩넚 API (?몄쬆 ?꾩슂, ?몃뱾???대??먯꽌 JWT 泥섎━)
        mainRouter.mountSubRouter("/api/v1/transfers", transferHandler.getRouter());
        
        // 蹂대꼫??API
        mainRouter.mountSubRouter("/api/v1/bonus", bonusHandler.getRouter());
        
        // 梨꾧뎬 API
        mainRouter.mountSubRouter("/api/v1/mining", miningHandler.getRouter());
        
        // 怨듭??ы빆 API
        mainRouter.mountSubRouter("/api/v1/notices", noticeHandler.getRouter());
        
        // ?뚮┝ API
        mainRouter.mountSubRouter("/api/v1/notifications", notificationHandler.getRouter());
        
        // 臾몄쓽?섍린 API
        mainRouter.mountSubRouter("/api/v1/inquiries", inquiryHandler.getRouter());
        
        // 誘몄뀡 API
        mainRouter.mountSubRouter("/api/v1/missions", missionHandler.getRouter());
        
        // 援щ룆 API
        mainRouter.mountSubRouter("/api/v1/subscription", subscriptionHandler.getRouter());
        
        // 由щ럭 API
        mainRouter.mountSubRouter("/api/v1/review", reviewHandler.getRouter());
        
        // ?먯씠?꾩떆 API
        mainRouter.mountSubRouter("/api/v1/agency", agencyHandler.getRouter());
        
        // ??궧 API
        mainRouter.mountSubRouter("/api/v1/ranking", rankingHandler.getRouter());
        
        // 諛곕꼫 API
        mainRouter.mountSubRouter("/api/v1/banners", bannerHandler.getRouter());
        
        // ?ㅼ솑 API
        mainRouter.mountSubRouter("/api/v1/swap", swapHandler.getRouter());
        
        // ?섏쟾 API
        mainRouter.mountSubRouter("/api/v1/exchange", exchangeHandler.getRouter());
        
        // 寃곗젣 ?낃툑 API
        mainRouter.mountSubRouter("/api/v1/payment", paymentDepositHandler.getRouter());
        
        // ?좏겙 ?낃툑 API
        mainRouter.mountSubRouter("/api/v1/deposits", tokenDepositHandler.getRouter());
        // ?낃툑 ?ㅼ틦?덉슜 ?대? API (API ???몄쬆)
        mainRouter.mountSubRouter("/api/v1/internal/deposits", internalDepositHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/internal/withdrawals", internalWithdrawalHandler.getRouter());
        
        // ?먯뼱?쒕엻 API
        mainRouter.mountSubRouter("/api/v1/airdrop", airdropHandler.getRouter());
        
        // ?대씪?댁뼵??API (API Key 湲곕컲 ?좏겙 諛쒓툒 諛??좎? ?곗씠???섏떊)
        mainRouter.mountSubRouter("/api/v1/client", clientHandler.getRouter());
        
        // 蹂댁븞 API (嫄곕옒 鍮꾨?踰덊샇 ??
        mainRouter.mountSubRouter("/api/v1/security", securityHandler.getRouter());
        
        // ?듯솕 API (?섏쑉 議고쉶??怨듦컻 API)
        mainRouter.mountSubRouter("/api/v1/currencies", currencyHandler.getRouter());
        
        // 紐⑤땲?곕쭅 ?섏씠吏 (role 2, 3留??묎렐 媛??
        mainRouter.mountSubRouter("/", monitoringHandler.getRouter());
        
        // JWT ?몄쬆???꾩슂??API
        Router protectedRouter = Router.router(vertx);
        protectedRouter.route().handler(JWTAuthHandler.create(jwtAuth));
        protectedRouter.mountSubRouter("/api/v1/wallets", walletHandler.getRouter());
        
        mainRouter.mountSubRouter("/", protectedRouter);
        
        // HTTP ?쒕쾭 ?쒖옉
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
        // CORS - custom handler to allow non-standard origins (capacitor://, ionic://)
        router.route().handler(ctx -> {
            String origin = ctx.request().getHeader("Origin");
            if (origin != null && !origin.isBlank()) {
                ctx.response().putHeader("Access-Control-Allow-Origin", origin);
                ctx.response().putHeader("Vary", "Origin");
                ctx.response().putHeader("Access-Control-Allow-Credentials", "true");
                ctx.response().putHeader("Access-Control-Max-Age", "86400");
                ctx.response().putHeader("Access-Control-Expose-Headers", "Content-Length, Content-Type, Authorization");
            }

            if (ctx.request().method() == HttpMethod.OPTIONS) {
                ctx.response().putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                String reqHeaders = ctx.request().getHeader("Access-Control-Request-Headers");
                if (reqHeaders != null && !reqHeaders.isBlank()) {
                    ctx.response().putHeader("Access-Control-Allow-Headers", reqHeaders);
                } else {
                    ctx.response().putHeader(
                        "Access-Control-Allow-Headers",
                        "Content-Type, Authorization, Accept, Origin, X-Requested-With, X-Device-Id, X-Device-Type, " +
                        "X-Device-Os, X-App-Version, X-Platform, X-Client-Version, X-Client-Type, X-App-Build, User-Agent, " +
                        "Referer, X-Forwarded-For, X-Real-IP, X-Forwarded-Proto, Cache-Control, Pragma, If-Modified-Since, " +
                        "If-None-Match, Accept-Language, Accept-Encoding, Access-Control-Request-Method, Access-Control-Request-Headers"
                    );
                }
                ctx.response().setStatusCode(204).end();
                return;
            }

            ctx.next();
        });
        
        // Body Handler
        router.route().handler(BodyHandler.create());
        
        // Request 濡쒓퉭 諛?硫뷀듃由??섏쭛 (/health, /metrics??濡쒓렇 ?앸왂???ㅻⅨ 濡쒓렇 ?뺤씤 ?⑹씠)
        router.route().handler(ctx -> {
            long startTime = System.currentTimeMillis();
            String method = ctx.request().method().toString();
            String path = ctx.request().path();
            boolean skipRequestLog = "/health".equals(path) || "/metrics".equals(path);
            if (!skipRequestLog) {
                log.info("[REQUEST] {} {} from {}", method, path, ctx.request().remoteAddress());
            }
            // ?쒖옉 ?쒓컙??而⑦뀓?ㅽ듃?????(failure handler?먯꽌 ?ъ슜)
            ctx.put("startTime", startTime);
            
            // ?묐떟 ?꾨즺 ??硫뷀듃由?湲곕줉
            ctx.response().endHandler(v -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = ctx.response().getStatusCode();
                metricsCollector.recordRequest(method, path, statusCode, duration);
            });
            
            ctx.next();
        });
        
        // Failure Handler (?먮윭 諛쒖깮 ??硫뷀듃由?湲곕줉)
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
            // Docker ?섍꼍: openapi.yaml, 媛쒕컻 ?섍꼍: src/main/resources/openapi.yaml
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
        // Prometheus 硫뷀듃由??붾뱶?ъ씤??
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
     * ?대찓???ъ떆????二쇨린 ?뚮퉬: RPOP ???ъ떆????理쒖쥌 ?ㅽ뙣 ??DLQ
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
     * 梨꾧뎬 ?뺤궛 諛곗튂: 1?쒓컙留덈떎 ?뺤궛 ?湲??몄뀡(last_settled_at < ends_at)??settle?섏뿬
     * mining_history쨌internal_transfers??諛섏쁺. ?깆쓣 ?댁? ?딆? ?좎???梨꾧뎬 ?꾨즺 ??DB??諛섏쁺??
     */
    private void startMiningSettlementBatch(MiningService miningService) {
        long intervalMs = 3600_000L; // 1?쒓컙
        vertx.setPeriodic(intervalMs, id -> {
            miningService.runSettlementBatch()
                .onSuccess(count -> {
                    if (count > 0) {
                        log.info("Mining settlement batch completed: {} users settled", count);
                    }
                })
                .onFailure(throwable -> log.warn("Mining settlement batch failed", throwable));
        });
        log.info("Mining settlement batch started (interval {} ms)", intervalMs);
    }

    /**
     * Exchange rate refresh scheduler.
     * - Refreshes rates from external providers and upserts into DB.
     * - Clients read rates from DB only (no per-user external calls).
     */
    private void startExchangeRateRefreshScheduler(CurrencyService currencyService) {
        long intervalMs = parseLongEnv("EXCHANGE_RATE_REFRESH_MS", 300_000L); // 5 minutes default

        // Kick once shortly after startup.
        vertx.setTimer(1500, id -> currencyService.refreshExchangeRates()
            .onSuccess(v -> log.info("Exchange rates refreshed on startup"))
            .onFailure(t -> log.warn("Exchange rate startup refresh failed", t)));

        vertx.setPeriodic(intervalMs, id -> currencyService.refreshExchangeRates()
            .onFailure(t -> log.warn("Exchange rate scheduled refresh failed", t)));

        log.info("Exchange rate refresh scheduler started (interval {} ms)", intervalMs);
    }

    /**
     * Periodically republishes pending withdrawals to guarantee eventual external processing.
     */
    private void startWithdrawalRedispatchScheduler(TransferService transferService) {
        long intervalMs = parseLongEnv("WITHDRAWAL_REDISPATCH_MS", 15_000L);
        int batchSize = (int) Math.max(1L, Math.min(parseLongEnv("WITHDRAWAL_REDISPATCH_BATCH", 100L), 500L));

        // Kick once shortly after startup.
        vertx.setTimer(2_000L, id -> transferService.redispatchPendingWithdrawals(batchSize)
            .onSuccess(count -> {
                if (count > 0) {
                    log.info("Withdrawal redispatch startup run completed: {} events republished", count);
                }
            })
            .onFailure(t -> log.warn("Withdrawal redispatch startup run failed", t)));

        vertx.setPeriodic(intervalMs, id -> transferService.redispatchPendingWithdrawals(batchSize)
            .onSuccess(count -> {
                if (count > 0) {
                    log.info("Withdrawal redispatch scheduled run completed: {} events republished", count);
                }
            })
            .onFailure(t -> log.warn("Withdrawal redispatch scheduled run failed", t)));

        log.info("Withdrawal redispatch scheduler started (interval {} ms, batch {})", intervalMs, batchSize);
    }

    private static long parseLongEnv(String key, long defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    /**
     * Redis 珥덇린??(?좏겙 釉붾옓由ъ뒪?맞룹옱?쒕룄 ?먯슜)
     * Redis媛 ?놁뼱???쒕퉬?ㅻ뒗 ?숈옉?섎룄濡?null??諛섑솚?????덉쓬
     */
    private RedisAPI initializeRedis(JsonObject redisConfig) {
        try {
            String mode = redisConfig.getString("mode", "standalone");
            RedisOptions options = createRedisOptions(redisConfig, mode);
            Redis redisClient = Redis.createClient(vertx, options);
            
            // 鍮꾨룞湲??곌껐? ?섏쨷??泥섎━?섎?濡? ?곌껐 ?ㅽ뙣?대룄 ?쒕퉬?ㅻ뒗 怨꾩냽 ?숈옉
            redisClient.connect()
                .onSuccess(conn -> {
                    log.info("Redis connected successfully for token blacklist (mode: {})", mode);
                })
                .onFailure(throwable -> {
                    log.warn("Failed to connect to Redis for token blacklist. Logout will work but tokens won't be blacklisted: {}", throwable.getMessage());
                });
            
            // RedisAPI???곌껐???꾨즺?섍린 ?꾩뿉???앹꽦 媛??(?곌껐 ?ㅽ뙣 ??null 諛섑솚)
            return RedisAPI.api(redisClient);
        } catch (Exception e) {
            log.warn("Failed to initialize Redis for token blacklist: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Redis 紐⑤뱶???곕Ⅸ ?듭뀡 ?앹꽦 (EventVerticle怨??숈씪??濡쒖쭅)
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
        putIfEnvSet(config, "iosClientId", "GOOGLE_IOS_CLIENT_ID");
        putIfEnvSet(config, "iosRedirectUri", "GOOGLE_IOS_REDIRECT_URI");
        return config;
    }

    private static JsonObject applyKakaoEnvOverrides(JsonObject baseConfig) {
        JsonObject config = baseConfig.copy();
        putIfEnvSet(config, "clientId", "KAKAO_CLIENT_ID");
        putIfEnvSet(config, "clientSecret", "KAKAO_CLIENT_SECRET");
        putIfEnvSet(config, "redirectUri", "KAKAO_REDIRECT_URI");
        putIfEnvSet(config, "androidRedirectUri", "KAKAO_ANDROID_REDIRECT_URI");
        putIfEnvSet(config, "iosRedirectUri", "KAKAO_IOS_REDIRECT_URI");
        return config;
    }

    private static JsonObject applyAppleEnvOverrides(JsonObject baseConfig) {
        JsonObject config = baseConfig.copy();
        putIfEnvSet(config, "teamId", "APPLE_TEAM_ID");
        putIfEnvSet(config, "keyId", "APPLE_KEY_ID");
        putIfEnvSet(config, "serviceId", "APPLE_SERVICE_ID");
        putIfEnvSet(config, "privateKeyPath", "APPLE_PRIVATE_KEY_PATH");
        putIfEnvSet(config, "redirectUri", "APPLE_REDIRECT_URI");
        return config;
    }

    private static void putIfEnvSet(JsonObject config, String key, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}

