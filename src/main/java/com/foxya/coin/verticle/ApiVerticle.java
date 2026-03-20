package com.foxya.coin.verticle;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
import io.vertx.sqlclient.SqlConnection;
import com.foxya.coin.common.utils.ErrorHandler;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.referral.ReferralHandler;
import com.foxya.coin.referral.ReferralRepository;
import com.foxya.coin.referral.ReferralRevenueTierRepository;
import com.foxya.coin.referral.ReferralService;
import com.foxya.coin.transfer.TransferHandler;
import com.foxya.coin.transfer.KorionWithdrawalBridgeClient;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.TransferService;
import com.foxya.coin.transfer.WithdrawalBridgeClient;
import com.foxya.coin.user.UserHandler;
import com.foxya.coin.user.ProfileImageModerationService;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.UserService;
import com.foxya.coin.user.UserExternalIdRepository;
import com.foxya.coin.device.DeviceRepository;
import com.foxya.coin.wallet.WalletHandler;
import com.foxya.coin.wallet.InternalWalletHandler;
import com.foxya.coin.wallet.VirtualWalletMappingRepository;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.WalletService;
import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.app.AppHandler;
import com.foxya.coin.app.InternalConfigHandler;
import com.foxya.coin.admin.AdminWalletOpsHandler;
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
import com.foxya.coin.transfer.InternalOfflinePayHandler;
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
import com.foxya.coin.notification.NoticeNotificationDispatchRepository;
import com.foxya.coin.notification.NoticeNotificationDispatchService;
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
import com.foxya.coin.common.alert.DbAlertMonitorService;
import com.foxya.coin.retry.EmailRetryJob;
import com.foxya.coin.retry.ExchangeRateRetryJob;
import com.foxya.coin.retry.FcmRetryJob;
import com.foxya.coin.retry.BulkNotificationJob;
import com.foxya.coin.retry.RetryQueuePublisher;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.common.DeviceGuard;
import com.foxya.coin.currency.CurrencyHandler;
import com.foxya.coin.currency.CurrencyService;
import com.foxya.coin.currency.ExchangeRateRepository;
import com.foxya.coin.currency.CoinPriceRepository;
import com.foxya.coin.country.CountryCodeRepository;
import com.foxya.coin.country.CountryCodeService;
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
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.JWTAuthHandler;
import com.foxya.coin.common.metrics.MetricsCollector;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisReplicas;
import io.vertx.core.json.JsonArray;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
public class ApiVerticle extends AbstractVerticle {

    private static final String CFG_FCM_RETRY_PROCESSOR_MS = "fcm_retry_processor_ms";
    private static final String CFG_FCM_RETRY_BATCH = "fcm_retry_batch";
    private static final String CFG_FCM_RETRY_BLOCK_MS = "fcm_retry_block_ms";
    private static final String CFG_FCM_RETRY_CONSUMER_GROUP = "fcm_retry_consumer_group";
    private static final String CFG_EXCHANGE_RATE_RETRY_MAX_RETRIES = "exchange_rate_retry_max_retries";
    private static final String CFG_EXCHANGE_RATE_RETRY_PROCESSOR_MS = "exchange_rate_retry_processor_ms";
    private static final String CFG_EXCHANGE_RATE_RETRY_BATCH = "exchange_rate_retry_batch";
    private static final String CFG_EXCHANGE_RATE_RETRY_BLOCK_MS = "exchange_rate_retry_block_ms";
    private static final String CFG_EXCHANGE_RATE_RETRY_CONSUMER_GROUP = "exchange_rate_retry_consumer_group";
    private static final String CFG_BULK_NOTIFICATION_PROCESSOR_MS = "bulk_notification_processor_ms";
    private static final String CFG_BULK_NOTIFICATION_BATCH = "bulk_notification_batch";
    private static final String CFG_BULK_NOTIFICATION_BLOCK_MS = "bulk_notification_block_ms";
    private static final String CFG_BULK_NOTIFICATION_CONSUMER_GROUP = "bulk_notification_consumer_group";
    private static final long LOCK_EMAIL_RETRY_PROCESSOR = 10_001L;
    private static final long LOCK_FCM_RETRY_PROCESSOR = 10_002L;
    private static final long LOCK_EXCHANGE_RATE_RETRY_PROCESSOR = 10_003L;
    private static final long LOCK_BULK_NOTIFICATION_PROCESSOR = 10_004L;
    private static final long LOCK_MINING_SETTLEMENT_BATCH = 10_005L;
    private static final long LOCK_COUNTRY_CODE_SYNC = 10_006L;
    private static final long LOCK_EXCHANGE_RATE_REFRESH = 10_007L;
    private static final long LOCK_WITHDRAWAL_REDISPATCH = 10_008L;
    private static final long LOCK_WAITING_WITHDRAWAL_PROMOTION = 10_009L;
    private static final long LOCK_LEVEL_SYNC = 10_010L;
    private static final long LOCK_IMPORTANT_NOTICE_DISPATCH = 10_011L;
    private static final long LOCK_DB_ALERT_MONITOR = 10_012L;
    private static final long LOCK_COIN_PRICE_REFRESH = 10_013L;
    
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
        VirtualWalletMappingRepository virtualWalletMappingRepository = new VirtualWalletMappingRepository();
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

        // Normalized comment.
        JsonObject redisConfig = config().getJsonObject("redis", new JsonObject());
        RedisAPI redisApi = initializeRedis(redisConfig);
        RetryQueuePublisher retryQueuePublisher = redisApi != null ? new RetryQueuePublisher(redisApi) : null;
        EventPublisher eventPublisher = redisApi != null ? new EventPublisher(redisApi) : null;
        AppConfigRepository appConfigRepository = new AppConfigRepository();

        FcmService fcmService = new FcmService(vertx, pool, deviceRepository, retryQueuePublisher, appConfigRepository);
        NotificationService notificationService = new NotificationService(pool, notificationRepository, fcmService, userRepository, retryQueuePublisher);

        // Normalized comment.
        EmailService emailService = new EmailService(
            vertx,
            config().getJsonObject("smtp", new JsonObject()),
            frontendConfig,
            retryQueuePublisher
        );

