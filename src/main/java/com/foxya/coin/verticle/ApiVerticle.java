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
import com.foxya.coin.user.ProfileImageModerationService;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.UserService;
import com.foxya.coin.user.UserExternalIdRepository;
import com.foxya.coin.device.DeviceRepository;
import com.foxya.coin.wallet.WalletHandler;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.WalletService;
import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.app.AppHandler;
import com.foxya.coin.app.InternalConfigHandler;
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
import com.foxya.coin.notification.AdminNotificationHandler;
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

import java.util.concurrent.atomic.AtomicBoolean;

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
        
        // Normalized comment.
        metricsCollector = new MetricsCollector();
        
        JsonObject httpConfig = config().getJsonObject("http", new JsonObject());
        JsonObject databaseConfig = config().getJsonObject("database", new JsonObject());
        JsonObject jwtConfig = config().getJsonObject("jwt", new JsonObject());
        JsonObject frontendConfig = config().getJsonObject("frontend", new JsonObject());
        
        int port = httpConfig.getInteger("port", 8080);
        
        // Normalized comment.
        PgPool pool = createPgPool(databaseConfig);
        
        // Normalized comment.
        JWTAuth jwtAuth = createJwtAuth(jwtConfig);
        
        // Normalized comment.
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
        NotificationService notificationService = new NotificationService(pool, notificationRepository, fcmService, userRepository);
        
        // Normalized comment.
        JsonObject redisConfig = config().getJsonObject("redis", new JsonObject());
        RedisAPI redisApi = initializeRedis(redisConfig);
        RetryQueuePublisher retryQueuePublisher = redisApi != null ? new RetryQueuePublisher(redisApi) : null;
        EventPublisher eventPublisher = redisApi != null ? new EventPublisher(redisApi) : null;

        // Normalized comment.
        EmailService emailService = new EmailService(
            vertx,
            config().getJsonObject("smtp", new JsonObject()),
            frontendConfig,
            retryQueuePublisher
        );

        // Normalized comment.
        WebClient webClient = WebClient.create(vertx);

        String profileImageUploadDir = System.getenv("PROFILE_IMAGE_UPLOAD_DIR");
        if (profileImageUploadDir == null || profileImageUploadDir.isBlank()) {
            profileImageUploadDir = config().getString("profileImageUploadDir", "/tmp/fox_coin/profile-images");
        }

        boolean profileImageModerationEnabled = "true".equalsIgnoreCase(System.getenv("PROFILE_IMAGE_MODERATION_ENABLED"));
        if (!profileImageModerationEnabled) {
            profileImageModerationEnabled = config().getBoolean("profileImageModerationEnabled", false);
        }
        String googleCloudVisionApiKey = System.getenv("GOOGLE_CLOUD_VISION_API_KEY");
        if (googleCloudVisionApiKey == null || googleCloudVisionApiKey.isBlank()) {
            // Backward-compat for accidental typo in env var key.
            googleCloudVisionApiKey = System.getenv("OOGLE_CLOUD_VISION_API_KEY");
        }
        if (googleCloudVisionApiKey == null || googleCloudVisionApiKey.isBlank()) {
            googleCloudVisionApiKey = config().getString("googleCloudVisionApiKey", "");
        }
        if (profileImageModerationEnabled) {
            if (googleCloudVisionApiKey == null || googleCloudVisionApiKey.isBlank()) {
                log.warn("Profile image moderation is enabled, but GOOGLE_CLOUD_VISION_API_KEY is not configured.");
            } else {
                log.info("Profile image moderation is enabled.");
            }
        }
        ProfileImageModerationService profileImageModerationService = new ProfileImageModerationService(
            webClient,
            profileImageModerationEnabled,
            googleCloudVisionApiKey
        );
        
        // Normalized comment.
        UserService userService = new UserService(
            pool,
            userRepository,
            jwtAuth,
            jwtConfig,
            frontendConfig,
            emailVerificationRepository,
            emailService,
            redisApi,
            userExternalIdRepository,
            profileImageUploadDir,
            profileImageModerationService
        );
        
        // Normalized comment.
        JsonObject blockchainConfig = config().getJsonObject("blockchain", new JsonObject());
        JsonObject tronConfig = blockchainConfig.getJsonObject("tron", new JsonObject());
        String tronServiceUrl = tronConfig.getString("serviceUrl", "");
        
        WalletService walletService = new WalletService(pool, walletRepository, currencyRepository, webClient, tronServiceUrl, redisApi);
        AppConfigRepository appConfigRepository = new AppConfigRepository();
        TransferService transferService = new TransferService(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, notificationService, airdropRepository, appConfigRepository);
        ReferralService referralService = new ReferralService(pool, referralRepository, userRepository, emailVerificationRepository, transferService, referralRevenueTierRepository, airdropRepository, notificationService);
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
        
        // Normalized comment.
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
            pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, eventPublisher, notificationService, walletRepository, appConfigRepository);
        String depositScannerApiKey = System.getenv("DEPOSIT_SCANNER_API_KEY");
        if (depositScannerApiKey == null || depositScannerApiKey.isEmpty()) {
            depositScannerApiKey = config().getString("depositScanner.apiKey");
        }
        InternalDepositHandler internalDepositHandler = new InternalDepositHandler(
            vertx, pool, walletRepository, tokenDepositService, depositScannerApiKey);
        InternalWithdrawalHandler internalWithdrawalHandler = new InternalWithdrawalHandler(
            vertx, transferService, depositScannerApiKey);
        InternalConfigHandler internalConfigHandler = new InternalConfigHandler(
            vertx, pool, appConfigRepository, depositScannerApiKey);
        
        // Normalized comment.
        JsonObject googleConfig = applyGoogleEnvOverrides(config().getJsonObject("google", new JsonObject()));
        // Normalized comment.
        JsonObject kakaoConfig = applyKakaoEnvOverrides(config().getJsonObject("kakao", new JsonObject()));
        // Normalized comment.
        JsonObject appleConfig = applyAppleEnvOverrides(config().getJsonObject("apple", new JsonObject()));
        
        // Normalized comment.
        String minAppVersion = System.getenv("MIN_APP_VERSION");
        if (minAppVersion == null || minAppVersion.isBlank()) {
            minAppVersion = config().getJsonObject("frontend", new JsonObject()).getString("minAppVersion");
        }
        AuthService authService = new AuthService(
            pool, userRepository, userService, jwtAuth, jwtConfig, socialLinkRepository, phoneVerificationRepository,
            redisApi, walletRepository, transferRepository, bonusRepository, miningRepository, missionRepository,
            notificationService, notificationRepository, subscriptionRepository, reviewRepository, agencyRepository,
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
            pool, airdropRepository, currencyRepository, transferRepository, walletRepository, notificationService);
        ClientService clientService = new ClientService(
            pool, clientRepository, userExternalIdRepository, userRepository, jwtAuth, redisApi);
        
        // Normalized comment.
        if (redisApi != null && retryQueuePublisher != null) {
            startEmailRetryProcessor(emailService, retryQueuePublisher);
        }

        // Normalized comment.
        startMiningSettlementBatch(miningService);

        // Exchange rate refresh scheduler: external providers -> DB(upsert). API reads from DB only.
        startExchangeRateRefreshScheduler(currencyService);

        // Re-dispatch pending withdrawals so external settlement is eventually processed.
        startWithdrawalRedispatchScheduler(transferService);
        startWaitingWithdrawalPromotionScheduler(transferService);
        startLevelSyncScheduler(levelService);

        // Normalized comment.
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
        AdminNotificationHandler adminNotificationHandler = new AdminNotificationHandler(vertx, notificationService, jwtAuth);
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
        
        // Normalized comment.
        String monitoringApiKey = System.getenv("MONITORING_API_KEY");
        if (monitoringApiKey == null || monitoringApiKey.isEmpty()) {
            monitoringApiKey = config().getString("monitoring.apiKey", "default-monitoring-key-change-in-production");
            log.info("Monitoring API key loaded from config.json: {}", monitoringApiKey);
        } else {
            log.info("Monitoring API key loaded from environment variable MONITORING_API_KEY");
        }
        MonitoringHandler monitoringHandler = new MonitoringHandler(vertx, monitoringApiKey);
        
        // Normalized comment.
        Router mainRouter = Router.router(vertx);
        
        // Normalized comment.
        setupGlobalHandlers(mainRouter);
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/auth", authHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/app", appHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/levels", levelHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/user", levelHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/users", levelHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/users", userHandler.getRouter());
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/user", userHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/referrals", referralHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/transfers", transferHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/bonus", bonusHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/mining", miningHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/notices", noticeHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/notifications", notificationHandler.getRouter());

        // Admin test notification API (DB insert + FCM send via NotificationService)
        mainRouter.mountSubRouter("/api/v1/admin/notifications", adminNotificationHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/inquiries", inquiryHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/missions", missionHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/subscription", subscriptionHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/review", reviewHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/agency", agencyHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/ranking", rankingHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/banners", bannerHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/swap", swapHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/exchange", exchangeHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/payment", paymentDepositHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/deposits", tokenDepositHandler.getRouter());
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/internal/deposits", internalDepositHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/internal/withdrawals", internalWithdrawalHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/internal/config", internalConfigHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/airdrop", airdropHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/client", clientHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/security", securityHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/api/v1/currencies", currencyHandler.getRouter());
        
        // Normalized comment.
        mainRouter.mountSubRouter("/", monitoringHandler.getRouter());
        
        // Normalized comment.
        Router protectedRouter = Router.router(vertx);
        protectedRouter.route().handler(JWTAuthHandler.create(jwtAuth));
        protectedRouter.mountSubRouter("/api/v1/wallets", walletHandler.getRouter());
        
        mainRouter.mountSubRouter("/", protectedRouter);
        
        // Normalized comment.
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
        
        // Normalized comment.
        router.route().handler(ctx -> {
            long startTime = System.currentTimeMillis();
            String method = ctx.request().method().toString();
            String path = ctx.request().path();
            boolean skipRequestLog = "/health".equals(path) || "/metrics".equals(path);
            if (!skipRequestLog) {
                log.info("[REQUEST] {} {} from {}", method, path, ctx.request().remoteAddress());
            }
            // Normalized comment.
            ctx.put("startTime", startTime);
            
            // Normalized comment.
            ctx.response().endHandler(v -> {
                long duration = System.currentTimeMillis() - startTime;
                int statusCode = ctx.response().getStatusCode();
                metricsCollector.recordRequest(method, path, statusCode, duration);
            });
            
            ctx.next();
        });
        
        // Normalized comment.
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
            // Normalized comment.
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
        // Normalized comment.
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
      * Normalized comment.
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
      * Normalized comment.
      * Normalized comment.
     */
    private void startMiningSettlementBatch(MiningService miningService) {
        long intervalMs = 3600_000L; // 1?�간
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

    private void startWaitingWithdrawalPromotionScheduler(TransferService transferService) {
        long intervalMs = parseLongEnv("WAITING_WITHDRAWAL_PROMOTION_MS", 30_000L);
        int batchSize = (int) Math.max(1L, Math.min(parseLongEnv("WAITING_WITHDRAWAL_PROMOTION_BATCH", 100L), 500L));

        vertx.setTimer(2_500L, id -> transferService.promoteWaitingWithdrawals(batchSize)
            .onSuccess(count -> {
                if (count > 0) {
                    log.info("Waiting withdrawal promotion startup run completed: {} withdrawals promoted", count);
                }
            })
            .onFailure(t -> log.warn("Waiting withdrawal promotion startup run failed", t)));

        vertx.setPeriodic(intervalMs, id -> transferService.promoteWaitingWithdrawals(batchSize)
            .onSuccess(count -> {
                if (count > 0) {
                    log.info("Waiting withdrawal promotion scheduled run completed: {} withdrawals promoted", count);
                }
            })
            .onFailure(t -> log.warn("Waiting withdrawal promotion scheduled run failed", t)));

        log.info("Waiting withdrawal promotion scheduler started (interval {} ms, batch {})", intervalMs, batchSize);
    }

    /**
     * EXP-레벨 불일치 사용자 보정 스케줄러.
     * - users.exp 기준 계산 레벨이 users.level보다 높을 때 자동 보정
     * - 레벨업 알림은 기존 dedupe 규칙(related_id=newLevel)으로 중복 방지
     */
    private void startLevelSyncScheduler(LevelService levelService) {
        long intervalMs = parseLongEnv("LEVEL_SYNC_SCHEDULER_MS", 300_000L);
        int batchSize = (int) Math.max(1L, Math.min(parseLongEnv("LEVEL_SYNC_SCHEDULER_BATCH", 300L), 2000L));
        AtomicBoolean running = new AtomicBoolean(false);

        Runnable runTask = () -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            levelService.runLevelSyncBatch(batchSize)
                .onSuccess(updatedCount -> {
                    if (updatedCount > 0) {
                        log.info("Level sync scheduled run completed: {} users updated", updatedCount);
                    }
                })
                .onFailure(t -> log.warn("Level sync scheduled run failed", t))
                .onComplete(ar -> running.set(false));
        };

        vertx.setTimer(3_000L, id -> runTask.run());
        vertx.setPeriodic(intervalMs, id -> runTask.run());
        log.info("Level sync scheduler started (interval {} ms, batch {})", intervalMs, batchSize);
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
      * Normalized comment.
      * Normalized comment.
     */
    private RedisAPI initializeRedis(JsonObject redisConfig) {
        try {
            String mode = redisConfig.getString("mode", "standalone");
            RedisOptions options = createRedisOptions(redisConfig, mode);
            Redis redisClient = Redis.createClient(vertx, options);
            
            // Normalized comment.
            redisClient.connect()
                .onSuccess(conn -> {
                    log.info("Redis connected successfully for token blacklist (mode: {})", mode);
                })
                .onFailure(throwable -> {
                    log.warn("Failed to connect to Redis for token blacklist. Logout will work but tokens won't be blacklisted: {}", throwable.getMessage());
                });
            
            // Normalized comment.
            return RedisAPI.api(redisClient);
        } catch (Exception e) {
            log.warn("Failed to initialize Redis for token blacklist: {}", e.getMessage());
            return null;
        }
    }
    
    /**
      * Normalized comment.
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
        putIfEnvSet(config, "iosClientId", "APPLE_IOS_CLIENT_ID");
        putIfEnvSet(config, "privateKeyPath", "APPLE_PRIVATE_KEY_PATH");
        putIfEnvSet(config, "redirectUri", "APPLE_REDIRECT_URI");
        putIfEnvSet(config, "iosRedirectUri", "APPLE_IOS_REDIRECT_URI");
        return config;
    }

    private static void putIfEnvSet(JsonObject config, String key, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            config.put(key, value);
        }
    }
}
