package com.foxya.coin.auth;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.utils.CountryCodeUtils;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.ConflictException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.exceptions.SocialSignupExpiredException;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.EmailService;
import com.foxya.coin.common.utils.GoogleOAuthUtil;
import com.foxya.coin.common.utils.KakaoOAuthUtil;
import com.foxya.coin.common.utils.AppleOAuthUtil;
import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.device.DeviceRepository;
import com.foxya.coin.device.entities.Device;
import com.foxya.coin.user.UserService;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.auth.dto.LoginDto;
import com.foxya.coin.auth.dto.ApiKeyDto;
import com.foxya.coin.auth.dto.LoginResponseDto;
import com.foxya.coin.auth.dto.ApiKeyResponseDto;
import com.foxya.coin.auth.dto.TokenResponseDto;
import com.foxya.coin.auth.dto.LogoutResponseDto;
import com.foxya.coin.auth.dto.DeleteAccountRequestDto;
import com.foxya.coin.auth.dto.DeleteAccountResponseDto;
import com.foxya.coin.auth.dto.FindLoginIdDataDto;
import com.foxya.coin.auth.dto.CheckEmailResponseDto;
import com.foxya.coin.auth.dto.GoogleLoginRequestDto;
import com.foxya.coin.auth.dto.RefreshResponseDto;
import com.foxya.coin.auth.dto.RegisterRequestDto;
import com.foxya.coin.auth.dto.RecoveryChallengeResponseDto;
import com.foxya.coin.auth.dto.RecoveryResetResponseDto;
import com.foxya.coin.auth.dto.GoogleLoginResponseDto;
import com.foxya.coin.auth.dto.KakaoLoginRequestDto;
import com.foxya.coin.auth.dto.KakaoLoginResponseDto;
import com.foxya.coin.auth.dto.AppleLoginRequestDto;
import com.foxya.coin.auth.dto.AppleLoginResponseDto;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.wallet.VirtualWalletMappingRepository;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.mission.MissionRepository;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.NotificationRepository;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationI18nUtils;
import com.foxya.coin.subscription.SubscriptionRepository;
import com.foxya.coin.review.ReviewRepository;
import com.foxya.coin.agency.AgencyRepository;
import com.foxya.coin.swap.SwapRepository;
import com.foxya.coin.exchange.ExchangeRepository;
import com.foxya.coin.payment.PaymentDepositRepository;
import com.foxya.coin.deposit.TokenDepositRepository;
import com.foxya.coin.airdrop.AirdropRepository;
import com.foxya.coin.inquiry.InquiryRepository;
import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.auth.RecoverySignatureVerifier;
import com.foxya.coin.referral.ReferralRepository;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class AuthService extends BaseService {
    
    private final UserRepository userRepository;
    private final UserService userService;
    private final JWTAuth jwtAuth;
    private final JsonObject jwtConfig;
    private final SocialLinkRepository socialLinkRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final RedisAPI redisApi;
    private final WalletRepository walletRepository;
    private final VirtualWalletMappingRepository virtualWalletMappingRepository;
    private final TransferRepository transferRepository;
    private final BonusRepository bonusRepository;
    private final MiningRepository miningRepository;
    private final MissionRepository missionRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ReviewRepository reviewRepository;
    private final AgencyRepository agencyRepository;
    private final SwapRepository swapRepository;
    private final ExchangeRepository exchangeRepository;
    private final PaymentDepositRepository paymentDepositRepository;
    private final TokenDepositRepository tokenDepositRepository;
    private final AirdropRepository airdropRepository;
    private final InquiryRepository inquiryRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final SignupEmailCodeRepository signupEmailCodeRepository;
    private final ReferralRepository referralRepository;
    private final DeviceRepository deviceRepository;
    private final EmailService emailService;
    private final WebClient webClient;
    private final JsonObject googleConfig;
    private final JsonObject kakaoConfig;
    private final JsonObject appleConfig;
    private final String minAppVersion;
    private final AppConfigRepository appConfigRepository;

    // Redis 키 접두사
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final String ROTATED_REFRESH_PREFIX = "token:rotated-refresh:";
    private static final String USER_TOKENS_PREFIX = "user:tokens:";
    private static final String RECOVERY_NONCE_PREFIX = "recovery:nonce:";
    private static final int RECOVERY_NONCE_TTL_SECONDS = 600;
    private static final String KAKAO_OAUTH_CACHE_PREFIX = "oauth:kakao:code:";
    private static final String KAKAO_OAUTH_LOCK_PREFIX = "oauth:kakao:lock:";
    private static final int KAKAO_OAUTH_CACHE_TTL_SECONDS = 60;
    private static final int KAKAO_OAUTH_LOCK_TTL_SECONDS = 10;
    private static final String APPLE_SIGNUP_NOTICE_TITLE = "애플 가입 안내";
    private static final String APPLE_SIGNUP_NOTICE_MESSAGE = "애플 가입시 기본정보로 가입 됐습니다. 설정>내정보관리 에서 수정해주세요.";
    private static final int REFERRAL_REGISTER_AIRDROP_PHASE = 1;
    private static final java.math.BigDecimal REFERRAL_REGISTER_AIRDROP_AMOUNT = new java.math.BigDecimal("2");
    private static final int REFERRAL_REGISTER_AIRDROP_UNLOCK_DAYS = 7;
    private static final String REFERRAL_REGISTER_AIRDROP_NOTICE_TITLE = "Referral Registration Completed";
    private static final String REFERRAL_REGISTER_AIRDROP_NOTICE_MESSAGE = "Airdrop granted for referral registration.";
    private static final String REFERRAL_REGISTER_AIRDROP_NOTICE_TITLE_KEY = "notifications.referralAirdrop.title";
    private static final String REFERRAL_REGISTER_AIRDROP_NOTICE_MESSAGE_KEY = "notifications.referralAirdrop.message";
    private static final String TEAM_MEMBER_JOINED_NOTICE_TITLE = "팀원 증가";
    private static final String TEAM_MEMBER_JOINED_NOTICE_MESSAGE = "추천인 등록으로 팀원이 추가되었습니다.";
    private static final String TEAM_MEMBER_JOINED_NOTICE_TITLE_KEY = "notifications.teamMemberJoined.title";
    private static final String TEAM_MEMBER_JOINED_NOTICE_MESSAGE_KEY = "notifications.teamMemberJoined.message";
    private static final String NEW_DEVICE_LOGIN_NOTICE_TITLE = "새 기기 로그인 감지";
    private static final String NEW_DEVICE_LOGIN_NOTICE_MESSAGE = "새로운 기기에서 계정 로그인이 감지되었습니다.";
    private static final String NEW_DEVICE_LOGIN_NOTICE_TITLE_KEY = "notifications.newDeviceLogin.title";
    private static final String NEW_DEVICE_LOGIN_NOTICE_MESSAGE_KEY = "notifications.newDeviceLogin.message";
    
    public AuthService(PgPool pool, UserRepository userRepository, UserService userService, JWTAuth jwtAuth, JsonObject jwtConfig,
                      SocialLinkRepository socialLinkRepository, PhoneVerificationRepository phoneVerificationRepository,
                      RedisAPI redisApi, WalletRepository walletRepository, VirtualWalletMappingRepository virtualWalletMappingRepository, TransferRepository transferRepository,
                      BonusRepository bonusRepository, MiningRepository miningRepository, MissionRepository missionRepository,
                      NotificationService notificationService, NotificationRepository notificationRepository, SubscriptionRepository subscriptionRepository,
                      ReviewRepository reviewRepository, AgencyRepository agencyRepository, SwapRepository swapRepository,
                      ExchangeRepository exchangeRepository, PaymentDepositRepository paymentDepositRepository,
                      TokenDepositRepository tokenDepositRepository, AirdropRepository airdropRepository,
                      InquiryRepository inquiryRepository, EmailVerificationRepository emailVerificationRepository,
                      SignupEmailCodeRepository signupEmailCodeRepository, DeviceRepository deviceRepository,
                      ReferralRepository referralRepository, EmailService emailService, WebClient webClient, JsonObject googleConfig,
                      JsonObject kakaoConfig, JsonObject appleConfig,
                      String minAppVersion, AppConfigRepository appConfigRepository) {
        super(pool);
        this.minAppVersion = minAppVersion;
        this.appConfigRepository = appConfigRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
        this.socialLinkRepository = socialLinkRepository;
        this.phoneVerificationRepository = phoneVerificationRepository;
        this.redisApi = redisApi;
        this.walletRepository = walletRepository;
        this.virtualWalletMappingRepository = virtualWalletMappingRepository;
        this.transferRepository = transferRepository;
        this.bonusRepository = bonusRepository;
        this.miningRepository = miningRepository;
        this.missionRepository = missionRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.reviewRepository = reviewRepository;
        this.agencyRepository = agencyRepository;
        this.swapRepository = swapRepository;
        this.exchangeRepository = exchangeRepository;
        this.paymentDepositRepository = paymentDepositRepository;
        this.tokenDepositRepository = tokenDepositRepository;
        this.airdropRepository = airdropRepository;
        this.inquiryRepository = inquiryRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.signupEmailCodeRepository = signupEmailCodeRepository;
        this.deviceRepository = deviceRepository;
        this.referralRepository = referralRepository;
        this.emailService = emailService;
        this.webClient = webClient;
        this.googleConfig = googleConfig;
        this.kakaoConfig = kakaoConfig;
        this.appleConfig = appleConfig;
    }

    private static Boolean toRestrictionFlag(Integer value) {
        return value != null && value == 1;
    }
    
    /**
     * 로그인
     */
    public Future<LoginResponseDto> login(LoginDto dto) {
        return userRepository.getUserByLoginId(pool, dto.getLoginId())
            .compose(user -> {
                if (user == null) {
                    return userRepository.getUserByLoginIdIncludingDeleted(pool, dto.getLoginId())
                        .compose(deletedUser -> {
                            if (deletedUser != null && deletedUser.getDeletedAt() != null) {
                                return Future.failedFuture(new UnauthorizedException("탈퇴한 계정입니다."));
                            }
                            return Future.failedFuture(new UnauthorizedException("사용자를 찾을 수 없습니다."));
                        });
                }
                if (isAccountBlocked(user)) {
                    return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
                }
                
                // 비밀번호 검증: 소셜 전용(비밀번호 미설정)은 빈 문자열로 로그인 허용
                String pw = dto.getPassword();
                boolean pwBlank = pw == null || pw.isBlank();
                if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                    if (!pwBlank) {
                        return Future.failedFuture(new UnauthorizedException("아이디 또는 비밀번호가 일치하지 않습니다."));
                    }
                    // 소셜 전용: password "" 허용, 통과
                } else {
                    if (pwBlank || !BCrypt.checkpw(pw, user.getPasswordHash())) {
                        return Future.failedFuture(new UnauthorizedException("아이디 또는 비밀번호가 일치하지 않습니다."));
                    }
                }
                
                return maybeRegisterDevice(user.getId(), dto.getDeviceId(), dto.getDeviceType(), dto.getDeviceOs(), dto.getAppVersion(), dto.getClientIp(), dto.getUserAgent())
                    .compose(ignored -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                        .compose(dbMin -> {
                            String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                            String accessToken = AuthUtils.generateAccessToken(jwtAuth, user.getId(), UserRole.USER, getAccessTokenExpireSeconds());
                            String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, user.getId(), UserRole.USER, (int) getRefreshTokenExpireSeconds());
                            return Future.succeededFuture(
                                LoginResponseDto.builder()
                                    .accessToken(accessToken)
                                    .refreshToken(refreshToken)
                                    .userId(user.getId())
                                    .loginId(user.getLoginId())
                                    .isTest(user.getIsTest())
                                    .minAppVersion(min)
                                    .warning(toRestrictionFlag(user.getIsWarning()))
                                    .miningSuspended(toRestrictionFlag(user.getIsMiningSuspended()))
                                    .accountBlocked(toRestrictionFlag(user.getIsAccountBlocked()))
                                    .build()
                            );
                        }));
            });
    }

    /**
     * 디바이스 정보가 모두 있으면 등록/갱신, 없으면 스킵 (회원가입 시 선택 전송용).
     */
    private Future<Void> maybeRegisterDevice(Long userId, String deviceId, String deviceType, String deviceOs, String appVersion, String clientIp, String userAgent) {
        if (deviceId == null || deviceId.isBlank() || deviceType == null || deviceType.isBlank() || deviceOs == null || deviceOs.isBlank()) {
            return Future.succeededFuture();
        }
        return registerOrUpdateDevice(userId, deviceId, deviceType, deviceOs, appVersion, clientIp, userAgent);
    }

    private Future<Void> registerOrUpdateDevice(Long userId, String deviceId, String deviceType, String deviceOs, String appVersion, String clientIp, String userAgent) {
        if (deviceId == null || deviceId.isBlank() || deviceType == null || deviceType.isBlank() || deviceOs == null || deviceOs.isBlank()) {
            return Future.failedFuture(new BadRequestException("디바이스 정보가 필요합니다."));
        }
        String normalizedType = deviceType.toUpperCase();
        String normalizedOs = deviceOs.toUpperCase();
        String slotType = "WEB".equals(normalizedType) ? "WEB" : normalizedOs;
        LocalDateTime now = LocalDateTime.now();
        Future<Device> existingFuture = "WEB".equals(slotType)
            ? deviceRepository.getActiveDeviceByUserAndType(pool, userId, slotType)
            : deviceRepository.getActiveDeviceByUserAndDeviceOs(pool, userId, slotType);
        return existingFuture
            .compose(existing -> {
                if (existing == null) {
                    Device device = Device.builder()
                        .userId(userId)
                        .deviceId(deviceId)
                        .deviceType(slotType)
                        .deviceOs(normalizedOs)
                        .appVersion(appVersion)
                        .userAgent(userAgent)
                        .lastIp(clientIp)
                        .lastLoginAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                    return deviceRepository.createDevice(pool, device).mapEmpty();
                }
                if (!deviceId.equals(existing.getDeviceId())) {
                    return deviceRepository.softDeleteDeviceByUserAndDeviceId(pool, userId, existing.getDeviceId())
                        .compose(v -> {
                            Device device = Device.builder()
                                .userId(userId)
                                .deviceId(deviceId)
                                .deviceType(slotType)
                                .deviceOs(normalizedOs)
                                .appVersion(appVersion)
                                .userAgent(userAgent)
                                .lastIp(clientIp)
                                .lastLoginAt(now)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                            return deviceRepository.createDevice(pool, device).mapEmpty();
                        })
                        .compose(v -> createNewDeviceLoginNotice(userId, existing.getDeviceId(), deviceId, slotType));
                }
                return deviceRepository.updateDeviceLogin(pool, existing.getId(), normalizedOs, appVersion, userAgent, clientIp, now);
            });
    }

    /**
     * 시드 구문 로그인 (챌린지 서명 기반)
     */
    public Future<LoginResponseDto> loginWithSeed(com.foxya.coin.auth.dto.LoginWithSeedRequestDto dto) {
        if (dto == null || dto.getAddress() == null || dto.getAddress().isBlank()) {
            return Future.failedFuture(new BadRequestException("지갑 주소를 입력해주세요."));
        }
        if (dto.getSignature() == null || dto.getSignature().isBlank()) {
            return Future.failedFuture(new BadRequestException("서명 값이 필요합니다."));
        }
        String normalizedChain = normalizeRecoveryChain(dto.getChain());
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        String normalizedAddress = normalizeAddress(normalizedChain, dto.getAddress());
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("복구 기능을 사용할 수 없습니다."));
        }
        String key = buildRecoveryKey(normalizedChain, normalizedAddress);
        return redisApi.get(key)
            .compose(res -> {
                if (res == null || res.toString() == null || res.toString().isBlank()) {
                    return Future.failedFuture(new BadRequestException("복구 요청이 만료되었습니다. 다시 시도해주세요."));
                }
                String nonce = res.toString();
                String message = buildRecoveryMessage(normalizedChain, normalizedAddress, nonce);
                boolean verified = verifyRecoverySignature(normalizedChain, message, dto.getSignature(), normalizedAddress);
                if (!verified) {
                    return Future.failedFuture(new BadRequestException("서명 검증에 실패했습니다."));
                }
                return findWalletForRecovery(normalizedChain, normalizedAddress)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("해당 주소의 지갑을 찾을 수 없습니다."));
                        }
                        return userRepository.getUserByIdNotDeleted(pool, wallet.getUserId())
                            .compose(user -> {
                                if (user == null) {
                                    return Future.failedFuture(new UnauthorizedException("사용자를 찾을 수 없습니다."));
                                }
                                if (isAccountBlocked(user)) {
                                    return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
                                }
                                return registerOrUpdateDevice(user.getId(), dto.getDeviceId(), dto.getDeviceType(), dto.getDeviceOs(), dto.getAppVersion(), dto.getClientIp(), dto.getUserAgent())
                                    .compose(ignored -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                                        .compose(dbMin -> {
                                            String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                                            String accessToken = AuthUtils.generateAccessToken(jwtAuth, user.getId(), UserRole.USER, getAccessTokenExpireSeconds());
                                            String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, user.getId(), UserRole.USER, (int) getRefreshTokenExpireSeconds());
                                            return redisApi.del(java.util.List.of(key))
                                                .recover(e -> Future.succeededFuture())
                                                .map(v -> LoginResponseDto.builder()
                                                    .accessToken(accessToken)
                                                    .refreshToken(refreshToken)
                                                    .userId(user.getId())
                                                    .loginId(user.getLoginId())
                                                    .isTest(user.getIsTest())
                                                    .minAppVersion(min)
                                                    .warning(toRestrictionFlag(user.getIsWarning()))
                                                    .miningSuspended(toRestrictionFlag(user.getIsMiningSuspended()))
                                                    .accountBlocked(toRestrictionFlag(user.getIsAccountBlocked()))
                                                    .build());
                                        }));
                            });
                    });
            });
    }

    /**
     * 아이디 찾기 (가입 이메일로 로그인 아이디 조회)
     * loginId의 3·4번째 자리 2글자를 *로 마스킹하여 반환.
     */
    public Future<FindLoginIdDataDto> findLoginId(String email) {
        return userRepository.getUserByEmailForFindLoginId(pool, email)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("등록된 이메일과 일치하는 계정을 찾을 수 없습니다."));
                }
                String masked = maskLoginId(user.getLoginId());
                return Future.succeededFuture(FindLoginIdDataDto.builder().maskedLoginId(masked).build());
            });
    }

    /** 계정 차단 여부 (is_account_blocked = 1). V41/coin_system V46 */
    private static boolean isAccountBlocked(User user) {
        return user != null && user.getIsAccountBlocked() != null && user.getIsAccountBlocked() != 0;
    }

    /** deviceOs가 iOS인지 여부. 로그인 시 min_app_version 조회에 사용 (iOS면 config_value_apple, 아니면 config_value). */
    private static boolean isIos(String deviceOs) {
        return deviceOs != null && "IOS".equalsIgnoreCase(deviceOs.trim());
    }

    /**
     * loginId 3·4번째 자리(1-based) 2글자를 *로 마스킹
     */
    private static String maskLoginId(String loginId) {
        if (loginId == null || loginId.length() < 3) {
            return loginId;
        }
        char[] c = loginId.toCharArray();
        if (c.length >= 3) c[2] = '*';
        if (c.length >= 4) c[3] = '*';
        return new String(c);
    }

    /**
     * 비밀번호 찾기 (가입 이메일로 임시 비밀번호 발송)
     * 일반 회원(login_id=email 또는 email_verifications 인증 이메일)만 대상. 소셜 전용·이메일 미등록은 조회에서 제외.
     */
    public Future<Void> sendTempPassword(String email) {
        return userRepository.getUserByEmailForSendTempPassword(pool, email)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("등록된 이메일과 일치하는 계정을 찾을 수 없습니다."));
                }
                String temp = emailService.generateTemporaryPassword();
                String hashed = BCrypt.hashpw(temp, BCrypt.gensalt(10));
                return userRepository.updateLoginPassword(pool, user.getId(), hashed)
                    .compose(v -> emailService.sendTemporaryPassword(email, temp))
                    .recover(throwable -> {
                        log.error("임시 비밀번호 이메일 발송 실패 - email: {}", email, throwable);
                        return Future.failedFuture(new BadRequestException("임시 비밀번호 발송에 실패했습니다."));
                    });
            });
    }

    /**
     * 시드 기반 계정 복구: 챌린지 발급
     */
    public Future<RecoveryChallengeResponseDto> requestRecoveryChallenge(String address, String chain) {
        if (address == null || address.isBlank()) {
            return Future.failedFuture(new BadRequestException("지갑 주소를 입력해주세요."));
        }
        String normalizedChain = normalizeRecoveryChain(chain);
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        String normalizedAddress = normalizeAddress(normalizedChain, address);
        return findWalletForRecovery(normalizedChain, normalizedAddress)
            .compose(wallet -> {
                if (wallet == null) {
                    return Future.failedFuture(new NotFoundException("해당 주소의 지갑을 찾을 수 없습니다."));
                }
                String nonce = UUID.randomUUID().toString().replace("-", "");
                String message = buildRecoveryMessage(normalizedChain, normalizedAddress, nonce);
                if (redisApi == null) {
                    return Future.failedFuture(new BadRequestException("복구 기능을 사용할 수 없습니다."));
                }
                String key = buildRecoveryKey(normalizedChain, normalizedAddress);
                return redisApi.setex(key, String.valueOf(RECOVERY_NONCE_TTL_SECONDS), nonce)
                    .map(v -> RecoveryChallengeResponseDto.builder()
                        .message(message)
                        .expiresInSeconds(RECOVERY_NONCE_TTL_SECONDS)
                        .build());
            });
    }

    /**
     * 시드 기반 계정 복구: 서명 검증 후 비밀번호 변경
     */
    public Future<RecoveryResetResponseDto> resetPasswordWithRecovery(String address, String chain, String signature, String newPassword) {
        if (address == null || address.isBlank()) {
            return Future.failedFuture(new BadRequestException("지갑 주소를 입력해주세요."));
        }
        if (signature == null || signature.isBlank()) {
            return Future.failedFuture(new BadRequestException("서명 값이 필요합니다."));
        }
        if (newPassword == null || newPassword.length() < 8) {
            return Future.failedFuture(new BadRequestException("비밀번호는 8자 이상이어야 합니다."));
        }
        String normalizedChain = normalizeRecoveryChain(chain);
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        String normalizedAddress = normalizeAddress(normalizedChain, address);
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("복구 기능을 사용할 수 없습니다."));
        }
        String key = buildRecoveryKey(normalizedChain, normalizedAddress);
        return redisApi.get(key)
            .compose(res -> {
                if (res == null || res.toString() == null || res.toString().isBlank()) {
                    return Future.failedFuture(new BadRequestException("복구 요청이 만료되었습니다. 다시 시도해주세요."));
                }
                String nonce = res.toString();
                String message = buildRecoveryMessage(normalizedChain, normalizedAddress, nonce);
                boolean verified = verifyRecoverySignature(normalizedChain, message, signature, normalizedAddress);
                if (!verified) {
                    return Future.failedFuture(new BadRequestException("서명 검증에 실패했습니다."));
                }
                return findWalletForRecovery(normalizedChain, normalizedAddress)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("해당 주소의 지갑을 찾을 수 없습니다."));
                        }
                        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(10));
                        return userRepository.updateLoginPassword(pool, wallet.getUserId(), hashed)
                            .compose(v -> redisApi.del(List.of(key)))
                            .map(v -> RecoveryResetResponseDto.builder()
                                .status("OK")
                                .message("비밀번호가 변경되었습니다.")
                                .build());
                    });
            });
    }

    private String normalizeRecoveryChain(String chain) {
        if (chain == null) {
            return null;
        }
        String normalized = chain.trim().toUpperCase();
        return switch (normalized) {
            case "ETH", "TRON", "BTC" -> normalized;
            default -> null;
        };
    }

    private String normalizeAddress(String chain, String address) {
        if ("ETH".equals(chain)) {
            return address.trim().toLowerCase();
        }
        return address.trim();
    }

    private String buildRecoveryKey(String chain, String address) {
        return RECOVERY_NONCE_PREFIX + chain + ":" + address;
    }

    private String buildRecoveryMessage(String chain, String address, String nonce) {
        return "FOXya Coin Recovery\nChain: " + chain + "\nAddress: " + address + "\nNonce: " + nonce;
    }

    private Future<com.foxya.coin.wallet.entities.Wallet> findWalletForRecovery(String chain, String address) {
        if ("ETH".equals(chain)) {
            return transferRepository.getWalletByAddressIgnoreCase(pool, address);
        }
        if ("TRON".equals(chain) && virtualWalletMappingRepository != null) {
            return virtualWalletMappingRepository.findByOwnerAddressAndNetwork(pool, address, chain)
                .compose(mapping -> {
                    if (mapping == null || mapping.getVirtualAddress() == null || mapping.getVirtualAddress().isBlank()) {
                        return transferRepository.getWalletByAddress(pool, address);
                    }
                    return transferRepository.getWalletByAddress(pool, mapping.getVirtualAddress())
                        .compose(wallet -> wallet != null
                            ? Future.succeededFuture(wallet)
                            : transferRepository.getWalletByAddress(pool, address));
                });
        }
        return transferRepository.getWalletByAddress(pool, address);
    }

    private boolean verifyRecoverySignature(String chain, String message, String signature, String address) {
        String sig = signature.trim();
        if ("ETH".equals(chain)) {
            return RecoverySignatureVerifier.verifyEthSignature(message, sig, address);
        }
        if ("TRON".equals(chain)) {
            return RecoverySignatureVerifier.verifyTronSignature(message, sig, address);
        }
        if ("BTC".equals(chain)) {
            return RecoverySignatureVerifier.verifyBtcSignature(message, sig, address);
        }
        return false;
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
        java.util.regex.Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final java.util.regex.Pattern NICKNAME_PATTERN =
        java.util.regex.Pattern.compile("^[가-힣a-zA-Z0-9]{1,8}$");
    /**
     * 이메일(아이디) 중복 검사 (비인증). available=true면 가입 가능.
     */
    public Future<CheckEmailResponseDto> checkEmailAvailable(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return Future.succeededFuture(CheckEmailResponseDto.builder()
                .available(false)
                .status("INVALID")
                .build());
        }
        return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
            .map(user -> {
                if (user == null) {
                    return CheckEmailResponseDto.builder().available(true).status("AVAILABLE").build();
                }
                if (user.getDeletedAt() != null) {
                    return CheckEmailResponseDto.builder().available(false).status("DELETED").build();
                }
                return CheckEmailResponseDto.builder().available(false).status("DUPLICATE").build();
            });
    }

    /**
     * 회원가입용 이메일 인증코드 발송 (비인증). 이미 가입된 이메일이면 ConflictException.
     */
    public Future<Void> sendSignupEmailCode(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return Future.failedFuture(new BadRequestException("올바른 이메일 주소를 입력해주세요."));
        }
        return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
            .compose(existing -> {
                if (existing != null) {
                    if (existing.getDeletedAt() != null) {
                        return Future.failedFuture(new ConflictException("탈퇴회원입니다"));
                    }
                    return Future.failedFuture(new ConflictException("중복회원입니다"));
                }
                String code = emailService.generateVerificationCode();
                return signupEmailCodeRepository.upsert(pool, email, code, DateUtils.now().plusMinutes(10))
                    .compose(ok -> emailService.sendVerificationCode(email, code))
                    .mapEmpty();
            });
    }

    /**
     * 회원가입용 이메일 인증 확인 (비인증).
     */
    public Future<Void> verifySignupEmail(String email, String code) {
        return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
            .compose(existing -> {
                if (existing != null) {
                    if (existing.getDeletedAt() != null) {
                        return Future.failedFuture(new ConflictException("탈퇴회원입니다"));
                    }
                    return Future.failedFuture(new ConflictException("중복회원입니다"));
                }
                return signupEmailCodeRepository.findValid(pool, email, code)
                    .compose(rec -> {
                        if (rec == null) {
                            return Future.failedFuture(new BadRequestException("인증코드가 일치하지 않거나 만료되었습니다."));
                        }
                        return Future.succeededFuture();
                    })
                    .mapEmpty();
            });
    }

    /**
     * 회원가입 (이메일 인증 완료 후). loginId=email, 프로필(nickname, name, country, gender) 저장.
     */
    public Future<LoginResponseDto> registerWithEmail(RegisterRequestDto dto) {
        String email = dto.getEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return Future.failedFuture(new BadRequestException("올바른 이메일 주소를 입력해주세요."));
        }
        if (!Boolean.TRUE.equals(dto.getSeedConfirmed())) {
            return Future.failedFuture(new BadRequestException("시드 구문 확인 후 계속 진행해주세요."));
        }
        String country = (dto.getCountry() != null && !dto.getCountry().isBlank()) ? dto.getCountry() : dto.getCountryCode();
        String normalizedCountry = CountryCodeUtils.normalizeCountryCode(country);
        if (normalizedCountry == null) {
            return Future.failedFuture(new BadRequestException("올바른 국가 코드를 입력해주세요."));
        }
        if (!CountryCodeUtils.isValidSignupCountryCode(normalizedCountry)) {
            return Future.failedFuture(new BadRequestException("올바른 국가 코드를 입력해주세요."));
        }
        String g = dto.getGender();
        if (g != null && !g.isEmpty() && !java.util.Set.of("M", "F", "O").contains(g.toUpperCase())) {
            return Future.failedFuture(new BadRequestException("gender는 M, F, O 또는 비워두세요."));
        }
        String nickname = dto.getNickname();
        if (nickname == null || !NICKNAME_PATTERN.matcher(nickname).matches()) {
            return Future.failedFuture(new BadRequestException("닉네임은 한글·영문·숫자 1~8자리로 입력해주세요."));
        }
        String code = dto.getCode();
        boolean isSocialFlow = (code == null || code.isBlank());
        String signupToken = dto.getSignupToken();
        if (isSocialFlow && (signupToken == null || signupToken.isBlank())) {
            return Future.failedFuture(new BadRequestException("소셜 가입 토큰이 필요합니다."));
        }

        AtomicReference<String> socialProviderRef = new AtomicReference<>(null);

        return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
            .compose(existingAny -> {
                if (existingAny != null && existingAny.getDeletedAt() != null) {
                    return Future.failedFuture(new ConflictException("탈퇴회원입니다"));
                }
                // 소셜 로그인 플로우: code가 빈 문자열이면 이메일 인증 단계 건너뛰기
                Future<Void> codeValidation = isSocialFlow 
                    ? Future.succeededFuture() 
                    : signupEmailCodeRepository.findValid(pool, email, code)
                        .compose(rec -> {
                            if (rec == null) {
                                return Future.failedFuture(new BadRequestException("인증코드가 일치하지 않거나 만료되었습니다."));
                            }
                            return Future.succeededFuture();
                        })
                        .mapEmpty();
                return codeValidation
                    .compose(ignored -> loadSocialSignupPayload(signupToken, isSocialFlow))
                    .compose(payload -> {
                        if (isSocialFlow) {
                            socialProviderRef.set(payload != null ? payload.getString("provider") : null);
                            String socialEmail = payload.getString("email");
                            if (socialEmail == null || !email.equalsIgnoreCase(socialEmail)) {
                                return Future.failedFuture(new BadRequestException("소셜 가입 정보가 유효하지 않습니다."));
                            }
                        }
                        return userRepository.getUserByLoginId(pool, email)
                            .compose(existing -> {
                                if (existing != null) {
                                    if (!isSocialFlow) {
                                        return Future.failedFuture(new ConflictException("중복회원입니다"));
                                    }
                                    return socialLinkRepository.hasSocialLink(pool, existing.getId())
                                        .compose(hasSocial -> {
                                            if (!hasSocial) {
                                                return Future.failedFuture(new ConflictException("중복회원입니다"));
                                            }
                                            return userRepository.existsByNicknameExcludingUser(pool, nickname, existing.getId())
                                                .compose(nickExists -> {
                                                    if (Boolean.TRUE.equals(nickExists)) {
                                                        return Future.failedFuture(new BadRequestException("이미 사용 중인 닉네임입니다."));
                                                    }
                                                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                                                    updates.put("nickname", nickname);
                                                    updates.put("name", dto.getName());
                                                    updates.put("country_code", normalizedCountry);
                                                    if (g != null && !g.isEmpty()) {
                                                        updates.put("gender", g.toUpperCase());
                                                    }
                                                    String passwordHash = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());
                                                    return userRepository.updateMeProfile(pool, existing.getId(), updates)
                                                        .compose(v -> userRepository.updateLoginPassword(pool, existing.getId(), passwordHash))
                                                        .map(v -> existing);
                                                });
                                        });
                                }
                                return userRepository.existsByNickname(pool, nickname)
                                    .compose(nickExists -> {
                                        if (Boolean.TRUE.equals(nickExists)) {
                                            return Future.failedFuture(new BadRequestException("이미 사용 중인 닉네임입니다."));
                                        }
                                        CreateUserDto create = CreateUserDto.builder()
                                            .loginId(email)
                                            .password(dto.getPassword())
                                            .nickname(nickname)
                                            .name(dto.getName())
                                            .countryCode(normalizedCountry)
                                            .gender(g != null && !g.isEmpty() ? g.toUpperCase() : null)
                                            .build();
                                        return userService.createUser(create)
                                            .compose(user -> {
                                                if (!isSocialFlow || payload == null) {
                                                    return Future.succeededFuture(user);
                                                }
                                                String provider = payload.getString("provider");
                                                String providerUserId = payload.getString("providerUserId");
                                                String socialEmail = payload.getString("email");
                                                if (provider == null || providerUserId == null || socialEmail == null) {
                                                    return Future.failedFuture(new BadRequestException("소셜 가입 정보가 유효하지 않습니다."));
                                                }
                                                return socialLinkRepository.createSocialLink(pool, user.getId(), provider, providerUserId, socialEmail)
                                                    .compose(linked -> {
                                                        if (!linked) {
                                                            return Future.failedFuture(new BadRequestException("소셜 계정 연동에 실패했습니다."));
                                                        }
                                                        return Future.succeededFuture(user);
                                                    });
                                            });
                                    });
                            });
                    })
                    .compose(user -> clearSocialSignupPayload(signupToken, isSocialFlow).map(v -> user))
                    .compose(user -> {
                        // 소셜 로그인 플로우가 아니면 code 사용 처리
                        if (!isSocialFlow) {
                            return signupEmailCodeRepository.markUsed(pool, email)
                                .map(v -> user);
                        }
                        return Future.succeededFuture(user);
                    })
                    .compose(user -> maybeRegisterDevice(user.getId(), dto.getDeviceId(), dto.getDeviceType(), dto.getDeviceOs(), dto.getAppVersion(), dto.getClientIp(), dto.getUserAgent())
                        .map(ignored -> user))
                    // 회원가입 완료 시 이메일 인증 기록 저장 (레퍼럴 코드 등록 등에서 email_verifications 조회)
                    .compose(user -> emailVerificationRepository.insertVerifiedEmail(pool, user.getId(), email)
                        .map(ignored -> user))
                    .compose(user -> maybeCreateAppleSignupNotice(user.getId(), isSocialFlow, socialProviderRef.get())
                        .map(ignored -> user))
                    .compose(user -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                        .compose(dbMin -> {
                            String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                            String accessToken = AuthUtils.generateAccessToken(jwtAuth, user.getId(), UserRole.USER, getAccessTokenExpireSeconds());
                            String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, user.getId(), UserRole.USER, (int) getRefreshTokenExpireSeconds());
                            LoginResponseDto loginDto = LoginResponseDto.builder()
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .userId(user.getId())
                                .loginId(user.getLoginId())
                                .isTest(user.getIsTest())
                                .minAppVersion(min)
                                .warning(toRestrictionFlag(user.getIsWarning()))
                                .miningSuspended(toRestrictionFlag(user.getIsMiningSuspended()))
                                .accountBlocked(toRestrictionFlag(user.getIsAccountBlocked()))
                                .build();
                            String refCode = dto.getReferralCode();
                        if (refCode != null && !refCode.isBlank()) {
                            return userRepository.getUserByReferralCodeNotDeleted(pool, refCode)
                                .compose(referrer -> {
                                    if (referrer != null && !referrer.getId().equals(user.getId())) {
                                        return referralRepository.existsReferralRelation(pool, user.getId())
                                            .compose(exists -> {
                                                if (!Boolean.TRUE.equals(exists)) {
                                                    return referralRepository.hasReferrerAnyReferredWithSameIpOrDevice(pool, referrer.getId(), dto.getClientIp(), dto.getDeviceId())
                                                        .compose(dup -> {
                                                            if (Boolean.TRUE.equals(dup)) {
                                                                return Future.succeededFuture(loginDto); // 동일 IP/기기 중복 초대 무효: 관계 생성 안 함
                                                            }
                                                            return referralRepository.createReferralRelation(pool, referrer.getId(), user.getId(), 1)
                                                                .compose(r -> grantRegisterAirdropIfFirstTime(user.getId())
                                                                    .compose(granted -> {
                                                                        Future<Void> airdropNoticeFuture = granted
                                                                            ? createReferralRegisterAirdropNotice(user.getId(), r.getId())
                                                                            : Future.succeededFuture();
                                                                        return airdropNoticeFuture.compose(x ->
                                                                            createTeamMemberJoinedNotice(r.getReferrerId(), r.getReferredId(), r.getId())
                                                                        );
                                                                    })
                                                                    .map(loginDto))
                                                                .recover(e -> { log.warn("Referral create failed", e); return Future.succeededFuture(loginDto); });
                                                        });
                                                }
                                                return Future.succeededFuture(loginDto);
                                            });
                                    }
                                    return Future.succeededFuture(loginDto);
                                })
                                .otherwise(loginDto);
                        }
                            return Future.succeededFuture(loginDto);
                        }));
            });
    }

    /**
     * 추천인 등록 보상: 사용자당 최초 1회만 Phase 1 / Amount 2 / Unlock 7일 지급
     */
    private Future<Boolean> grantRegisterAirdropIfFirstTime(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null || Boolean.TRUE.equals(user.getReferralAirdropRewarded())) {
                    return Future.succeededFuture(false);
                }
                LocalDateTime unlockDate = LocalDateTime.now().plusDays(REFERRAL_REGISTER_AIRDROP_UNLOCK_DAYS);
                return airdropRepository.createPhaseIfAbsent(
                        pool,
                        userId,
                        REFERRAL_REGISTER_AIRDROP_PHASE,
                        REFERRAL_REGISTER_AIRDROP_AMOUNT,
                        unlockDate,
                        REFERRAL_REGISTER_AIRDROP_UNLOCK_DAYS
                    )
                    .compose(v -> userRepository.updateReferralAirdropRewarded(pool, userId, true))
                    .map(v -> true);
            });
    }

    private Future<Void> createReferralRegisterAirdropNotice(Long userId, Long relationId) {
        if (notificationService == null || userId == null) {
            return Future.succeededFuture();
        }
        Long relatedId = relationId != null ? relationId : userId;
        String metadata = NotificationI18nUtils.buildMetadata(
            REFERRAL_REGISTER_AIRDROP_NOTICE_TITLE_KEY,
            REFERRAL_REGISTER_AIRDROP_NOTICE_MESSAGE_KEY,
            new JsonObject()
                .put("amount", REFERRAL_REGISTER_AIRDROP_AMOUNT.stripTrailingZeros().toPlainString())
                .put("unlockDays", REFERRAL_REGISTER_AIRDROP_UNLOCK_DAYS)
        );
        return notificationService.createNotificationIfAbsentByRelatedId(
                userId,
                NotificationType.AIRDROP_RECEIVED,
                REFERRAL_REGISTER_AIRDROP_NOTICE_TITLE,
                REFERRAL_REGISTER_AIRDROP_NOTICE_MESSAGE,
                relatedId,
                metadata
            )
            .map(v -> (Void) null)
            .recover(err -> {
                log.warn("추천인 등록 에어드랍 알림 생성 실패(무시) - userId: {}", userId, err);
                return Future.succeededFuture((Void) null);
            });
    }

    private Future<Void> createTeamMemberJoinedNotice(Long referrerId, Long referredId, Long relationId) {
        if (notificationService == null || referrerId == null) {
            return Future.succeededFuture();
        }
        Long relatedId = relationId != null ? relationId : referrerId;
        String metadata = NotificationI18nUtils.buildMetadata(
            TEAM_MEMBER_JOINED_NOTICE_TITLE_KEY,
            TEAM_MEMBER_JOINED_NOTICE_MESSAGE_KEY,
            new JsonObject().put("referredUserId", referredId)
        );
        return notificationService.createNotificationIfAbsentByRelatedId(
                referrerId,
                NotificationType.TEAM_MEMBER_JOINED,
                TEAM_MEMBER_JOINED_NOTICE_TITLE,
                TEAM_MEMBER_JOINED_NOTICE_MESSAGE,
                relatedId,
                metadata
            )
            .map(v -> (Void) null)
            .recover(err -> {
                log.warn("팀원 증가 알림 생성 실패(무시) - referrerId: {}", referrerId, err);
                return Future.succeededFuture((Void) null);
            });
    }

    private Future<Void> createNewDeviceLoginNotice(Long userId, String previousDeviceId, String currentDeviceId, String slotType) {
        if (notificationService == null || userId == null) {
            return Future.succeededFuture();
        }
        String metadata = NotificationI18nUtils.buildMetadata(
            NEW_DEVICE_LOGIN_NOTICE_TITLE_KEY,
            NEW_DEVICE_LOGIN_NOTICE_MESSAGE_KEY,
            new JsonObject()
                .put("previousDeviceId", previousDeviceId)
                .put("currentDeviceId", currentDeviceId)
                .put("slotType", slotType)
        );
        return notificationService.createNotification(
                userId,
                NotificationType.NEW_DEVICE_LOGIN,
                NEW_DEVICE_LOGIN_NOTICE_TITLE,
                NEW_DEVICE_LOGIN_NOTICE_MESSAGE,
                null,
                metadata
            )
            .map(v -> (Void) null)
            .recover(err -> {
                log.warn("새 기기 로그인 알림 생성 실패(무시) - userId: {}", userId, err);
                return Future.succeededFuture((Void) null);
            });
    }
    
    private Future<Void> maybeCreateAppleSignupNotice(Long userId, boolean isSocialFlow, String provider) {
        if (!isSocialFlow || userId == null || provider == null || !"APPLE".equalsIgnoreCase(provider)) {
            return Future.succeededFuture();
        }
        return notificationRepository.insert(
                pool,
                userId,
                NotificationType.NOTICE,
                APPLE_SIGNUP_NOTICE_TITLE,
                APPLE_SIGNUP_NOTICE_MESSAGE,
                null,
                null
            )
            .<Void>map(v -> null)
            .recover(err -> {
                log.warn("Apple 기본정보 가입 안내 알림 생성 실패(무시) - userId: {}", userId, err);
                return Future.succeededFuture((Void) null);
            });
    }

    /**
     * API Key 생성
     */
    public Future<ApiKeyResponseDto> createApiKey(Long userId, ApiKeyDto dto) {
        String apiKey = "fxc_" + UUID.randomUUID().toString().replace("-", "");
        
        // TODO: DB에 API Key 저장
        
        return Future.succeededFuture(
            ApiKeyResponseDto.builder()
                .apiKey(apiKey)
                .name(dto.getName())
                .description(dto.getDescription())
                .build()
        );
    }
    
    /**
     * Access Token 재발급
     */
    public Future<TokenResponseDto> refreshAccessToken(Long userId, String role) {
        String accessToken = AuthUtils.generateAccessToken(
            jwtAuth,
            userId,
            UserRole.valueOf(role),
            getAccessTokenExpireSeconds()
        );

        return Future.succeededFuture(
            TokenResponseDto.builder()
                .accessToken(accessToken)
                .userId(userId)
                .build()
        );
    }
    
    /**
     * Refresh Token 재발급
     */
    public Future<TokenResponseDto> refreshRefreshToken(Long userId, String role) {
        String refreshToken = AuthUtils.generateRefreshToken(
            jwtAuth,
            userId,
            UserRole.valueOf(role),
            (int) getRefreshTokenExpireSeconds()
        );

        return Future.succeededFuture(
            TokenResponseDto.builder()
                .refreshToken(refreshToken)
                .userId(userId)
                .build()
        );
    }

    private static final long SOCIAL_SIGNUP_TTL_SECONDS = 600L; // 10분
    private static final String SOCIAL_SIGNUP_PREFIX = "social_signup:";

    /** config 기반 Access Token 만료(초). access_token_expire_minutes * 60 */
    private int getAccessTokenExpireSeconds() {
        return jwtConfig.getInteger("access_token_expire_minutes", 60) * 60;
    }

    /** config 기반 Refresh Token 만료(초). refresh_token_expire_minutes * 60 */
    private long getRefreshTokenExpireSeconds() {
        return jwtConfig.getInteger("refresh_token_expire_minutes", 14400) * 60L;
    }

    /**
     * refreshToken으로 accessToken(및 refreshToken 로테이션) 재발급.
     * Authorization 헤더 없이 body의 refreshToken만으로 호출.
     * 실패 시 UnauthorizedException("Invalid or expired refresh token") → 401.
     */
    public Future<RefreshResponseDto> refresh(String refreshToken, String deviceId, String deviceType, String deviceOs) {
        // Vert.x 4.x JWTAuth.authenticate: TokenCredentials 사용 (JsonObject "jwt"/"token"은 내부 구현에 따라 동작 안 할 수 있음)
        return jwtAuth.authenticate(new TokenCredentials(refreshToken))
            .compose(user -> {
                Long userId = AuthUtils.getUserIdOf(user);
                String role = AuthUtils.getUserRoleOf(user);
                if (userId == null || role == null) {
                    return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
                }
                String tokenType = AuthUtils.getTokenTypeOf(user);
                boolean typedToken = tokenType != null && !tokenType.isBlank();
                if (typedToken && !AuthUtils.isRefreshToken(user)) {
                    return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
                }
                return isTokenBlacklisted(refreshToken)
                    .compose(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return getRotatedRefreshToken(refreshToken)
                                .compose(rotatedRefreshToken -> {
                                    if (rotatedRefreshToken != null && !rotatedRefreshToken.isBlank()) {
                                        log.info("Refresh replay recovered by rotation cache. userId={}, deviceId={}, deviceType={}, deviceOs={}",
                                            userId, toLogValue(deviceId), toLogValue(deviceType), toLogValue(deviceOs));
                                        String replayAccessToken = AuthUtils.generateAccessToken(
                                            jwtAuth,
                                            userId,
                                            UserRole.valueOf(role),
                                            getAccessTokenExpireSeconds()
                                        );
                                        return Future.succeededFuture(RefreshResponseDto.builder()
                                            .accessToken(replayAccessToken)
                                            .refreshToken(rotatedRefreshToken)
                                            .build());
                                    }
                                    log.info("Refresh rejected: blacklisted token with no rotation cache. userId={}, deviceId={}, deviceType={}, deviceOs={}",
                                        userId, toLogValue(deviceId), toLogValue(deviceType), toLogValue(deviceOs));
                                    return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
                                });
                        }
                        return ensureActiveDevice(userId, deviceId, deviceType, deviceOs)
                            .compose(ignored -> {
                                String accessToken = AuthUtils.generateAccessToken(jwtAuth, userId, UserRole.valueOf(role), getAccessTokenExpireSeconds());
                                String newRefresh = AuthUtils.generateRefreshToken(jwtAuth, userId, UserRole.valueOf(role), (int) getRefreshTokenExpireSeconds());
                                RefreshResponseDto dto = RefreshResponseDto.builder()
                                    .accessToken(accessToken)
                                    .refreshToken(newRefresh)
                                    .build();
                                // 로테이션: tokenType이 명시된 REFRESH 토큰만 블랙리스트 처리.
                                // 레거시(타입 미포함) 토큰은 access/refresh 구분이 불가해 오탐 차단을 피한다.
                                Future<Void> rotationCacheFuture = typedToken
                                    ? cacheRotatedRefreshToken(refreshToken, newRefresh)
                                    : Future.succeededFuture();
                                Future<Void> blacklistFuture = typedToken
                                    ? addToBlacklist(refreshToken, getRefreshTokenExpireSeconds())
                                    : Future.succeededFuture();
                                return rotationCacheFuture
                                    .compose(v -> blacklistFuture)
                                    .map(v -> dto);
                            });
                    });
            })
            .recover(t -> {
                if (t instanceof UnauthorizedException) return Future.failedFuture(t);
                log.warn("Refresh token verification failed: {}", t.getMessage());
                return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
            });
    }

    private Future<Void> cacheRotatedRefreshToken(String oldRefreshToken, String newRefreshToken) {
        if (redisApi == null || oldRefreshToken == null || oldRefreshToken.isBlank() || newRefreshToken == null || newRefreshToken.isBlank()) {
            return Future.succeededFuture();
        }
        String key = ROTATED_REFRESH_PREFIX + oldRefreshToken;
        long ttlSeconds = getRotatedRefreshTtlSeconds();
        return redisApi.setex(key, String.valueOf(ttlSeconds), newRefreshToken)
            .<Void>map(response -> null)
            .recover(throwable -> {
                log.warn("Failed to cache rotated refresh token: {}", throwable.getMessage());
                return Future.succeededFuture();
            });
    }

    private Future<String> getRotatedRefreshToken(String oldRefreshToken) {
        if (redisApi == null || oldRefreshToken == null || oldRefreshToken.isBlank()) {
            return Future.succeededFuture(null);
        }
        String key = ROTATED_REFRESH_PREFIX + oldRefreshToken;
        return redisApi.get(key)
            .map(response -> {
                if (response == null || response.toString() == null || response.toString().isBlank()) {
                    return null;
                }
                return response.toString();
            })
            .recover(throwable -> {
                log.warn("Failed to read rotated refresh token cache: {}", throwable.getMessage());
                return Future.succeededFuture(null);
            });
    }

    /**
     * 이전 refresh 토큰 재사용(네트워크 재시도/다중 요청) 복구용 캐시 TTL.
     * config 미설정 시 refresh token 만료시간과 동일하게 유지해
     * 클라이언트가 이전 refresh 토큰을 오래 보관한 경우에도 자동 복구를 허용한다.
     */
    private long getRotatedRefreshTtlSeconds() {
        Integer configured = jwtConfig.getInteger("rotated_refresh_ttl_seconds");
        long ttl = configured != null ? configured.longValue() : getRefreshTokenExpireSeconds();
        return Math.max(60L, ttl);
    }

    private String toLogValue(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private Future<Void> ensureActiveDevice(Long userId, String deviceId, String deviceType, String deviceOs) {
        if (userId == null) {
            return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
        }
        if (deviceId != null && !deviceId.isBlank()) {
            return deviceRepository.getActiveDeviceByUserAndDeviceId(pool, userId, deviceId)
                .compose(device -> {
                    if (device == null) {
                        return Future.failedFuture(new UnauthorizedException("다른기기에서 로그인중입니다. 이전기기를 로그아웃 시킵니다."));
                    }
                    return Future.succeededFuture();
                });
        }
        if (deviceType != null && !deviceType.isBlank()) {
            String normalizedType = deviceType.toUpperCase();
            if ("WEB".equals(normalizedType)) {
                return deviceRepository.getActiveDeviceByUserAndType(pool, userId, "WEB")
                    .compose(device -> {
                        if (device == null) {
                            return Future.failedFuture(new UnauthorizedException("다른기기에서 로그인중입니다. 이전기기를 로그아웃 시킵니다."));
                        }
                        return Future.succeededFuture();
                    });
            }
            if (deviceOs != null && !deviceOs.isBlank()) {
                String normalizedOs = deviceOs.toUpperCase();
                return deviceRepository.getActiveDeviceByUserAndDeviceOs(pool, userId, normalizedOs)
                    .compose(device -> {
                        if (device == null) {
                            return Future.failedFuture(new UnauthorizedException("다른기기에서 로그인중입니다. 이전기기를 로그아웃 시킵니다."));
                        }
                        return Future.succeededFuture();
                    });
            }
        }
        return Future.succeededFuture();
    }

    /**
     * 토큰을 블랙리스트에 지정 TTL로 추가
     */
    private Future<Void> addToBlacklist(String token, long expireSeconds) {
        if (redisApi == null) return Future.succeededFuture();
        String key = TOKEN_BLACKLIST_PREFIX + token;
        return redisApi.setex(key, String.valueOf(expireSeconds), "1")
            .<Void>map(r -> null)
            .recover(e -> {
                log.error("Failed to add token to blacklist", e);
                return Future.succeededFuture();
            });
    }

    private Future<String> createSocialSignupToken(String provider, String providerUserId, String email) {
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("소셜 가입 기능을 사용할 수 없습니다."));
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        JsonObject payload = new JsonObject()
            .put("provider", provider)
            .put("providerUserId", providerUserId)
            .put("email", email);
        return redisApi.setex(SOCIAL_SIGNUP_PREFIX + token, String.valueOf(SOCIAL_SIGNUP_TTL_SECONDS), payload.encode())
            .map(r -> token);
    }

    private Future<JsonObject> loadSocialSignupPayload(String token, boolean isSocialFlow) {
        if (!isSocialFlow) {
            return Future.succeededFuture(null);
        }
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("소셜 가입 기능을 사용할 수 없습니다."));
        }
        String key = SOCIAL_SIGNUP_PREFIX + token;
        return redisApi.get(key)
            .compose(res -> {
                if (res == null || res.toString() == null || res.toString().isBlank()) {
                    return Future.failedFuture(new SocialSignupExpiredException("소셜 가입 정보가 만료되었습니다. 스토리지를 비우고 소셜 로그인을 다시 시도해 주세요."));
                }
                return Future.succeededFuture(new JsonObject(res.toString()));
            });
    }

    private Future<Void> clearSocialSignupPayload(String token, boolean isSocialFlow) {
        if (!isSocialFlow || redisApi == null || token == null || token.isBlank()) {
            return Future.succeededFuture();
        }
        return redisApi.del(java.util.List.of(SOCIAL_SIGNUP_PREFIX + token))
            .recover(e -> Future.succeededFuture())
            .mapEmpty();
    }
    
    /**
     * API Key 재발급
     */
    public Future<ApiKeyResponseDto> reissueApiKey(Long userId) {
        String apiKey = "fxc_" + UUID.randomUUID().toString().replace("-", "");
        
        // TODO: DB에서 기존 API Key 무효화 후 새로 생성
        
        return Future.succeededFuture(
            ApiKeyResponseDto.builder()
                .apiKey(apiKey)
                .name("Reissued API Key")
                .build()
        );
    }
    
    /**
     * 로그아웃
     * @param userId 사용자 ID
     * @param token Access JWT 토큰 (블랙리스트에 추가)
     * @param refreshToken Refresh JWT 토큰 (요청 본문/쿠키로 전달되면 블랙리스트에 추가)
     * @param allDevices 모든 디바이스 로그아웃 여부
     * @return 로그아웃 응답
     */
    public Future<LogoutResponseDto> logout(Long userId, String token, String refreshToken, boolean allDevices, String deviceId, String deviceType, String deviceOs) {
        log.info("Logout request from user: {}, allDevices: {}", userId, allDevices);

        Future<Void> deviceCleanup;
        if (allDevices) {
            deviceCleanup = deviceRepository.softDeleteDevicesByUserId(pool, userId);
        } else if (deviceId != null && !deviceId.isBlank()) {
            deviceCleanup = deviceRepository.softDeleteDeviceByUserAndDeviceId(pool, userId, deviceId);
        } else if (deviceType != null && !deviceType.isBlank()) {
            String normalizedType = deviceType.toUpperCase();
            String normalizedOs = deviceOs != null ? deviceOs.toUpperCase() : null;
            if ("WEB".equals(normalizedType)) {
                deviceCleanup = deviceRepository.softDeleteDeviceByUserAndType(pool, userId, "WEB");
            } else if (normalizedOs != null && !normalizedOs.isBlank()) {
                deviceCleanup = deviceRepository.softDeleteDeviceByUserAndDeviceOs(pool, userId, normalizedOs);
            } else {
                deviceCleanup = deviceRepository.softDeleteDeviceByUserAndType(pool, userId, normalizedType);
            }
        } else {
            deviceCleanup = Future.succeededFuture();
        }

        if (redisApi == null) {
            log.warn("Redis API is not available, skipping token blacklist");
            return deviceCleanup.map(v -> LogoutResponseDto.builder()
                .status("OK")
                .message("Logged out successfully")
                .build());
        }

        List<Future<Void>> blacklistFutures = new ArrayList<>();
        if (token != null && !token.isBlank()) {
            blacklistFutures.add(addTokenToBlacklist(token));
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            blacklistFutures.add(blacklistRefreshTokenIfOwned(userId, refreshToken));
        }

        Future<Void> blacklistFuture = blacklistFutures.isEmpty()
            ? Future.succeededFuture()
            : Future.all(blacklistFutures).mapEmpty();

        Future<Void> allDevicesFuture = allDevices
            ? logoutAllDevices(userId)
            : Future.succeededFuture();

        return blacklistFuture
            .compose(v -> allDevicesFuture)
            .compose(v -> deviceCleanup)
            .map(v -> LogoutResponseDto.builder()
                .status("OK")
                .message(allDevices ? "Logged out from all devices" : "Logged out successfully")
                .build());
    }

    private Future<Void> blacklistRefreshTokenIfOwned(Long userId, String refreshToken) {
        return jwtAuth.authenticate(new TokenCredentials(refreshToken))
            .compose(user -> {
                Long refreshUserId = AuthUtils.getUserIdOf(user);
                if (refreshUserId == null || !refreshUserId.equals(userId)) {
                    return Future.succeededFuture();
                }
                String tokenType = AuthUtils.getTokenTypeOf(user);
                boolean typedToken = tokenType != null && !tokenType.isBlank();
                if (typedToken && !AuthUtils.isRefreshToken(user)) {
                    return Future.succeededFuture();
                }
                return addToBlacklist(refreshToken, getRefreshTokenExpireSeconds());
            })
            .recover(throwable -> Future.succeededFuture());
    }
    
    /**
     * Access Token을 블랙리스트에 추가 (로그아웃 시)
     */
    private Future<Void> addTokenToBlacklist(String token) {
        int accessTokenExpireMinutes = jwtConfig.getInteger("access_token_expire_minutes", 60);
        long expireSeconds = accessTokenExpireMinutes * 60L;
        return addToBlacklist(token, expireSeconds);
    }
    
    /**
     * 모든 디바이스 로그아웃
     * 사용자의 모든 토큰을 블랙리스트에 추가
     */
    private Future<Void> logoutAllDevices(Long userId) {
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        
        // 사용자의 모든 토큰 키 조회 (실제 구현에서는 사용자별 토큰을 관리해야 함)
        // 여기서는 간단하게 사용자 토큰 세트를 삭제
        return redisApi.del(List.of(userTokensKey))
            .<Void>map(response -> {
                log.info("All tokens removed for user: {}", userId);
                return null;
            })
            .recover(throwable -> {
                log.error("Failed to remove user tokens", throwable);
                return Future.succeededFuture();
            });
    }
    
    /**
     * 토큰이 블랙리스트에 있는지 확인
     * @param token JWT 토큰
     * @return 블랙리스트에 있으면 true
     */
    public Future<Boolean> isTokenBlacklisted(String token) {
        if (redisApi == null) {
            return Future.succeededFuture(false);
        }
        
        String blacklistKey = TOKEN_BLACKLIST_PREFIX + token;
        
        return redisApi.exists(List.of(blacklistKey))
            .map(response -> {
                Long count = response.toLong();
                return count != null && count > 0;
            })
            .recover(throwable -> {
                log.error("Failed to check token blacklist", throwable);
                return Future.succeededFuture(false);
            });
    }
    
    /**
     * 소셜 계정 연동
     */
    public Future<Boolean> linkSocial(Long userId, String provider, String token) {
        // TODO: 토큰 검증 및 사용자 정보 조회 (KAKAO, GOOGLE, EMAIL)
        String providerUserId = "provider_user_id_from_token"; // 실제로는 토큰에서 추출
        String email = provider.equals("EMAIL") ? token : null; // EMAIL의 경우 token이 이메일 주소
        
        // 소셜 링크 저장 전에 레퍼럴 코드 확인 및 생성 (회원가입처럼)
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new BadRequestException("사용자를 찾을 수 없습니다."));
                }
                
                // 레퍼럴 코드가 없으면 자동 생성 (회원가입과 동일한 로직)
                if (user.getReferralCode() == null || user.getReferralCode().isEmpty()) {
                    log.info("Auto-generating referral code for social link user: {}", userId);
                    return userService.generateReferralCode(userId)
                        .compose(referralCodeResponse -> {
                            // 레퍼럴 코드 생성 성공 후 소셜 링크 저장
                            return socialLinkRepository.createSocialLink(pool, userId, provider, providerUserId, email);
                        })
                        .recover(throwable -> {
                            // 레퍼럴 코드 생성 실패해도 소셜 링크 저장은 진행
                            log.warn("Failed to auto-generate referral code for user {} during social link: {}", userId, throwable.getMessage());
                            return socialLinkRepository.createSocialLink(pool, userId, provider, providerUserId, email);
                        });
                } else {
                    // 레퍼럴 코드가 이미 있으면 바로 소셜 링크 저장
                    return socialLinkRepository.createSocialLink(pool, userId, provider, providerUserId, email);
                }
            });
    }
    
    /**
     * 본인인증(휴대폰) 등록
     */
    public Future<Boolean> verifyPhone(Long userId, String phoneNumber, String verificationCode) {
        // TODO: 인증 코드 검증 로직 추가
        return phoneVerificationRepository.verifyPhone(pool, userId, phoneNumber, verificationCode);
    }
    
    /**
     * Google 로그인
     * @param dto Google 로그인 요청 DTO (Authorization Code 포함)
     * @return Google 로그인 응답 DTO
     */
    public Future<GoogleLoginResponseDto> googleLogin(GoogleLoginRequestDto dto) {
        log.info("Google login attempt with code");
        
        String platform = dto.getPlatform();
        boolean hasCodeVerifier = dto.getCodeVerifier() != null && !dto.getCodeVerifier().isEmpty();
        boolean isAndroid = "ANDROID".equalsIgnoreCase(platform);
        boolean isIos = "IOS".equalsIgnoreCase(platform);

        // 플랫폼 미지정 + PKCE면 기존 Android 처리 유지 (레거시 호환)
        if (!isAndroid && !isIos && hasCodeVerifier) {
            isAndroid = true;
        }

        String clientId = isAndroid
            ? googleConfig.getString("androidClientId", googleConfig.getString("clientId"))
            : isIos
                ? googleConfig.getString("iosClientId", googleConfig.getString("clientId"))
                : googleConfig.getString("clientId");
        String clientSecret = (isAndroid || isIos) ? null : googleConfig.getString("clientSecret");
        String redirectUri = isAndroid
            ? googleConfig.getString("androidRedirectUri", googleConfig.getString("redirectUri"))
            : isIos
                ? googleConfig.getString("iosRedirectUri", googleConfig.getString("redirectUri"))
                : googleConfig.getString("redirectUri");
        
        if (clientId == null) {
            log.error("Google OAuth configuration is missing");
            return Future.failedFuture(new BadRequestException("Google OAuth configuration is missing"));
        }
        boolean usingCode = dto.getCode() != null && !dto.getCode().isBlank();
        if (usingCode && clientSecret == null && (dto.getCodeVerifier() == null || dto.getCodeVerifier().isEmpty())) {
            log.error("Google OAuth client secret is missing");
            return Future.failedFuture(new BadRequestException("Google OAuth configuration is missing"));
        }
        
        // 1. Google OAuth 인증 (토큰 교환 + 사용자 정보 조회)
        Future<JsonObject> userInfoFuture;
        if (usingCode) {
            userInfoFuture = GoogleOAuthUtil.authenticate(webClient, dto.getCode(), clientId, clientSecret, redirectUri, dto.getCodeVerifier());
        } else {
            String expectedAud = googleConfig.getString("clientId", clientId);
            userInfoFuture = GoogleOAuthUtil.verifyIdToken(webClient, dto.getIdToken(), expectedAud);
        }

        return userInfoFuture
            .compose(userInfo -> {
                String googleId = userInfo.getString("id");
                String email = userInfo.getString("email");
                String name = userInfo.getString("name");
                String picture = userInfo.getString("picture");
                
                // 2. 기존 계정 확인 (이메일로)
                return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
                    .compose(existingUser -> {
                        if (existingUser != null && existingUser.getDeletedAt() != null) {
                            return Future.failedFuture(new BadRequestException("탈퇴한 계정입니다."));
                        }
                        if (existingUser != null) {
                            if (isAccountBlocked(existingUser)) {
                                return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
                            }
                            // 기존 사용자: 소셜 링크 연동 및 로그인
                            log.info("Existing user found: {}", email);
                            return socialLinkRepository.createSocialLink(pool, existingUser.getId(), "GOOGLE", googleId, email)
                                .compose(linked -> {
                                    if (!linked) {
                                        log.warn("Failed to link Google account, but continuing login");
                                    }
                                    
                                    return registerOrUpdateDevice(existingUser.getId(), dto.getDeviceId(), dto.getDeviceType(), dto.getDeviceOs(), dto.getAppVersion(), dto.getClientIp(), dto.getUserAgent())
                                        .compose(ignored -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                                            .map(dbMin -> {
                                                String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                                                String jwtAccessToken = AuthUtils.generateAccessToken(jwtAuth, existingUser.getId(), UserRole.USER, getAccessTokenExpireSeconds());
                                                String jwtRefreshToken = AuthUtils.generateRefreshToken(jwtAuth, existingUser.getId(), UserRole.USER, (int) getRefreshTokenExpireSeconds());
                                                return GoogleLoginResponseDto.builder()
                                                    .accessToken(jwtAccessToken)
                                                    .refreshToken(jwtRefreshToken)
                                                    .userId(existingUser.getId())
                                                    .loginId(existingUser.getLoginId())
                                                    .isNewUser(false)
                                                    .isTest(existingUser.getIsTest())
                                                    .minAppVersion(min)
                                                    .warning(toRestrictionFlag(existingUser.getIsWarning()))
                                                    .miningSuspended(toRestrictionFlag(existingUser.getIsMiningSuspended()))
                                                    .accountBlocked(toRestrictionFlag(existingUser.getIsAccountBlocked()))
                                                    .build();
                                            }));
                                });
                        } else {
                            // 신규 사용자: 계정 생성
                            log.info("New user registration via Google: {}", email);
                            return createSocialSignupToken("GOOGLE", googleId, email)
                                .compose(signupToken -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                                    .map(dbMin -> {
                                        String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                                        return GoogleLoginResponseDto.builder()
                                            .loginId(email)
                                            .isNewUser(true)
                                            .signupToken(signupToken)
                                            .minAppVersion(min)
                                            .build();
                                    }));
                        }
                    });
            })
            .recover(throwable -> {
                if (throwable instanceof UnauthorizedException) {
                    return Future.<GoogleLoginResponseDto>failedFuture((UnauthorizedException) throwable);
                }
                if (throwable instanceof BadRequestException) {
                    return Future.<GoogleLoginResponseDto>failedFuture((BadRequestException) throwable);
                }
                log.error("Google login failed", throwable);
                return Future.<GoogleLoginResponseDto>failedFuture(new BadRequestException("Google login failed: " + throwable.getMessage()));
            });
    }

    /**
     * Kakao 로그인
     * @param dto Kakao 로그인 요청 DTO (Authorization Code 포함)
     * @return Kakao 로그인 응답 DTO
     */
    public Future<KakaoLoginResponseDto> kakaoLogin(KakaoLoginRequestDto dto) {
        log.info("Kakao login attempt");

        String platform = dto.getPlatform();
        boolean isAndroid = "ANDROID".equalsIgnoreCase(platform);
        boolean isIos = "IOS".equalsIgnoreCase(platform);

        String clientId = kakaoConfig.getString("clientId");
        String clientSecret = kakaoConfig.getString("clientSecret");
        String redirectUri = isAndroid
            ? kakaoConfig.getString("androidRedirectUri", kakaoConfig.getString("redirectUri"))
            : isIos
                ? kakaoConfig.getString("iosRedirectUri", kakaoConfig.getString("redirectUri"))
                : kakaoConfig.getString("redirectUri");

        if (clientId == null || clientId.isBlank()) {
            log.error("Kakao OAuth configuration is missing");
            return Future.failedFuture(new BadRequestException("Kakao OAuth configuration is missing"));
        }

        final String authCode = dto.getCode();
        final String accessToken = dto.getAccessToken();
        final boolean hasCode = authCode != null && !authCode.isBlank();
        final boolean hasAccessToken = accessToken != null && !accessToken.isBlank();

        if (!hasCode && !hasAccessToken) {
            return Future.failedFuture(new BadRequestException("Missing code or accessToken"));
        }

        final String cacheKey = hasCode ? KAKAO_OAUTH_CACHE_PREFIX + authCode : null;
        final String lockKey = hasCode ? KAKAO_OAUTH_LOCK_PREFIX + authCode : null;

        Future<JsonObject> userInfoFuture = hasAccessToken
            ? KakaoOAuthUtil.getUserInfo(webClient, accessToken)
            : acquireKakaoLock(lockKey, cacheKey)
                .compose(ignored -> KakaoOAuthUtil.authenticate(webClient, authCode, clientId, clientSecret, redirectUri));

        Future<KakaoLoginResponseDto> loginFlow = userInfoFuture
            .compose(userInfo -> {
                String kakaoId = userInfo.getString("id");
                String email = userInfo.getString("email");

                // 2. 기존 계정 확인 (이메일로)
                return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
                    .compose(existingUser -> {
                        if (existingUser != null && existingUser.getDeletedAt() != null) {
                            return Future.failedFuture(new BadRequestException("탈퇴한 계정입니다."));
                        }
                        if (existingUser != null) {
                            if (isAccountBlocked(existingUser)) {
                                return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
                            }
                            // 기존 사용자: 소셜 링크 연동 및 로그인
                            log.info("Existing user found: {}", email);
                            return socialLinkRepository.createSocialLink(pool, existingUser.getId(), "KAKAO", kakaoId, email)
                                .compose(linked -> {
                                    if (!linked) {
                                        log.warn("Failed to link Kakao account, but continuing login");
                                    }

                                    return registerOrUpdateDevice(existingUser.getId(), dto.getDeviceId(), dto.getDeviceType(), dto.getDeviceOs(), dto.getAppVersion(), dto.getClientIp(), dto.getUserAgent())
                                        .compose(ignored -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                                            .map(dbMin -> {
                                                String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                                                String jwtAccessToken = AuthUtils.generateAccessToken(jwtAuth, existingUser.getId(), UserRole.USER, getAccessTokenExpireSeconds());
                                                String jwtRefreshToken = AuthUtils.generateRefreshToken(jwtAuth, existingUser.getId(), UserRole.USER, (int) getRefreshTokenExpireSeconds());
                                                return KakaoLoginResponseDto.builder()
                                                    .accessToken(jwtAccessToken)
                                                    .refreshToken(jwtRefreshToken)
                                                    .userId(existingUser.getId())
                                                    .loginId(existingUser.getLoginId())
                                                    .isNewUser(false)
                                                    .isTest(existingUser.getIsTest())
                                                    .minAppVersion(min)
                                                    .warning(toRestrictionFlag(existingUser.getIsWarning()))
                                                    .miningSuspended(toRestrictionFlag(existingUser.getIsMiningSuspended()))
                                                    .accountBlocked(toRestrictionFlag(existingUser.getIsAccountBlocked()))
                                                    .build();
                                            }))
                                        .compose(resp -> cacheKakaoResponse(cacheKey, resp).map(resp));
                                });
                        } else {
                            // 신규 사용자: 계정 생성
                            log.info("New user registration via Kakao: {}", email);
                            return createSocialSignupToken("KAKAO", kakaoId, email)
                                .compose(signupToken -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                                    .map(dbMin -> {
                                        String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                                        return KakaoLoginResponseDto.builder()
                                            .loginId(email)
                                            .isNewUser(true)
                                            .signupToken(signupToken)
                                            .minAppVersion(min)
                                            .build();
                                    })
                                    .compose(resp -> cacheKakaoResponse(cacheKey, resp).map(resp)));
                        }
                    });
            })
            .recover(throwable -> {
                if (throwable instanceof CachedKakaoResponseException cachedErr) {
                    return Future.succeededFuture(cachedErr.response);
                }
                // 중복 콜백으로 인한 invalid_grant는 캐시 응답이 없으면 성공/실패 여부를 알 수 없으므로
                // 클라이언트 재시도에 맡기고 에러 로그만 남긴다.
                if (throwable instanceof UnauthorizedException
                    && throwable.getMessage() != null
                    && throwable.getMessage().contains("Invalid authorization code")) {
                    log.warn("Kakao OAuth invalid_grant (likely duplicate).");
                }
                if (throwable instanceof UnauthorizedException) {
                    return Future.<KakaoLoginResponseDto>failedFuture((UnauthorizedException) throwable);
                }
                if (throwable instanceof BadRequestException) {
                    return Future.<KakaoLoginResponseDto>failedFuture((BadRequestException) throwable);
                }
                log.error("Kakao login failed", throwable);
                return Future.<KakaoLoginResponseDto>failedFuture(new BadRequestException("Kakao login failed: " + throwable.getMessage()));
            });

        if (!hasCode) {
            return loginFlow;
        }
        Future<KakaoLoginResponseDto> cachedResponse = getCachedKakaoResponse(cacheKey);
        return cachedResponse.recover(err -> loginFlow);
    }

    private Future<Void> acquireKakaoLock(String lockKey, String cacheKey) {
        if (redisApi == null || lockKey == null) {
            return Future.succeededFuture();
        }
        return redisApi
            .set(java.util.List.of(lockKey, "1", "NX", "EX", String.valueOf(KAKAO_OAUTH_LOCK_TTL_SECONDS)))
            .compose(resp -> {
                if (resp == null) {
                    return waitForCachedKakaoResponse(cacheKey, 5, 500)
                        .compose(cached -> Future.<Void>failedFuture(new CachedKakaoResponseException(cached)));
                }
                return Future.succeededFuture();
            })
            .recover(err -> {
                if (err instanceof UnauthorizedException) {
                    log.warn("Kakao OAuth cache miss after wait, proceeding to exchange.");
                    return Future.succeededFuture();
                }
                log.warn("Failed to acquire Kakao OAuth lock, proceeding without lock: {}", err.getMessage());
                return Future.succeededFuture();
            });
    }

    private Future<KakaoLoginResponseDto> getCachedKakaoResponse(String cacheKey) {
        if (redisApi == null || cacheKey == null) {
            return Future.failedFuture(new CacheMissException());
        }
        return redisApi.get(cacheKey)
            .compose(res -> {
                if (res == null) {
                    return Future.failedFuture(new CacheMissException());
                }
                try {
                    JsonObject payload = new JsonObject(res.toString());
                    KakaoLoginResponseDto dto = payload.mapTo(KakaoLoginResponseDto.class);
                    return Future.succeededFuture(dto);
                } catch (Exception e) {
                    return Future.failedFuture(e);
                }
            });
    }

    private Future<KakaoLoginResponseDto> waitForCachedKakaoResponse(String cacheKey, int attempts, long delayMs) {
        if (redisApi == null || cacheKey == null) {
            return Future.failedFuture(new UnauthorizedException("Invalid authorization code"));
        }
        return redisApi.get(cacheKey)
            .compose(res -> {
                if (res != null) {
                    try {
                        JsonObject payload = new JsonObject(res.toString());
                        KakaoLoginResponseDto dto = payload.mapTo(KakaoLoginResponseDto.class);
                        return Future.succeededFuture(dto);
                    } catch (Exception e) {
                        return Future.failedFuture(e);
                    }
                }
                if (attempts <= 0) {
                    return Future.failedFuture(new UnauthorizedException("Invalid authorization code"));
                }
                Vertx vertx = Vertx.currentContext() != null ? Vertx.currentContext().owner() : null;
                if (vertx == null) {
                    return Future.failedFuture(new UnauthorizedException("Invalid authorization code"));
                }
                Promise<KakaoLoginResponseDto> promise = Promise.promise();
                vertx.setTimer(delayMs, id -> {
                    waitForCachedKakaoResponse(cacheKey, attempts - 1, delayMs).onComplete(promise);
                });
                return promise.future();
            });
    }

    private Future<Void> cacheKakaoResponse(String cacheKey, KakaoLoginResponseDto response) {
        if (redisApi == null || cacheKey == null || response == null) {
            return Future.succeededFuture();
        }
        JsonObject payload = JsonObject.mapFrom(response);
        Future<Void> future = redisApi.setex(cacheKey, String.valueOf(KAKAO_OAUTH_CACHE_TTL_SECONDS), payload.encode())
            .mapEmpty();
        future.onFailure(err -> log.warn("Failed to cache Kakao OAuth response: {}", err.getMessage()));
        return future;
    }

    private static class CachedKakaoResponseException extends RuntimeException {
        private final KakaoLoginResponseDto response;

        private CachedKakaoResponseException(KakaoLoginResponseDto response) {
            this.response = response;
        }
    }

    private static class CacheMissException extends RuntimeException {
        private CacheMissException() {
        }
    }

    /**
     * Apple 로그인
     * @param dto Apple 로그인 요청 DTO (Authorization Code 포함)
     * @return Apple 로그인 응답 DTO
     */
    public Future<AppleLoginResponseDto> appleLogin(AppleLoginRequestDto dto) {
        log.info("Apple login attempt with code");

        boolean isIos = "IOS".equalsIgnoreCase(dto.getPlatform())
            || "IOS".equalsIgnoreCase(dto.getDeviceOs());

        String webClientId = appleConfig.getString("serviceId");
        String iosClientId = appleConfig.getString("iosClientId");
        String clientId = isIos
            ? (iosClientId != null && !iosClientId.isBlank() ? iosClientId : webClientId)
            : webClientId;
        String teamId = appleConfig.getString("teamId");
        String keyId = appleConfig.getString("keyId");
        String privateKeyPath = appleConfig.getString("privateKeyPath");
        String redirectUri = isIos
            ? appleConfig.getString("iosRedirectUri")
            : appleConfig.getString("redirectUri");

        if (clientId == null || clientId.isBlank() || teamId == null || teamId.isBlank()
            || keyId == null || keyId.isBlank() || privateKeyPath == null || privateKeyPath.isBlank()) {
            log.error("Apple OAuth configuration is missing");
            return Future.failedFuture(new BadRequestException("Apple OAuth configuration is missing"));
        }

        String clientSecret;
        try {
            clientSecret = AppleOAuthUtil.createClientSecret(teamId, keyId, clientId, privateKeyPath);
        } catch (Exception e) {
            log.error("Failed to create Apple client_secret", e);
            return Future.failedFuture(new BadRequestException("Apple OAuth configuration is missing"));
        }

        // iOS 네이티브 Sign in with Apple은 idToken을 직접 전달하므로 토큰 교환 없이 검증 가능.
        Future<JsonObject> userInfoFuture;
        if (isIos && dto.getIdToken() != null && !dto.getIdToken().isBlank()) {
            userInfoFuture = AppleOAuthUtil.verifyIdToken(webClient, dto.getIdToken(), clientId);
        } else {
            userInfoFuture = AppleOAuthUtil.exchangeToken(webClient, dto.getCode(), clientId, clientSecret, redirectUri)
                .compose(tokenResponse -> {
                    String idToken = tokenResponse.getString("id_token");
                    return AppleOAuthUtil.verifyIdToken(webClient, idToken, clientId);
                });
        }

        return userInfoFuture
            .compose(userInfo -> {
                String appleId = userInfo.getString("sub");
                String email = userInfo.getString("email");

                // 이메일이 없으면 provider_user_id로 기존 계정 조회
                if (email == null || email.isBlank()) {
                    return socialLinkRepository.findUserIdByProviderUserId(pool, "APPLE", appleId)
                        .compose(userId -> {
                            if (userId == null) {
                                return Future.failedFuture(new BadRequestException("Apple email is required for first login."));
                            }
                            return userRepository.getUserById(pool, userId)
                                .compose(existingUser -> buildAppleLoginResponse(existingUser, appleId, null, dto));
                        });
                }

                return userRepository.getUserByLoginIdIncludingDeleted(pool, email)
                    .compose(existingUser -> {
                        if (existingUser != null && existingUser.getDeletedAt() != null) {
                            return Future.failedFuture(new BadRequestException("탈퇴한 계정입니다."));
                        }
                        if (existingUser != null) {
                            if (isAccountBlocked(existingUser)) {
                                return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
                            }
                            return buildAppleLoginResponse(existingUser, appleId, email, dto);
                        }

                        log.info("New user registration via Apple: {}", email);
                        return createSocialSignupToken("APPLE", appleId, email)
                            .compose(signupToken -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                                .map(dbMin -> {
                                    String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                                    return AppleLoginResponseDto.builder()
                                        .loginId(email)
                                        .isNewUser(true)
                                        .signupToken(signupToken)
                                        .minAppVersion(min)
                                        .build();
                                }));
                    });
            })
            .recover(throwable -> {
                if (throwable instanceof UnauthorizedException) {
                    return Future.<AppleLoginResponseDto>failedFuture((UnauthorizedException) throwable);
                }
                if (throwable instanceof BadRequestException) {
                    return Future.<AppleLoginResponseDto>failedFuture((BadRequestException) throwable);
                }
                log.error("Apple login failed", throwable);
                return Future.<AppleLoginResponseDto>failedFuture(new BadRequestException("Apple login failed: " + throwable.getMessage()));
            });
    }

    private Future<AppleLoginResponseDto> buildAppleLoginResponse(User existingUser, String appleId, String email, AppleLoginRequestDto dto) {
        if (existingUser == null) {
            return Future.failedFuture(new BadRequestException("사용자를 찾을 수 없습니다."));
        }
        if (isAccountBlocked(existingUser)) {
            return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
        }
        return socialLinkRepository.createSocialLink(pool, existingUser.getId(), "APPLE", appleId, email != null ? email : existingUser.getLoginId())
            .compose(linked -> registerOrUpdateDevice(existingUser.getId(), dto.getDeviceId(), dto.getDeviceType(), dto.getDeviceOs(), dto.getAppVersion(), dto.getClientIp(), dto.getUserAgent())
                .compose(ignored -> appConfigRepository.getMinAppVersion(pool, isIos(dto.getDeviceOs()))
                    .map(dbMin -> {
                        String min = (dbMin != null && !dbMin.isBlank()) ? dbMin : minAppVersion;
                        String jwtAccessToken = AuthUtils.generateAccessToken(jwtAuth, existingUser.getId(), UserRole.USER, getAccessTokenExpireSeconds());
                        String jwtRefreshToken = AuthUtils.generateRefreshToken(jwtAuth, existingUser.getId(), UserRole.USER, (int) getRefreshTokenExpireSeconds());
                        return AppleLoginResponseDto.builder()
                            .accessToken(jwtAccessToken)
                            .refreshToken(jwtRefreshToken)
                            .userId(existingUser.getId())
                            .loginId(existingUser.getLoginId())
                            .isNewUser(false)
                            .isTest(existingUser.getIsTest())
                            .minAppVersion(min)
                            .warning(toRestrictionFlag(existingUser.getIsWarning()))
                            .miningSuspended(toRestrictionFlag(existingUser.getIsMiningSuspended()))
                            .accountBlocked(toRestrictionFlag(existingUser.getIsAccountBlocked()))
                            .build();
                    })));
    }
    
    /**
     * 회원 탈퇴
     * @param userId 사용자 ID
     * @param password 비밀번호 (확인용)
     * @return 탈퇴 응답
     */
    public Future<DeleteAccountResponseDto> deleteAccount(Long userId, String password) {
        log.info("Account deletion request from user: {}", userId);
        
        // 1. 사용자 조회 및 비밀번호 확인
        return userRepository.getUserByIdNotDeleted(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }

                String normalizedPassword = password != null ? password.trim() : "";
                Future<Void> authCheck;
                if (!normalizedPassword.isEmpty()) {
                    if (!BCrypt.checkpw(normalizedPassword, user.getPasswordHash())) {
                        return Future.failedFuture(new UnauthorizedException("Invalid password"));
                    }
                    authCheck = Future.succeededFuture();
                } else {
                    authCheck = socialLinkRepository.hasSocialLink(pool, userId)
                        .compose(hasSocial -> {
                            if (!hasSocial) {
                                return Future.failedFuture(new UnauthorizedException("Invalid password"));
                            }
                            return Future.succeededFuture();
                        });
                }

                // 2. 트랜잭션으로 모든 관련 데이터 Soft Delete
                return authCheck.compose(ignoredAuth -> pool.withTransaction(client -> {
                    // 2-1. 사용자 지갑 Soft Delete
                    return walletRepository.softDeleteWalletsByUserId(client, userId)
                        // 2-2. 전송 내역 Soft Delete
                        .compose(v -> transferRepository.softDeleteTransfersByUserId(client, userId))
                        // 2-3. 보너스 Soft Delete
                        .compose(v -> bonusRepository.softDeleteBonusesByUserId(client, userId))
                        // 2-4. 일일 채굴 Soft Delete
                        .compose(v -> miningRepository.softDeleteDailyMiningByUserId(client, userId))
                        // 2-5. 채굴 내역 Soft Delete
                        .compose(v -> miningRepository.softDeleteMiningHistoryByUserId(client, userId))
                        // 2-6. 미션 진행 상황 Soft Delete
                        .compose(v -> missionRepository.softDeleteUserMissionsByUserId(client, userId))
                        // 2-7. 알림 Soft Delete
                        .compose(v -> notificationRepository.softDeleteNotificationsByUserId(client, userId))
                        // 2-8. 구독 Soft Delete
                        .compose(v -> subscriptionRepository.softDeleteSubscriptionByUserId(client, userId))
                        // 2-9. 리뷰 Soft Delete
                        .compose(v -> reviewRepository.softDeleteReviewByUserId(client, userId))
                        // 2-10. 에이전시 멤버십 Soft Delete
                        .compose(v -> agencyRepository.softDeleteAgencyMembershipByUserId(client, userId))
                        // 2-11. 소셜 링크 Soft Delete
                        .compose(v -> socialLinkRepository.softDeleteSocialLinksByUserId(client, userId))
                        // 2-12. 전화번호 인증 Soft Delete
                        .compose(v -> phoneVerificationRepository.softDeletePhoneVerificationByUserId(client, userId))
                        // 2-13. 이메일 인증 Soft Delete
                        .compose(v -> emailVerificationRepository.softDeleteEmailVerificationByUserId(client, userId))
                        // 2-14. 스왑 Soft Delete
                        .compose(v -> swapRepository.softDeleteSwapsByUserId(client, userId))
                        // 2-15. 환전 Soft Delete
                        .compose(v -> exchangeRepository.softDeleteExchangesByUserId(client, userId))
                        // 2-16. 결제 입금 Soft Delete
                        .compose(v -> paymentDepositRepository.softDeletePaymentDepositsByUserId(client, userId))
                        // 2-17. 토큰 입금 Soft Delete
                        .compose(v -> tokenDepositRepository.softDeleteTokenDepositsByUserId(client, userId))
                        // 2-18. 에어드랍 Phase Soft Delete
                        .compose(v -> airdropRepository.softDeleteAirdropPhasesByUserId(client, userId))
                        // 2-19. 에어드랍 전송 Soft Delete
                        .compose(v -> airdropRepository.softDeleteAirdropTransfersByUserId(client, userId))
                        // 2-20. 문의 Soft Delete
                        .compose(v -> inquiryRepository.softDeleteInquiriesByUserId(client, userId))
                        // 2-21. 레퍼럴 관계 Soft Delete (referrer_id 또는 referred_id가 userId인 경우)
                        .compose(v -> referralRepository.softDeleteReferralRelationsByUserId(client, userId))
                        // 2-22. 디바이스 Soft Delete
                        .compose(v -> deviceRepository.softDeleteDevicesByUserId(client, userId))
                        // 2-23. 사용자 Soft Delete (마지막)
                        .compose(v -> userRepository.softDeleteUser(client, userId));
                }))
                .map(deletedUser -> {
                    log.info("Account deleted successfully - userId: {}", userId);
                    
                    // 3. Redis에서 사용자 토큰 제거 (모든 디바이스 로그아웃)
                    if (redisApi != null) {
                        logoutAllDevices(userId)
                            .onFailure(e -> log.warn("Failed to remove user tokens from Redis: {}", e.getMessage()));
                    }
                    
                    return DeleteAccountResponseDto.builder()
                        .status("OK")
                        .message("Account deleted successfully")
                        .build();
                });
            })
            .recover(throwable -> {
                if (throwable instanceof UnauthorizedException) {
                    return Future.failedFuture(throwable);
                }
                log.error("Account deletion failed - userId: {}", userId, throwable);
                return Future.failedFuture(new BadRequestException("Account deletion failed: " + throwable.getMessage()));
            });
    }
}