        // Normalized comment.
        int externalConnectTimeoutMs = (int) Math.max(1000L, Math.min(parseLongEnv("EXTERNAL_HTTP_CONNECT_TIMEOUT_MS", 5000L), 60000L));
        int externalIdleTimeoutSec = (int) Math.max(1L, Math.min(parseLongEnv("EXTERNAL_HTTP_IDLE_TIMEOUT_SECONDS", 15L), 300L));
        WebClientOptions webClientOptions = new WebClientOptions()
            .setConnectTimeout(externalConnectTimeoutMs)
            .setIdleTimeout(externalIdleTimeoutSec);
        WebClient webClient = WebClient.create(vertx, webClientOptions);
        log.info("External WebClient configured (connectTimeout={}ms, idleTimeout={}s)", externalConnectTimeoutMs, externalIdleTimeoutSec);
        DbAlertMonitorService dbAlertMonitorService = DbAlertMonitorService.fromEnv(pool, metricsCollector, webClient);

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
        SubscriptionService subscriptionService = new SubscriptionService(
            pool, subscriptionRepository);
        
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
            subscriptionService,
            profileImageUploadDir,
            profileImageModerationService
        );
        
        // Normalized comment.
        JsonObject blockchainConfig = config().getJsonObject("blockchain", new JsonObject());
        JsonObject tronConfig = blockchainConfig.getJsonObject("tron", new JsonObject());
        String tronServiceUrl = tronConfig.getString("serviceUrl", "");
        
        WalletService walletService = new WalletService(
            pool,
            walletRepository,
            currencyRepository,
            webClient,
            tronServiceUrl,
            redisApi,
            virtualWalletMappingRepository,
            appConfigRepository
        );
        TransferService transferService;
        ReferralService referralService;
        BonusService bonusService = new BonusService(
            pool, bonusRepository, referralRepository, subscriptionRepository, reviewRepository,
            agencyRepository, socialLinkRepository, phoneVerificationRepository);
        LevelService levelService = new LevelService(
            pool, userRepository, miningRepository, notificationService);
        MiningService miningService;
        NoticeService noticeService = new NoticeService(
            pool, noticeRepository);
        NoticeNotificationDispatchRepository noticeNotificationDispatchRepository = new NoticeNotificationDispatchRepository();
        NoticeNotificationDispatchService noticeNotificationDispatchService = new NoticeNotificationDispatchService(pool, noticeNotificationDispatchRepository);
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
        CoinPriceRepository coinPriceRepository = new CoinPriceRepository();
        CurrencyService currencyService = new CurrencyService(pool, currencyRepository, exchangeRateRepository, coinPriceRepository, webClient);
        CountryCodeRepository countryCodeRepository = new CountryCodeRepository();
        CountryCodeService countryCodeService = new CountryCodeService(pool, countryCodeRepository);
        
        SwapRepository swapRepository = new SwapRepository();
        SwapService swapService = new SwapService(
            pool, swapRepository, currencyRepository, appConfigRepository, currencyService, transferRepository, notificationService);
        ExchangeRepository exchangeRepository = new ExchangeRepository();
        ExchangeService exchangeService = new ExchangeService(
            pool, exchangeRepository, currencyRepository, transferRepository, userRepository, notificationService);
        PaymentDepositRepository paymentDepositRepository = new PaymentDepositRepository();
        PaymentDepositService paymentDepositService = new PaymentDepositService(
            pool, paymentDepositRepository, currencyRepository, transferRepository, notificationService);
        TokenDepositRepository tokenDepositRepository = new TokenDepositRepository();
        TokenDepositService tokenDepositService = new TokenDepositService(
            pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, eventPublisher, notificationService, walletRepository, appConfigRepository);
        WithdrawalBridgeClient withdrawalBridgeClient = isKorionWithdrawalBridgeEnabled()
            ? KorionWithdrawalBridgeClient.fromEnv(webClient)
            : null;
        transferService = new TransferService(
            pool,
            transferRepository,
            userRepository,
            currencyRepository,
            walletRepository,
            eventPublisher,
            redisApi,
            notificationService,
            airdropRepository,
            appConfigRepository,
            tokenDepositRepository,
            paymentDepositRepository,
            swapRepository,
            exchangeRepository,
            withdrawalBridgeClient
        );
        referralService = new ReferralService(
            pool, referralRepository, userRepository, emailVerificationRepository, transferService,
            referralRevenueTierRepository, airdropRepository, notificationService, subscriptionService);
        miningService = new MiningService(
            pool, miningRepository, userRepository, bonusService, bonusRepository, walletRepository,
            referralService, transferRepository, currencyRepository, emailVerificationRepository, levelService,
            notificationService, subscriptionService);
        String depositScannerApiKey = System.getenv("DEPOSIT_SCANNER_API_KEY");
        if (depositScannerApiKey == null || depositScannerApiKey.isEmpty()) {
            depositScannerApiKey = config().getString("depositScanner.apiKey");
        }
        String internalWithdrawalApiKey = resolveInternalWithdrawalApiKey(
            System.getenv("KORION_WITHDRAW_CALLBACK_API_KEY"),
            depositScannerApiKey
        );
        InternalDepositHandler internalDepositHandler = new InternalDepositHandler(
            vertx, pool, walletRepository, tokenDepositService, depositScannerApiKey);
        InternalWithdrawalHandler internalWithdrawalHandler = new InternalWithdrawalHandler(
            vertx, transferService, internalWithdrawalApiKey);
        String internalOfflinePayApiKey = System.getenv("OFFLINE_PAY_INTERNAL_API_KEY");
        if (internalOfflinePayApiKey == null || internalOfflinePayApiKey.isEmpty()) {
            internalOfflinePayApiKey = depositScannerApiKey;
        }
        InternalOfflinePayHandler internalOfflinePayHandler = new InternalOfflinePayHandler(
            vertx, transferService, internalOfflinePayApiKey);
        InternalConfigHandler internalConfigHandler = new InternalConfigHandler(
            vertx, pool, appConfigRepository, depositScannerApiKey);
        InternalWalletHandler internalWalletHandler = new InternalWalletHandler(
            vertx, walletService, depositScannerApiKey);
        
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
            redisApi, walletRepository, virtualWalletMappingRepository, transferRepository, bonusRepository, miningRepository, missionRepository,
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
        
        boolean backgroundJobsEnabled = parseBooleanEnv("BACKGROUND_JOBS_ENABLED", true);
        if (backgroundJobsEnabled) {
            if (redisApi != null && retryQueuePublisher != null) {
                startEmailRetryProcessor(emailService, retryQueuePublisher, pool);
                startFcmRetryProcessor(fcmService, retryQueuePublisher, appConfigRepository, pool);
                startExchangeRateRetryProcessor(currencyService, retryQueuePublisher, appConfigRepository, pool);
                startBulkNotificationProcessor(notificationService, retryQueuePublisher, appConfigRepository, pool);
            }

            startMiningSettlementBatch(miningService, pool);

            // Exchange rate refresh scheduler: external providers -> DB(upsert). API reads from DB only.
            startExchangeRateRefreshScheduler(currencyService, retryQueuePublisher, appConfigRepository, pool);
            startCoinPriceRefreshScheduler(currencyService, pool);
            startCountryCodeSyncOnce(countryCodeService, pool);

            boolean legacyWithdrawalRedispatchEnabled = parseBooleanEnv("LEGACY_WITHDRAWAL_REDISPATCH_ENABLED", false);
            boolean legacyWaitingWithdrawalPromotionEnabled = parseBooleanEnv("LEGACY_WAITING_WITHDRAWAL_PROMOTION_ENABLED", false);
            if (legacyWithdrawalRedispatchEnabled) {
                startWithdrawalRedispatchScheduler(transferService, pool);
            } else {
                log.info("Legacy withdrawal redispatch scheduler disabled for this API instance");
            }
            if (legacyWaitingWithdrawalPromotionEnabled) {
                startWaitingWithdrawalPromotionScheduler(transferService, pool);
            } else {
                log.info("Legacy waiting withdrawal promotion scheduler disabled for this API instance");
            }
            startLevelSyncScheduler(levelService, pool);
            startImportantNoticeDispatchScheduler(noticeNotificationDispatchService, pool);
            startDbAlertMonitor(dbAlertMonitorService, pool);
            log.info("Background jobs enabled for this API instance");
        } else {
            log.info("Background jobs disabled for this API instance");
        }

        // Normalized comment.
        AuthHandler authHandler = new AuthHandler(vertx, authService, countryCodeService, jwtAuth);
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
        AdminNotificationHandler adminNotificationHandler = new AdminNotificationHandler(vertx, notificationService, retryQueuePublisher, jwtAuth);
        AdminWalletOpsHandler adminWalletOpsHandler = new AdminWalletOpsHandler(vertx, webClient, jwtAuth);
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
        mainRouter.mountSubRouter("/api/v2/admin/wallet-ops", adminWalletOpsHandler.getRouter());
        
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
        mainRouter.mountSubRouter("/api/v1/internal/offline-pay", internalOfflinePayHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/internal/config", internalConfigHandler.getRouter());
        mainRouter.mountSubRouter("/api/v1/internal/wallets", internalWalletHandler.getRouter());
        
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

    static String resolveInternalWithdrawalApiKey(String callbackApiKey, String depositScannerApiKey) {
        if (callbackApiKey != null && !callbackApiKey.isBlank()) {
            return callbackApiKey;
        }
        return depositScannerApiKey;
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
    private void startEmailRetryProcessor(EmailService emailService,
                                         RetryQueuePublisher retryQueuePublisher,
                                         PgPool pool) {
        long intervalMs = 15_000;
        AtomicBoolean running = new AtomicBoolean(false);
        vertx.setPeriodic(intervalMs, id -> {
            runExclusiveBackgroundTask(pool, LOCK_EMAIL_RETRY_PROCESSOR, "Email retry processor", running, () ->
                retryQueuePublisher.popEmailRetry()
                    .compose(job -> {
                        if (job == null) {
                            return Future.succeededFuture();
                        }
                        return emailService.processRetryJob(job)
                            .onSuccess(v -> log.debug("Email retry succeeded. type={}, email={}", job.getType(), job.getEmail()))
                            .recover(throwable -> {
                                EmailRetryJob next = job.withIncrementedRetry();
                                if (next.getRetryCount() >= job.getMaxRetries()) {
                                    return retryQueuePublisher.enqueueEmailDlq(job, throwable.getMessage());
                                }
                                return retryQueuePublisher.enqueueEmailRetry(next);
                            });
                    }));
        });
        log.info("Email retry processor started (interval {} ms)", intervalMs);
    }

    private void startFcmRetryProcessor(FcmService fcmService,
                                        RetryQueuePublisher retryQueuePublisher,
                                        AppConfigRepository appConfigRepository,
                                        PgPool pool) {
        if (fcmService == null || retryQueuePublisher == null) {
            return;
        }
        if (!fcmService.isEnabled()) {
            log.info("FCM retry processor skipped because FCM is disabled.");
            return;
        }

        int defaultIntervalMs = (int) Math.max(1_000L, Math.min(parseLongEnv("FCM_RETRY_PROCESSOR_MS", 5_000L), 60_000L));
        int defaultBatchSize = (int) Math.max(1L, Math.min(parseLongEnv("FCM_RETRY_BATCH", 20L), 100L));
        int defaultBlockMs = (int) Math.max(100L, Math.min(parseLongEnv("FCM_RETRY_BLOCK_MS", 700L), 5000L));
        String defaultConsumerGroup = System.getenv("FCM_RETRY_CONSUMER_GROUP");
        if (defaultConsumerGroup == null || defaultConsumerGroup.isBlank()) {
            defaultConsumerGroup = "fcm-retry-group";
        }
        String consumerName = System.getenv("FCM_RETRY_CONSUMER_NAME");
        if (consumerName == null || consumerName.isBlank()) {
            consumerName = "api-" + UUID.randomUUID();
        }
        String configuredConsumerName = consumerName;

        Future<Integer> intervalFuture = resolveIntConfig(appConfigRepository, pool, CFG_FCM_RETRY_PROCESSOR_MS, defaultIntervalMs, 1000, 60000);
        Future<Integer> batchFuture = resolveIntConfig(appConfigRepository, pool, CFG_FCM_RETRY_BATCH, defaultBatchSize, 1, 100);
        Future<Integer> blockFuture = resolveIntConfig(appConfigRepository, pool, CFG_FCM_RETRY_BLOCK_MS, defaultBlockMs, 100, 5000);
        Future<String> groupFuture = resolveStringConfig(appConfigRepository, pool, CFG_FCM_RETRY_CONSUMER_GROUP, defaultConsumerGroup);

        Future.all(intervalFuture, batchFuture, blockFuture, groupFuture)
            .onSuccess(result -> {
                int intervalMs = (Integer) result.resultAt(0);
                int batchSize = (Integer) result.resultAt(1);
                int blockMs = (Integer) result.resultAt(2);
                String consumerGroup = (String) result.resultAt(3);

                String finalConsumerGroup = consumerGroup;
                String finalConsumerName = configuredConsumerName;
                retryQueuePublisher.ensureFcmRetryConsumerGroup(finalConsumerGroup)
                    .onSuccess(v -> log.info("FCM retry consumer group ready. group={}", finalConsumerGroup))
                    .onFailure(t -> log.warn("Failed to initialize FCM retry consumer group. group={}", finalConsumerGroup, t));

                AtomicBoolean running = new AtomicBoolean(false);
                Runnable runTask = () -> {
                    runExclusiveBackgroundTask(pool, LOCK_FCM_RETRY_PROCESSOR, "FCM retry batch processing", running, () ->
                        retryQueuePublisher.readFcmRetryBatch(finalConsumerGroup, finalConsumerName, batchSize, blockMs)
                            .compose(messages -> processFcmRetryMessages(messages, 0, finalConsumerGroup, retryQueuePublisher, fcmService)));
                };

                vertx.setTimer(3_000L, id -> runTask.run());
                vertx.setPeriodic(intervalMs, id -> runTask.run());
                log.info("FCM retry processor started (interval {} ms, batch {}, block {} ms, group {}, consumer {})",
                    intervalMs, batchSize, blockMs, consumerGroup, configuredConsumerName);
            })
            .onFailure(t -> log.warn("Failed to initialize FCM retry processor config", t));
    }

    private Future<Void> processFcmRetryMessages(List<RetryQueuePublisher.FcmRetryMessage> messages,
                                                 int index,
                                                 String consumerGroup,
                                                 RetryQueuePublisher retryQueuePublisher,
                                                 FcmService fcmService) {
        if (messages == null || index >= messages.size()) {
            return Future.succeededFuture();
        }
        RetryQueuePublisher.FcmRetryMessage message = messages.get(index);
        if (message == null || message.job() == null || message.messageId() == null || message.messageId().isBlank()) {
            return processFcmRetryMessages(messages, index + 1, consumerGroup, retryQueuePublisher, fcmService);
        }

        return fcmService.processRetryJob(message.job())
            .compose(v -> retryQueuePublisher.ackFcmRetry(consumerGroup, message.messageId()))
            .recover(throwable -> {
                log.warn("FCM retry failed. userId={}, retryCount={}, maxRetries={}, token={}",
                    message.job().getUserId(),
                    message.job().getRetryCount(),
                    message.job().getMaxRetries(),
                    message.job().getToken() == null ? "(blank)" : message.job().getToken().substring(0, Math.min(20, message.job().getToken().length())) + "...",
                    throwable);

                FcmRetryJob next = message.job().withIncrementedRetry();
                Future<Void> nextAction = next.getRetryCount() >= message.job().getMaxRetries()
                    ? retryQueuePublisher.enqueueFcmDlq(message.job(), throwable.getMessage())
                    : retryQueuePublisher.enqueueFcmRetry(next);

                return nextAction.compose(v -> retryQueuePublisher.ackFcmRetry(consumerGroup, message.messageId()));
            })
            .compose(v -> processFcmRetryMessages(messages, index + 1, consumerGroup, retryQueuePublisher, fcmService));
    }

    private void startBulkNotificationProcessor(NotificationService notificationService,
                                                RetryQueuePublisher retryQueuePublisher,
                                                AppConfigRepository appConfigRepository,
                                                PgPool pool) {
        if (notificationService == null || retryQueuePublisher == null) {
            return;
        }

        int defaultIntervalMs = (int) Math.max(1_000L, Math.min(parseLongEnv("BULK_NOTIFICATION_PROCESSOR_MS", 2_000L), 60_000L));
        int defaultBatchSize = (int) Math.max(1L, Math.min(parseLongEnv("BULK_NOTIFICATION_BATCH", 50L), 500L));
        int defaultBlockMs = (int) Math.max(100L, Math.min(parseLongEnv("BULK_NOTIFICATION_BLOCK_MS", 700L), 5000L));
        String defaultConsumerGroup = System.getenv("BULK_NOTIFICATION_CONSUMER_GROUP");
        if (defaultConsumerGroup == null || defaultConsumerGroup.isBlank()) {
            defaultConsumerGroup = "bulk-notification-group";
        }
        String consumerName = System.getenv("BULK_NOTIFICATION_CONSUMER_NAME");
        if (consumerName == null || consumerName.isBlank()) {
            consumerName = "api-" + UUID.randomUUID();
        }
        String configuredConsumerName = consumerName;

        Future<Integer> intervalFuture = resolveIntConfig(appConfigRepository, pool, CFG_BULK_NOTIFICATION_PROCESSOR_MS, defaultIntervalMs, 1000, 60000);
        Future<Integer> batchFuture = resolveIntConfig(appConfigRepository, pool, CFG_BULK_NOTIFICATION_BATCH, defaultBatchSize, 1, 500);
        Future<Integer> blockFuture = resolveIntConfig(appConfigRepository, pool, CFG_BULK_NOTIFICATION_BLOCK_MS, defaultBlockMs, 100, 5000);
        Future<String> groupFuture = resolveStringConfig(appConfigRepository, pool, CFG_BULK_NOTIFICATION_CONSUMER_GROUP, defaultConsumerGroup);

        Future.all(intervalFuture, batchFuture, blockFuture, groupFuture)
            .onSuccess(result -> {
                int intervalMs = (Integer) result.resultAt(0);
                int batchSize = (Integer) result.resultAt(1);
                int blockMs = (Integer) result.resultAt(2);
                String consumerGroup = (String) result.resultAt(3);

                String finalConsumerGroup = consumerGroup;
                String finalConsumerName = configuredConsumerName;
                retryQueuePublisher.ensureBulkNotificationConsumerGroup(finalConsumerGroup)
                    .onSuccess(v -> log.info("Bulk-notification consumer group ready. group={}", finalConsumerGroup))
                    .onFailure(t -> log.warn("Failed to initialize bulk-notification consumer group. group={}", finalConsumerGroup, t));

                AtomicBoolean running = new AtomicBoolean(false);
                Runnable runTask = () -> {
                    runExclusiveBackgroundTask(pool, LOCK_BULK_NOTIFICATION_PROCESSOR, "Bulk-notification batch processing", running, () ->
                        retryQueuePublisher.readBulkNotificationBatch(finalConsumerGroup, finalConsumerName, batchSize, blockMs)
                            .compose(messages -> processBulkNotificationMessages(messages, 0, finalConsumerGroup, retryQueuePublisher, notificationService)));
                };

                vertx.setTimer(2_000L, id -> runTask.run());
                vertx.setPeriodic(intervalMs, id -> runTask.run());
                log.info("Bulk-notification processor started (interval {} ms, batch {}, block {} ms, group {}, consumer {})",
                    intervalMs, batchSize, blockMs, consumerGroup, configuredConsumerName);
            })
            .onFailure(t -> log.warn("Failed to initialize bulk-notification processor config", t));
    }

    private Future<Void> processBulkNotificationMessages(List<RetryQueuePublisher.BulkNotificationMessage> messages,
                                                         int index,
                                                         String consumerGroup,
                                                         RetryQueuePublisher retryQueuePublisher,
                                                         NotificationService notificationService) {
        if (messages == null || index >= messages.size()) {
            return Future.succeededFuture();
        }

        RetryQueuePublisher.BulkNotificationMessage message = messages.get(index);
        if (message == null || message.job() == null || message.messageId() == null || message.messageId().isBlank()) {
            return processBulkNotificationMessages(messages, index + 1, consumerGroup, retryQueuePublisher, notificationService);
        }

        BulkNotificationJob job = message.job();
        NotificationType type;
        try {
            type = NotificationType.valueOf(job.getType() == null ? "NOTICE" : job.getType().trim().toUpperCase());
        } catch (Exception ignored) {
            type = NotificationType.NOTICE;
        }

        return notificationService.createNotificationStrict(
                job.getUserId(),
                type,
                job.getTitle(),
                job.getMessage(),
                job.getRelatedId(),
                job.getMetadata()
            )
            .compose(v -> retryQueuePublisher.ackBulkNotification(consumerGroup, message.messageId()))
            .compose(v -> retryQueuePublisher.incrementBulkProcessed(job.getRequestId()))
            .compose(v -> retryQueuePublisher.incrementBulkSuccess(job.getRequestId()))
            .recover(throwable -> {
                log.warn("Bulk-notification processing failed. requestId={}, userId={}, retryCount={}, maxRetries={}",
                    job.getRequestId(), job.getUserId(), job.getRetryCount(), job.getMaxRetries(), throwable);

                BulkNotificationJob next = job.withIncrementedRetry();
                Future<Void> nextAction;
                if (next.getRetryCount() >= job.getMaxRetries()) {
                    nextAction = retryQueuePublisher.enqueueBulkNotificationDlq(job, throwable.getMessage())
                        .compose(v -> retryQueuePublisher.incrementBulkProcessed(job.getRequestId()))
                        .compose(v -> retryQueuePublisher.incrementBulkFailed(job.getRequestId()))
                        .compose(v -> retryQueuePublisher.incrementBulkDlq(job.getRequestId()));
                } else {
                    nextAction = retryQueuePublisher.enqueueBulkNotification(next)
                        .compose(v -> retryQueuePublisher.incrementBulkRetryScheduled(job.getRequestId()));
                }
                return nextAction.compose(v -> retryQueuePublisher.ackBulkNotification(consumerGroup, message.messageId()));
            })
            .compose(v -> processBulkNotificationMessages(messages, index + 1, consumerGroup, retryQueuePublisher, notificationService));
    }

    private void startExchangeRateRetryProcessor(CurrencyService currencyService,
                                                 RetryQueuePublisher retryQueuePublisher,
                                                 AppConfigRepository appConfigRepository,
                                                 PgPool pool) {
        if (currencyService == null || retryQueuePublisher == null) {
            return;
        }

        int defaultIntervalMs = (int) Math.max(1_000L, Math.min(parseLongEnv("EXCHANGE_RATE_RETRY_PROCESSOR_MS", 10_000L), 60_000L));
        int defaultBatchSize = (int) Math.max(1L, Math.min(parseLongEnv("EXCHANGE_RATE_RETRY_BATCH", 5L), 50L));
        int defaultBlockMs = (int) Math.max(100L, Math.min(parseLongEnv("EXCHANGE_RATE_RETRY_BLOCK_MS", 1000L), 5000L));
        String defaultConsumerGroup = System.getenv("EXCHANGE_RATE_RETRY_CONSUMER_GROUP");
        if (defaultConsumerGroup == null || defaultConsumerGroup.isBlank()) {
            defaultConsumerGroup = "exchange-rate-retry-group";
        }
        String consumerName = System.getenv("EXCHANGE_RATE_RETRY_CONSUMER_NAME");
        if (consumerName == null || consumerName.isBlank()) {
            consumerName = "api-" + UUID.randomUUID();
        }
        String configuredConsumerName = consumerName;

        Future<Integer> intervalFuture = resolveIntConfig(appConfigRepository, pool, CFG_EXCHANGE_RATE_RETRY_PROCESSOR_MS, defaultIntervalMs, 1000, 60000);
        Future<Integer> batchFuture = resolveIntConfig(appConfigRepository, pool, CFG_EXCHANGE_RATE_RETRY_BATCH, defaultBatchSize, 1, 50);
        Future<Integer> blockFuture = resolveIntConfig(appConfigRepository, pool, CFG_EXCHANGE_RATE_RETRY_BLOCK_MS, defaultBlockMs, 100, 5000);
        Future<String> groupFuture = resolveStringConfig(appConfigRepository, pool, CFG_EXCHANGE_RATE_RETRY_CONSUMER_GROUP, defaultConsumerGroup);

        Future.all(intervalFuture, batchFuture, blockFuture, groupFuture)
            .onSuccess(result -> {
                int intervalMs = (Integer) result.resultAt(0);
                int batchSize = (Integer) result.resultAt(1);
                int blockMs = (Integer) result.resultAt(2);
                String consumerGroup = (String) result.resultAt(3);

                String finalConsumerGroup = consumerGroup;
                String finalConsumerName = configuredConsumerName;
                retryQueuePublisher.ensureExchangeRateRetryConsumerGroup(finalConsumerGroup)
                    .onSuccess(v -> log.info("Exchange-rate retry consumer group ready. group={}", finalConsumerGroup))
                    .onFailure(t -> log.warn("Failed to initialize exchange-rate retry consumer group. group={}", finalConsumerGroup, t));

                AtomicBoolean running = new AtomicBoolean(false);
                Runnable runTask = () -> {
                    runExclusiveBackgroundTask(pool, LOCK_EXCHANGE_RATE_RETRY_PROCESSOR, "Exchange-rate retry batch processing", running, () ->
                        retryQueuePublisher.readExchangeRateRetryBatch(finalConsumerGroup, finalConsumerName, batchSize, blockMs)
                            .compose(messages -> processExchangeRateRetryMessages(messages, 0, finalConsumerGroup, retryQueuePublisher, currencyService)));
                };

                vertx.setTimer(4_000L, id -> runTask.run());
                vertx.setPeriodic(intervalMs, id -> runTask.run());
                log.info("Exchange-rate retry processor started (interval {} ms, batch {}, block {} ms, group {}, consumer {})",
                    intervalMs, batchSize, blockMs, consumerGroup, configuredConsumerName);
            })
            .onFailure(t -> log.warn("Failed to initialize exchange-rate retry processor config", t));
    }

    private Future<Void> processExchangeRateRetryMessages(List<RetryQueuePublisher.ExchangeRateRetryMessage> messages,
                                                          int index,
                                                          String consumerGroup,
                                                          RetryQueuePublisher retryQueuePublisher,
                                                          CurrencyService currencyService) {
        if (messages == null || index >= messages.size()) {
            return Future.succeededFuture();
        }

        RetryQueuePublisher.ExchangeRateRetryMessage message = messages.get(index);
        if (message == null || message.job() == null || message.messageId() == null || message.messageId().isBlank()) {
            return processExchangeRateRetryMessages(messages, index + 1, consumerGroup, retryQueuePublisher, currencyService);
        }

        return currencyService.refreshExchangeRates()
            .compose(v -> retryQueuePublisher.ackExchangeRateRetry(consumerGroup, message.messageId()))
            .recover(throwable -> {
                ExchangeRateRetryJob next = message.job().withIncrementedRetry();
                Future<Void> nextAction = next.getRetryCount() >= message.job().getMaxRetries()
                    ? retryQueuePublisher.enqueueExchangeRateDlq(message.job(), throwable.getMessage())
                    : retryQueuePublisher.enqueueExchangeRateRetry(next);
                return nextAction.compose(v -> retryQueuePublisher.ackExchangeRateRetry(consumerGroup, message.messageId()));
            })
            .compose(v -> processExchangeRateRetryMessages(messages, index + 1, consumerGroup, retryQueuePublisher, currencyService));
    }

    private void enqueueExchangeRateRetryIfPossible(RetryQueuePublisher retryQueuePublisher,
                                                    AppConfigRepository appConfigRepository,
                                                    PgPool pool,
                                                    String source,
                                                    Throwable error) {
        if (retryQueuePublisher == null) {
            return;
        }
        int defaultMaxRetries = (int) Math.max(1L, Math.min(parseLongEnv("EXCHANGE_RATE_RETRY_MAX_RETRIES", ExchangeRateRetryJob.DEFAULT_MAX_RETRIES), 20L));
        resolveIntConfig(appConfigRepository, pool, CFG_EXCHANGE_RATE_RETRY_MAX_RETRIES, defaultMaxRetries, 1, 20)
            .compose(maxRetries -> {
                ExchangeRateRetryJob job = ExchangeRateRetryJob.builder()
                    .source(source)
                    .retryCount(0)
                    .maxRetries(maxRetries)
                    .createdAt(LocalDateTime.now())
                    .build();
                return retryQueuePublisher.enqueueExchangeRateRetry(job);
            })
            .onFailure(t -> log.warn("Failed to enqueue exchange-rate retry. source={}, cause={}",
                source, error != null ? error.getMessage() : "unknown", t));
    }

    /**
      * Normalized comment.
      * Normalized comment.
     */
    private void startMiningSettlementBatch(MiningService miningService, PgPool pool) {
        long intervalMs = Math.max(10_000L, Math.min(parseLongEnv("MINING_SETTLEMENT_BATCH_MS", 60_000L), 3_600_000L));
        long initialDelayMs = Math.max(1_000L, Math.min(parseLongEnv("MINING_SETTLEMENT_BATCH_INITIAL_DELAY_MS", 5_000L), intervalMs));
        AtomicBoolean running = new AtomicBoolean(false);

        Runnable runTask = () -> {
            runExclusiveBackgroundTask(pool, LOCK_MINING_SETTLEMENT_BATCH, "Mining settlement batch", running, () ->
                miningService.runSettlementBatch()
                    .map(count -> {
                        if (count > 0) {
                            log.info("Mining settlement batch completed: {} users settled", count);
                        }
                        return (Void) null;
                    }));
        };

        vertx.setTimer(initialDelayMs, id -> runTask.run());
        vertx.setPeriodic(intervalMs, id -> runTask.run());
        log.info("Mining settlement batch started (interval {} ms, initialDelay {} ms)", intervalMs, initialDelayMs);
    }

    private void startCountryCodeSyncOnce(CountryCodeService countryCodeService, PgPool pool) {
        AtomicBoolean running = new AtomicBoolean(false);
        vertx.setTimer(2_000L, id -> runExclusiveBackgroundTask(pool, LOCK_COUNTRY_CODE_SYNC, "Country code startup sync", running, () ->
            countryCodeService.syncCountryCodesFromLocale()
                .map(count -> {
                    log.info("Country code sync completed on startup: {} rows", count);
                    return (Void) null;
                })));

        log.info("Country code startup sync scheduled (single-run)");
    }

    /**
     * Exchange rate refresh scheduler.
     * - Refreshes rates from external providers and upserts into DB.
     * - Clients read rates from DB only (no per-user external calls).
     */
    private void startExchangeRateRefreshScheduler(CurrencyService currencyService,
                                                   RetryQueuePublisher retryQueuePublisher,
                                                   AppConfigRepository appConfigRepository,
                                                   PgPool pool) {
        long intervalMs = parseLongEnv("EXCHANGE_RATE_REFRESH_MS", 300_000L); // 5 minutes default
        AtomicBoolean running = new AtomicBoolean(false);

        // Kick once shortly after startup.
        vertx.setTimer(1500, id -> runExclusiveBackgroundTask(pool, LOCK_EXCHANGE_RATE_REFRESH, "Exchange rate startup refresh", running, () ->
            currencyService.refreshExchangeRates()
                .onSuccess(v -> log.info("Exchange rates refreshed on startup"))
                .recover(t -> {
                    enqueueExchangeRateRetryIfPossible(retryQueuePublisher, appConfigRepository, pool, "startup-refresh", t);
                    return Future.failedFuture(t);
                })));

        vertx.setPeriodic(intervalMs, id -> runExclusiveBackgroundTask(pool, LOCK_EXCHANGE_RATE_REFRESH, "Exchange rate scheduled refresh", running, () ->
            currencyService.refreshExchangeRates()
                .recover(t -> {
                    enqueueExchangeRateRetryIfPossible(retryQueuePublisher, appConfigRepository, pool, "scheduled-refresh", t);
                    return Future.failedFuture(t);
                })));

        log.info("Exchange rate refresh scheduler started (interval {} ms)", intervalMs);
    }

    /**
     * Coin price refresh scheduler.
     * - Refreshes supported coin USD prices from external providers every minute.
     * - Persists snapshots into DB.
     * - SSE clients receive DB-backed updates after each successful refresh.
     */
    private void startCoinPriceRefreshScheduler(CurrencyService currencyService, PgPool pool) {
        long intervalMs = parseLongEnv("COIN_PRICE_REFRESH_MS", 60_000L);
        AtomicBoolean running = new AtomicBoolean(false);

        vertx.setTimer(1500, id -> runExclusiveBackgroundTask(pool, LOCK_COIN_PRICE_REFRESH, "Coin price startup refresh", running, () ->
            currencyService.refreshCoinPrices(Set.of())
                .onSuccess(v -> log.info("Coin prices refreshed on startup"))
                .recover(t -> {
                    log.warn("Coin price startup refresh failed: {}", t.getMessage());
                    return Future.failedFuture(t);
                })));

        vertx.setPeriodic(intervalMs, id -> runExclusiveBackgroundTask(pool, LOCK_COIN_PRICE_REFRESH, "Coin price scheduled refresh", running, () ->
            currencyService.refreshCoinPrices(Set.of())
                .recover(t -> {
                    log.warn("Coin price scheduled refresh failed: {}", t.getMessage());
                    return Future.failedFuture(t);
                })));

        log.info("Coin price refresh scheduler started (interval {} ms)", intervalMs);
    }

    /**
     * Periodically republishes pending withdrawals to guarantee eventual external processing.
     */
    private void startWithdrawalRedispatchScheduler(TransferService transferService, PgPool pool) {
        long intervalMs = parseLongEnv("WITHDRAWAL_REDISPATCH_MS", 15_000L);
        int batchSize = (int) Math.max(1L, Math.min(parseLongEnv("WITHDRAWAL_REDISPATCH_BATCH", 100L), 500L));
        AtomicBoolean running = new AtomicBoolean(false);

        // Kick once shortly after startup.
        vertx.setTimer(2_000L, id -> runExclusiveBackgroundTask(pool, LOCK_WITHDRAWAL_REDISPATCH, "Withdrawal redispatch startup run", running, () ->
            transferService.redispatchPendingWithdrawals(batchSize)
                .map(count -> {
                    if (count > 0) {
                        log.info("Withdrawal redispatch startup run completed: {} events republished", count);
                    }
                    return (Void) null;
                })));

        vertx.setPeriodic(intervalMs, id -> runExclusiveBackgroundTask(pool, LOCK_WITHDRAWAL_REDISPATCH, "Withdrawal redispatch scheduled run", running, () ->
            transferService.redispatchPendingWithdrawals(batchSize)
                .map(count -> {
                    if (count > 0) {
                        log.info("Withdrawal redispatch scheduled run completed: {} events republished", count);
                    }
                    return (Void) null;
                })));

        log.info("Withdrawal redispatch scheduler started (interval {} ms, batch {})", intervalMs, batchSize);
    }

    private void startWaitingWithdrawalPromotionScheduler(TransferService transferService, PgPool pool) {
        long intervalMs = parseLongEnv("WAITING_WITHDRAWAL_PROMOTION_MS", 30_000L);
        int batchSize = (int) Math.max(1L, Math.min(parseLongEnv("WAITING_WITHDRAWAL_PROMOTION_BATCH", 100L), 500L));
        AtomicBoolean running = new AtomicBoolean(false);

        vertx.setTimer(2_500L, id -> runExclusiveBackgroundTask(pool, LOCK_WAITING_WITHDRAWAL_PROMOTION, "Waiting withdrawal promotion startup run", running, () ->
            transferService.promoteWaitingWithdrawals(batchSize)
                .map(count -> {
                    if (count > 0) {
                        log.info("Waiting withdrawal promotion startup run completed: {} withdrawals promoted", count);
                    }
                    return (Void) null;
                })));

        vertx.setPeriodic(intervalMs, id -> runExclusiveBackgroundTask(pool, LOCK_WAITING_WITHDRAWAL_PROMOTION, "Waiting withdrawal promotion scheduled run", running, () ->
            transferService.promoteWaitingWithdrawals(batchSize)
                .map(count -> {
                    if (count > 0) {
                        log.info("Waiting withdrawal promotion scheduled run completed: {} withdrawals promoted", count);
                    }
                    return (Void) null;
                })));

        log.info("Waiting withdrawal promotion scheduler started (interval {} ms, batch {})", intervalMs, batchSize);
    }

    /**
     * EXP-레벨 불일치 사용자 보정 스케줄러.
     * - users.exp 기준 계산 레벨이 users.level보다 높을 때 자동 보정
     * - 레벨업 알림은 기존 dedupe 규칙(related_id=newLevel)으로 중복 방지
     */
    private void startLevelSyncScheduler(LevelService levelService, PgPool pool) {
        long intervalMs = parseLongEnv("LEVEL_SYNC_SCHEDULER_MS", 300_000L);
        int batchSize = (int) Math.max(1L, Math.min(parseLongEnv("LEVEL_SYNC_SCHEDULER_BATCH", 300L), 2000L));
        AtomicBoolean running = new AtomicBoolean(false);

        Runnable runTask = () -> {
            runExclusiveBackgroundTask(pool, LOCK_LEVEL_SYNC, "Level sync scheduled run", running, () ->
                levelService.runLevelSyncBatch(batchSize)
                    .map(updatedCount -> {
                        if (updatedCount > 0) {
                            log.info("Level sync scheduled run completed: {} users updated", updatedCount);
                        }
                        return (Void) null;
                    }));
        };

        vertx.setTimer(3_000L, id -> runTask.run());
        vertx.setPeriodic(intervalMs, id -> runTask.run());
        log.info("Level sync scheduler started (interval {} ms, batch {})", intervalMs, batchSize);
    }

    private void startImportantNoticeDispatchScheduler(NoticeNotificationDispatchService noticeDispatchService, PgPool pool) {
        long intervalMs = parseLongEnv("IMPORTANT_NOTICE_DISPATCH_MS", 30_000L);
        int noticeBatchSize = (int) Math.max(1L, Math.min(parseLongEnv("IMPORTANT_NOTICE_DISPATCH_NOTICE_BATCH", 3L), 20L));
        int userBatchSize = (int) Math.max(1L, Math.min(parseLongEnv("IMPORTANT_NOTICE_DISPATCH_USER_BATCH", 500L), 5000L));
        AtomicBoolean running = new AtomicBoolean(false);

        Runnable runTask = () -> {
            runExclusiveBackgroundTask(pool, LOCK_IMPORTANT_NOTICE_DISPATCH, "Important notice dispatch run", running, () ->
                noticeDispatchService.runImportantNoticeDispatchBatch(noticeBatchSize, userBatchSize)
                    .map(result -> {
                        if (result.getInsertedNotificationCount() > 0 || result.getScannedUserCount() > 0) {
                            log.info("Important notice dispatch run completed: jobs={}, scannedUsers={}, insertedNotifications={}",
                                result.getJobCount(), result.getScannedUserCount(), result.getInsertedNotificationCount());
                        }
                        return (Void) null;
                    }));
        };

        vertx.setTimer(4_000L, id -> runTask.run());
        vertx.setPeriodic(intervalMs, id -> runTask.run());
        log.info("Important notice dispatch scheduler started (interval {} ms, noticeBatch {}, userBatch {})",
            intervalMs, noticeBatchSize, userBatchSize);
    }

    private void startDbAlertMonitor(DbAlertMonitorService dbAlertMonitorService, PgPool pool) {
        if (dbAlertMonitorService == null || !dbAlertMonitorService.isMonitorEnabled()) {
            log.info("DB alert monitor disabled");
            return;
        }

        long intervalMs = Math.max(5_000L, Math.min(parseLongEnv("DB_ALERT_POLL_MS", 15_000L), 300_000L));
        AtomicBoolean running = new AtomicBoolean(false);
        Runnable runTask = () -> runExclusiveBackgroundTask(pool, LOCK_DB_ALERT_MONITOR, "DB alert monitor", running,
            dbAlertMonitorService::monitorOnce);

        vertx.setTimer(5_000L, id -> runTask.run());
        vertx.setPeriodic(intervalMs, id -> runTask.run());
        log.info("DB alert monitor started (interval {} ms, telegramEnabled={})",
            intervalMs, dbAlertMonitorService.isTelegramEnabled());
    }

    private Future<Integer> resolveIntConfig(AppConfigRepository appConfigRepository,
                                             PgPool pool,
                                             String key,
                                             int fallback,
                                             int min,
                                             int max) {
        if (appConfigRepository == null || pool == null || key == null || key.isBlank()) {
            return Future.succeededFuture(fallback);
        }
        return appConfigRepository.getByKey(pool, key)
            .map(value -> {
                if (value == null || value.isBlank()) {
                    return fallback;
                }
                try {
                    int parsed = Integer.parseInt(value.trim());
                    return Math.max(min, Math.min(max, parsed));
                } catch (Exception ignored) {
                    return fallback;
                }
            })
            .recover(err -> {
                log.warn("Failed to resolve app_config int. key={}, fallback={}, cause={}", key, fallback, err.getMessage());
                return Future.succeededFuture(fallback);
            });
    }

    private Future<String> resolveStringConfig(AppConfigRepository appConfigRepository,
                                               PgPool pool,
                                               String key,
                                               String fallback) {
        if (appConfigRepository == null || pool == null || key == null || key.isBlank()) {
            return Future.succeededFuture(fallback);
        }
        return appConfigRepository.getByKey(pool, key)
            .map(value -> (value == null || value.isBlank()) ? fallback : value.trim())
            .recover(err -> {
                log.warn("Failed to resolve app_config string. key={}, fallback={}, cause={}", key, fallback, err.getMessage());
                return Future.succeededFuture(fallback);
            });
    }

    private void runExclusiveBackgroundTask(PgPool pool,
                                            long lockKey,
                                            String taskName,
                                            AtomicBoolean running,
                                            Supplier<Future<Void>> taskSupplier) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executeWithAdvisoryLock(pool, lockKey, taskName, taskSupplier)
            .onFailure(t -> log.warn("{} failed", taskName, t))
            .onComplete(ar -> running.set(false));
    }

    private Future<Void> executeWithAdvisoryLock(PgPool pool,
                                                 long lockKey,
                                                 String taskName,
                                                 Supplier<Future<Void>> taskSupplier) {
        return pool.getConnection()
            .compose(connection -> connection
                .query("SELECT pg_try_advisory_lock(" + lockKey + ") AS locked")
                .execute()
                .compose(rows -> {
                    boolean locked = rows.iterator().hasNext()
                        && Boolean.TRUE.equals(rows.iterator().next().getBoolean("locked"));
                    if (!locked) {
                        return closeConnectionQuietly(connection, taskName).mapEmpty();
                    }

                    Future<Void> taskFuture;
                    try {
                        taskFuture = taskSupplier.get();
                    } catch (Throwable t) {
                        taskFuture = Future.failedFuture(t);
                    }

                    return taskFuture.transform(taskResult -> releaseAdvisoryLock(connection, lockKey, taskName)
                        .compose(v -> taskResult.succeeded()
                            ? Future.<Void>succeededFuture()
                            : Future.<Void>failedFuture(taskResult.cause())));
                })
                .recover(err -> closeConnectionQuietly(connection, taskName)
                    .compose(v -> Future.<Void>failedFuture(err))));
    }

    private Future<Void> releaseAdvisoryLock(SqlConnection connection, long lockKey, String taskName) {
        return connection.query("SELECT pg_advisory_unlock(" + lockKey + ")")
            .execute()
            .recover(err -> {
                log.warn("Failed to release advisory lock for {}", taskName, err);
                return Future.succeededFuture();
            })
            .compose(rows -> closeConnectionQuietly(connection, taskName));
    }

    private Future<Void> closeConnectionQuietly(SqlConnection connection, String taskName) {
        return connection.close()
            .recover(err -> {
                log.warn("Failed to close scheduler lock connection for {}", taskName, err);
                return Future.succeededFuture();
            });
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

    private static boolean parseBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
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

    private static boolean isKorionWithdrawalBridgeEnabled() {
        return "true".equalsIgnoreCase(System.getenv().getOrDefault("KORION_WITHDRAW_BRIDGE_ENABLED", "false"));
    }
}
