package com.foxya.coin.auth;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.common.utils.EmailService;
import com.foxya.coin.common.utils.GoogleOAuthUtil;
import com.foxya.coin.user.UserRepository;
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
import com.foxya.coin.auth.dto.GoogleLoginRequestDto;
import com.foxya.coin.auth.dto.RefreshResponseDto;
import com.foxya.coin.auth.dto.GoogleLoginResponseDto;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.bonus.BonusRepository;
import com.foxya.coin.mining.MiningRepository;
import com.foxya.coin.mission.MissionRepository;
import com.foxya.coin.notification.NotificationRepository;
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
import com.foxya.coin.referral.ReferralRepository;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.UUID;

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
    private final TransferRepository transferRepository;
    private final BonusRepository bonusRepository;
    private final MiningRepository miningRepository;
    private final MissionRepository missionRepository;
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
    private final ReferralRepository referralRepository;
    private final EmailService emailService;
    private final WebClient webClient;
    private final JsonObject googleConfig;
    
    // Redis 키 접두사
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final String USER_TOKENS_PREFIX = "user:tokens:";
    
    public AuthService(PgPool pool, UserRepository userRepository, UserService userService, JWTAuth jwtAuth, JsonObject jwtConfig,
                      SocialLinkRepository socialLinkRepository, PhoneVerificationRepository phoneVerificationRepository,
                      RedisAPI redisApi, WalletRepository walletRepository, TransferRepository transferRepository,
                      BonusRepository bonusRepository, MiningRepository miningRepository, MissionRepository missionRepository,
                      NotificationRepository notificationRepository, SubscriptionRepository subscriptionRepository,
                      ReviewRepository reviewRepository, AgencyRepository agencyRepository, SwapRepository swapRepository,
                      ExchangeRepository exchangeRepository, PaymentDepositRepository paymentDepositRepository,
                      TokenDepositRepository tokenDepositRepository, AirdropRepository airdropRepository,
                      InquiryRepository inquiryRepository, EmailVerificationRepository emailVerificationRepository,
                      ReferralRepository referralRepository, EmailService emailService, WebClient webClient, JsonObject googleConfig) {
        super(pool);
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
        this.socialLinkRepository = socialLinkRepository;
        this.phoneVerificationRepository = phoneVerificationRepository;
        this.redisApi = redisApi;
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.bonusRepository = bonusRepository;
        this.miningRepository = miningRepository;
        this.missionRepository = missionRepository;
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
        this.referralRepository = referralRepository;
        this.emailService = emailService;
        this.webClient = webClient;
        this.googleConfig = googleConfig;
    }
    
    /**
     * 로그인
     */
    public Future<LoginResponseDto> login(LoginDto dto) {
        return userRepository.getUserByLoginId(pool, dto.getLoginId())
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new UnauthorizedException("사용자를 찾을 수 없습니다."));
                }
                
                // 비밀번호 검증
                if (!BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
                    return Future.failedFuture(new UnauthorizedException("비밀번호가 일치하지 않습니다."));
                }
                
                // Access Token & Refresh Token 생성
                String accessToken = AuthUtils.generateAccessToken(jwtAuth, user.getId(), UserRole.USER);
                String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, user.getId(), UserRole.USER);
                
                return Future.succeededFuture(
                    LoginResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(user.getId())
                        .loginId(user.getLoginId())
                        .build()
                );
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
     * 회원가입
     */
    public Future<LoginResponseDto> register(CreateUserDto dto) {
        // UserService.createUser()를 사용하여 레퍼럴 코드 자동 생성 포함
        return userService.createUser(dto)
            .compose(user -> {
                // 회원가입 후 자동 로그인
                String accessToken = AuthUtils.generateAccessToken(jwtAuth, user.getId(), UserRole.USER);
                String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, user.getId(), UserRole.USER);
                
                return Future.succeededFuture(
                    LoginResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(user.getId())
                        .loginId(user.getLoginId())
                        .build()
                );
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
            UserRole.valueOf(role)
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
            UserRole.valueOf(role)
        );
        
        return Future.succeededFuture(
            TokenResponseDto.builder()
                .refreshToken(refreshToken)
                .userId(userId)
                .build()
        );
    }

    private static final long REFRESH_TOKEN_EXPIRE_SECONDS = 864000L; // 10일

    /**
     * refreshToken으로 accessToken(및 refreshToken 로테이션) 재발급.
     * Authorization 헤더 없이 body의 refreshToken만으로 호출.
     * 실패 시 UnauthorizedException("Invalid or expired refresh token") → 401.
     */
    public Future<RefreshResponseDto> refresh(String refreshToken) {
        // Vert.x 4.x JWTAuth.authenticate: TokenCredentials 사용 (JsonObject "jwt"/"token"은 내부 구현에 따라 동작 안 할 수 있음)
        return jwtAuth.authenticate(new TokenCredentials(refreshToken))
            .compose(user -> {
                Long userId = AuthUtils.getUserIdOf(user);
                String role = AuthUtils.getUserRoleOf(user);
                if (userId == null || role == null) {
                    return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
                }
                return isTokenBlacklisted(refreshToken)
                    .compose(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
                        }
                        String accessToken = AuthUtils.generateAccessToken(jwtAuth, userId, UserRole.valueOf(role));
                        String newRefresh = AuthUtils.generateRefreshToken(jwtAuth, userId, UserRole.valueOf(role));
                        RefreshResponseDto dto = RefreshResponseDto.builder()
                            .accessToken(accessToken)
                            .refreshToken(newRefresh)
                            .build();
                        // 로테이션: 사용한 refreshToken 블랙리스트에 추가(재사용 방지)
                        return addToBlacklist(refreshToken, REFRESH_TOKEN_EXPIRE_SECONDS)
                            .map(v -> dto);
                    });
            })
            .recover(t -> {
                if (t instanceof UnauthorizedException) return Future.failedFuture(t);
                log.warn("Refresh token verification failed: {}", t.getMessage());
                return Future.failedFuture(new UnauthorizedException("Invalid or expired refresh token"));
            });
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
     * @param token JWT 토큰 (블랙리스트에 추가)
     * @param allDevices 모든 디바이스 로그아웃 여부
     * @return 로그아웃 응답
     */
    public Future<LogoutResponseDto> logout(Long userId, String token, boolean allDevices) {
        log.info("Logout request from user: {}, allDevices: {}", userId, allDevices);
        
        if (redisApi == null) {
            log.warn("Redis API is not available, skipping token blacklist");
            return Future.succeededFuture(
                LogoutResponseDto.builder()
                    .status("OK")
                    .message("Logged out successfully")
                    .build()
            );
        }
        
        // 현재 토큰을 블랙리스트에 추가
        if (token != null && !token.isEmpty()) {
            return addTokenToBlacklist(token)
                .compose(v -> {
                    if (allDevices) {
                        // 모든 디바이스 로그아웃: 사용자의 모든 토큰을 블랙리스트에 추가
                        return logoutAllDevices(userId);
                    } else {
                        return Future.succeededFuture();
                    }
                })
                .map(v -> LogoutResponseDto.builder()
                    .status("OK")
                    .message("Logged out successfully")
                    .build());
        } else {
            // 토큰이 없으면 사용자 정보만 로그
            if (allDevices) {
                return logoutAllDevices(userId)
                    .map(v -> LogoutResponseDto.builder()
                        .status("OK")
                        .message("Logged out from all devices")
                        .build());
            } else {
                return Future.succeededFuture(
                    LogoutResponseDto.builder()
                        .status("OK")
                        .message("Logged out successfully")
                        .build()
                );
            }
        }
    }
    
    /**
     * Access Token을 블랙리스트에 추가 (로그아웃 시)
     */
    private Future<Void> addTokenToBlacklist(String token) {
        int accessTokenExpireMinutes = jwtConfig.getInteger("access_token_expire_minutes", 30);
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
        boolean isAndroid = "ANDROID".equalsIgnoreCase(platform) || hasCodeVerifier;

        String clientId = isAndroid
            ? googleConfig.getString("androidClientId", googleConfig.getString("clientId"))
            : googleConfig.getString("clientId");
        String clientSecret = isAndroid ? null : googleConfig.getString("clientSecret");
        String redirectUri = isAndroid
            ? googleConfig.getString("androidRedirectUri", googleConfig.getString("redirectUri"))
            : googleConfig.getString("redirectUri");
        
        if (clientId == null) {
            log.error("Google OAuth configuration is missing");
            return Future.failedFuture(new BadRequestException("Google OAuth configuration is missing"));
        }
        if (clientSecret == null && (dto.getCodeVerifier() == null || dto.getCodeVerifier().isEmpty())) {
            log.error("Google OAuth client secret is missing");
            return Future.failedFuture(new BadRequestException("Google OAuth configuration is missing"));
        }
        
        // 1. Google OAuth 인증 (토큰 교환 + 사용자 정보 조회)
        return GoogleOAuthUtil.authenticate(webClient, dto.getCode(), clientId, clientSecret, redirectUri, dto.getCodeVerifier())
            .compose(userInfo -> {
                String googleId = userInfo.getString("id");
                String email = userInfo.getString("email");
                String name = userInfo.getString("name");
                String picture = userInfo.getString("picture");
                
                // 2. 기존 계정 확인 (이메일로)
                return userRepository.getUserByLoginId(pool, email)
                    .compose(existingUser -> {
                        if (existingUser != null) {
                            // 기존 사용자: 소셜 링크 연동 및 로그인
                            log.info("Existing user found: {}", email);
                            return socialLinkRepository.createSocialLink(pool, existingUser.getId(), "GOOGLE", googleId, email)
                                .compose(linked -> {
                                    if (!linked) {
                                        log.warn("Failed to link Google account, but continuing login");
                                    }
                                    
                                    // JWT 토큰 생성
                                    String jwtAccessToken = AuthUtils.generateAccessToken(jwtAuth, existingUser.getId(), UserRole.USER);
                                    String jwtRefreshToken = AuthUtils.generateRefreshToken(jwtAuth, existingUser.getId(), UserRole.USER);
                                    
                                    return Future.succeededFuture(
                                        GoogleLoginResponseDto.builder()
                                            .accessToken(jwtAccessToken)
                                            .refreshToken(jwtRefreshToken)
                                            .userId(existingUser.getId())
                                            .loginId(existingUser.getLoginId())
                                            .isNewUser(false)
                                            .build()
                                    );
                                });
                        } else {
                            // 신규 사용자: 계정 생성
                            log.info("New user registration via Google: {}", email);
                            CreateUserDto createUserDto = CreateUserDto.builder()
                                .loginId(email)
                                .password(UUID.randomUUID().toString()) // 임시 비밀번호 (소셜 로그인은 비밀번호 불필요)
                                .build();
                            
                            return userService.createUser(createUserDto)
                                .compose(newUser -> {
                                    // 소셜 링크 연동
                                    return socialLinkRepository.createSocialLink(pool, newUser.getId(), "GOOGLE", googleId, email)
                                        .compose(linked -> {
                                            // JWT 토큰 생성
                                            String jwtAccessToken = AuthUtils.generateAccessToken(jwtAuth, newUser.getId(), UserRole.USER);
                                            String jwtRefreshToken = AuthUtils.generateRefreshToken(jwtAuth, newUser.getId(), UserRole.USER);
                                            
                                            return Future.succeededFuture(
                                                GoogleLoginResponseDto.builder()
                                                    .accessToken(jwtAccessToken)
                                                    .refreshToken(jwtRefreshToken)
                                                    .userId(newUser.getId())
                                                    .loginId(newUser.getLoginId())
                                                    .isNewUser(true)
                                                    .build()
                                            );
                                        });
                                });
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
                
                // 비밀번호 검증
                if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                    return Future.failedFuture(new UnauthorizedException("Invalid password"));
                }
                
                // 2. 트랜잭션으로 모든 관련 데이터 Soft Delete
                return pool.withTransaction(client -> {
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
                        // 2-22. 사용자 Soft Delete (마지막)
                        .compose(v -> userRepository.softDeleteUser(client, userId));
                })
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
