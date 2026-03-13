package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.EmailService;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.CountryCodeUtils;
import com.foxya.coin.subscription.SubscriptionService;
import com.foxya.coin.subscription.dto.SubscriptionStatusResponseDto;
import com.foxya.coin.user.dto.ExternalLinkCodeResponseDto;
import com.foxya.coin.user.dto.ExternalLinkStatusResponseDto;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import com.foxya.coin.user.dto.LoginResponseDto;
import com.foxya.coin.user.dto.MeResponseDto;
import com.foxya.coin.user.dto.ProfileImageUploadResponseDto;
import com.foxya.coin.user.dto.ReferralCodeResponseDto;
import com.foxya.coin.user.dto.UserProfileResponseDto;
import com.foxya.coin.user.dto.EmailInfoDto;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class UserService extends BaseService {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{1,8}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$");
    private static final String EXTERNAL_LINK_CODE_PREFIX = "external-link:code:";
    private static final int EXTERNAL_LINK_CODE_TTL_SECONDS = 300;
    private static final int EXTERNAL_LINK_CODE_LENGTH = 8;
    private static final String DEFAULT_PROFILE_IMAGE_UPLOAD_DIR = "/tmp/fox_coin/profile-images";
    
    private final UserRepository userRepository;
    private final JWTAuth jwtAuth;
    private final JsonObject jwtConfig;

    private static Boolean toRestrictionFlag(Integer value) {
        return value != null && value == 1;
    }
    private final String frontendBaseUrl;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final RedisAPI redisApi;
    private final UserExternalIdRepository userExternalIdRepository;
    private final SubscriptionService subscriptionService;
    private final ProfileImageProcessor profileImageProcessor;
    private final ProfileImageModerationService profileImageModerationService;
    
    public UserService(PgPool pool,
                       UserRepository userRepository,
                       JWTAuth jwtAuth,
                       JsonObject jwtConfig,
                       JsonObject frontendConfig,
                       EmailVerificationRepository emailVerificationRepository,
                       EmailService emailService,
                       RedisAPI redisApi,
                       UserExternalIdRepository userExternalIdRepository,
                       SubscriptionService subscriptionService,
                       String profileImageUploadDir,
                       ProfileImageModerationService profileImageModerationService) {
        super(pool);
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
        this.frontendBaseUrl = frontendConfig != null ? frontendConfig.getString("baseUrl", "http://localhost") : "http://localhost";
        this.emailVerificationRepository = emailVerificationRepository;
        this.emailService = emailService;
        this.redisApi = redisApi;
        this.userExternalIdRepository = userExternalIdRepository;
        this.subscriptionService = subscriptionService;
        this.profileImageProcessor = new ProfileImageProcessor(
            profileImageUploadDir != null && !profileImageUploadDir.isBlank()
                ? profileImageUploadDir
                : DEFAULT_PROFILE_IMAGE_UPLOAD_DIR
        );
        this.profileImageModerationService = profileImageModerationService;
    }
    
    public Future<User> createUser(CreateUserDto dto) {
        // 비밀번호 해시
        String passwordHash = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());
        dto.setPasswordHash(passwordHash);
        
        // 사용자 생성 후 레퍼럴 코드 자동 생성
        return userRepository.createUser(pool, dto)
            .compose(user -> {
                // 레퍼럴 코드가 없으면 자동 생성
                if (user.getReferralCode() == null || user.getReferralCode().isEmpty()) {
                    return generateUniqueReferralCode(0)
                        .compose(referralCode -> {
                            log.info("Auto-generating referral code for new user: {} -> {}", user.getId(), referralCode);
                            return userRepository.updateReferralCode(pool, user.getId(), referralCode);
                        })
                        .recover(throwable -> {
                            // 레퍼럴 코드 생성 실패해도 사용자 생성은 성공으로 처리
                            log.warn("Failed to auto-generate referral code for user {}: {}", user.getId(), throwable.getMessage());
                            return Future.succeededFuture(user);
                        });
                }
                return Future.succeededFuture(user);
            });
    }
    
    public Future<LoginResponseDto> login(LoginDto dto) {
        return userRepository.getUserByLoginId(pool, dto.getLoginId())
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new UnauthorizedException("사용자를 찾을 수 없습니다."));
                }
                if (user.getIsAccountBlocked() != null && user.getIsAccountBlocked() == 1) {
                    return Future.failedFuture(new UnauthorizedException("차단된 계정입니다."));
                }
                
                // 비밀번호 검증
                if (!BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
                    return Future.failedFuture(new UnauthorizedException("비밀번호가 일치하지 않습니다."));
                }
                
                // JWT 토큰 생성 (config 기반 만료)
                int accessExpireSec = jwtConfig.getInteger("access_token_expire_minutes", 60) * 60;
                int refreshExpireSec = jwtConfig.getInteger("refresh_token_expire_minutes", 14400) * 60;
                String accessToken = com.foxya.coin.common.utils.AuthUtils.generateAccessToken(
                    jwtAuth, user.getId(), com.foxya.coin.common.enums.UserRole.USER, accessExpireSec);
                String refreshToken = com.foxya.coin.common.utils.AuthUtils.generateRefreshToken(
                    jwtAuth, user.getId(), com.foxya.coin.common.enums.UserRole.USER, refreshExpireSec);
                
                return Future.succeededFuture(
                    LoginResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .userId(user.getId())
                        .loginId(user.getLoginId())
                        .isTest(user.getIsTest())
                        .warning(toRestrictionFlag(user.getIsWarning()))
                        .miningSuspended(toRestrictionFlag(user.getIsMiningSuspended()))
                        .accountBlocked(toRestrictionFlag(user.getIsAccountBlocked()))
                        .build()
                );
            });
    }
    
    public Future<User> getUserById(Long id) {
        return userRepository.getUserById(pool, id);
    }

    /**
     * 사용자 프로필 조회 (GET /users/{id}). id, loginId, nickname, profileImageUrl, level, referralCode.
     */
    public Future<UserProfileResponseDto> getUserProfile(Long id) {
        return userRepository.getUserById(pool, id)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }
                return Future.succeededFuture(UserProfileResponseDto.builder()
                    .id(user.getId())
                    .loginId(user.getLoginId())
                    .nickname(user.getNickname())
                    .profileImageUrl(user.getProfileImageUrl())
                    .level(user.getLevel() != null ? user.getLevel() : 1)
                    .referralCode(user.getReferralCode())
                    .country(user.getCountryCode())
                    .build());
            });
    }

    public Future<ProfileImageUploadResponseDto> uploadMyProfileImage(
        Long userId,
        Path uploadedTempFile,
        String contentType,
        String originalFileName,
        long uploadBytes
    ) {
        if (uploadedTempFile == null) {
            return Future.failedFuture(new BadRequestException("업로드된 이미지 파일이 없습니다."));
        }

        long resolvedUploadBytes = uploadBytes;
        if (resolvedUploadBytes <= 0) {
            try {
                resolvedUploadBytes = Files.size(uploadedTempFile);
            } catch (IOException ignored) {
                resolvedUploadBytes = 0;
            }
        }
        if (resolvedUploadBytes <= 0) {
            return Future.failedFuture(new BadRequestException("업로드된 이미지 파일이 비어 있습니다."));
        }
        if (resolvedUploadBytes > ProfileImageProcessor.MAX_UPLOAD_BYTES) {
            return Future.failedFuture(new BadRequestException("프로필 이미지는 최대 5MB까지 업로드할 수 있습니다."));
        }

        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }

                int level = user.getLevel() != null ? user.getLevel() : 1;
                if (level >= ProfileImageProcessor.MIN_LEVEL_TO_UPLOAD) {
                    return saveProfileImage(userId, uploadedTempFile, contentType, originalFileName);
                }
                if (subscriptionService == null) {
                    return Future.failedFuture(new BadRequestException("레벨 2 이상부터 프로필 사진을 등록할 수 있습니다."));
                }
                return subscriptionService.getSubscriptionStatus(userId)
                    .compose(subscriptionStatus -> canUnlockProfileImageWithSubscription(subscriptionStatus)
                        ? saveProfileImage(userId, uploadedTempFile, contentType, originalFileName)
                        : Future.failedFuture(new BadRequestException("레벨 2 이상부터 프로필 사진을 등록할 수 있습니다."))
                    );
            });
    }

    private boolean canUnlockProfileImageWithSubscription(SubscriptionStatusResponseDto subscriptionStatus) {
        return subscriptionStatus != null && Boolean.TRUE.equals(subscriptionStatus.getProfileImageUnlock());
    }

    private Future<ProfileImageUploadResponseDto> saveProfileImage(
        Long userId,
        Path uploadedTempFile,
        String contentType,
        String originalFileName
    ) {
        Future<Void> moderationFuture = profileImageModerationService != null
            ? profileImageModerationService.validate(uploadedTempFile)
            : Future.succeededFuture();

        return moderationFuture.compose(v -> processProfileImageBlocking(userId, uploadedTempFile, contentType, originalFileName))
            .compose(version -> {
                String profileImageUrl = ProfileImageProcessor.buildVariantUrl(
                    userId,
                    ProfileImageProcessor.VARIANT_PROFILE,
                    version
                );
                return userRepository.updateProfileImageUrl(pool, userId, profileImageUrl)
                    .map(updated -> ProfileImageUploadResponseDto.builder()
                        .profileImageUrl(profileImageUrl)
                        .build());
            });
    }

    public Future<Void> deleteMyProfileImage(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }
                return hardDeleteProfileImageBlocking(userId)
                    .compose(v -> userRepository.updateProfileImageUrl(pool, userId, null).mapEmpty());
            });
    }

    public Future<Path> getProfileImagePath(Long userId, String rawVariant) {
        String variant = profileImageProcessor.normalizeVariant(rawVariant);
        if (variant == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 이미지 변형 타입입니다."));
        }
        Path path = profileImageProcessor.resolveVariantPath(userId, variant);
        if (!Files.exists(path)) {
            return Future.failedFuture(new NotFoundException("프로필 이미지를 찾을 수 없습니다."));
        }
        return Future.succeededFuture(path);
    }

    private Future<Long> processProfileImageBlocking(
        Long userId,
        Path uploadedTempFile,
        String contentType,
        String originalFileName
    ) {
        io.vertx.core.Context context = Vertx.currentContext();
        if (context == null) {
            try {
                return Future.succeededFuture(profileImageProcessor.processAndStore(
                    userId, uploadedTempFile, contentType, originalFileName));
            } catch (IOException e) {
                return Future.failedFuture(new BadRequestException(resolveProfileImageErrorMessage(e)));
            }
        }
        return context.owner().<Long>executeBlocking(promise -> {
            try {
                long version = profileImageProcessor.processAndStore(userId, uploadedTempFile, contentType, originalFileName);
                promise.complete(version);
            } catch (IOException e) {
                promise.fail(e);
            }
        }).recover(throwable -> Future.failedFuture(new BadRequestException(resolveProfileImageErrorMessage(throwable))));
    }

    private Future<Void> hardDeleteProfileImageBlocking(Long userId) {
        io.vertx.core.Context context = Vertx.currentContext();
        if (context == null) {
            try {
                profileImageProcessor.hardDeleteUserImages(userId);
                return Future.succeededFuture();
            } catch (IOException e) {
                return Future.failedFuture(new BadRequestException("프로필 이미지 삭제에 실패했습니다."));
            }
        }
        return context.owner().<Void>executeBlocking(promise -> {
            try {
                profileImageProcessor.hardDeleteUserImages(userId);
                promise.complete();
            } catch (IOException e) {
                promise.fail(e);
            }
        }).recover(throwable -> Future.failedFuture(new BadRequestException("프로필 이미지 삭제에 실패했습니다.")));
    }

    private String resolveProfileImageErrorMessage(Throwable throwable) {
        String message = throwable != null ? throwable.getMessage() : null;
        if (message == null || message.isBlank()) {
            return "프로필 이미지 처리에 실패했습니다.";
        }
        return message;
    }

    /**
     * 내 정보 조회 (설정/내정보수정용). name, nickname, phone, gender, country.
     */
    public Future<MeResponseDto> getMe(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }
                return Future.succeededFuture(MeResponseDto.builder()
                    .name(user.getName())
                    .nickname(user.getNickname())
                    .phone(user.getPhone())
                    .gender(user.getGender())
                    .country(user.getCountryCode())
                    .appReviewRewarded(Boolean.TRUE.equals(user.getAppReviewRewarded()))
                    .reviewPromptDismissed(Boolean.TRUE.equals(user.getReviewPromptDismissed()))
                    .reviewPromptLastShownAt(user.getReviewPromptLastShownAt())
                    .build());
            });
    }

    public Future<JsonObject> updateReviewPromptState(Long userId, JsonObject body) {
        Boolean dismissed = body != null ? body.getBoolean("dismissed") : null;
        boolean markShown = body != null && Boolean.TRUE.equals(body.getBoolean("markShown"));

        if (dismissed == null && !markShown) {
            return Future.failedFuture(new BadRequestException("dismissed 또는 markShown 값이 필요합니다."));
        }

        return userRepository.updateReviewPromptState(pool, userId, dismissed, markShown)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }
                JsonObject response = new JsonObject()
                    .put("reviewPromptDismissed", Boolean.TRUE.equals(user.getReviewPromptDismissed()));
                if (user.getReviewPromptLastShownAt() != null) {
                    response.put("reviewPromptLastShownAt", user.getReviewPromptLastShownAt().toString());
                } else {
                    response.putNull("reviewPromptLastShownAt");
                }
                return Future.succeededFuture(response);
            });
    }

    /**
     * 내 정보 수정. name, nickname, phone, gender, country 수정 가능.
     * 내 정보 설정은 7일에 1회만 변경 가능.
     * body에 포함된 필드만 반영. 빈 문자열: name·nickname은 변경 안 함, phone은 삭제(null).
     */
    public Future<Void> updateMe(Long userId, JsonObject body) {
        Map<String, Object> updates = new HashMap<>();
        if (body.containsKey("name")) {
            String v = body.getString("name");
            if (v != null && !v.isBlank()) {
                if (v.length() > 50) {
                    return Future.failedFuture(new BadRequestException("이름은 1~50자입니다."));
                }
                updates.put("name", v);
            }
        }
        if (body.containsKey("nickname")) {
            String v = body.getString("nickname");
            if (v != null && !v.isBlank()) {
                if (!NICKNAME_PATTERN.matcher(v).matches()) {
                    return Future.failedFuture(new BadRequestException("닉네임은 한글·영문·숫자 1~8자리로 입력해주세요."));
                }
                return userRepository.existsByNicknameExcludingUser(pool, v, userId)
                    .compose(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            return Future.failedFuture(new BadRequestException("이미 사용 중인 닉네임입니다."));
                        }
                        updates.put("nickname", v);
                        return applyProfileUpdates(userId, body, updates);
                    });
            } else {
                return applyProfileUpdates(userId, body, updates);
            }
        }
        return applyProfileUpdates(userId, body, updates);
    }

    private Future<Void> applyProfileUpdates(Long userId, JsonObject body, Map<String, Object> updates) {
        if (body.containsKey("phone")) {
            String v = body.getString("phone");
            if (v == null || v.isBlank()) {
                updates.put("phone", null);
            } else {
                if (!PHONE_PATTERN.matcher(v).matches()) {
                    return Future.failedFuture(new BadRequestException("올바른 연락처 형식이 아닙니다."));
                }
                updates.put("phone", v);
            }
        }
        if (body.containsKey("country")) {
            String country = body.getString("country");
            if (country != null && !country.isBlank()) {
                String normalizedCountry = CountryCodeUtils.normalizeCountryCode(country);
                if (!CountryCodeUtils.isValidSignupCountryCode(normalizedCountry)) {
                    return Future.failedFuture(new BadRequestException("유효하지 않은 국가 코드입니다."));
                }
                updates.put("country_code", normalizedCountry);
            }
        }
        if (body.containsKey("gender")) {
            String gender = body.getString("gender");
            if (gender != null && !gender.isBlank()) {
                String normalizedGender = gender.trim().toUpperCase();
                if (!"M".equals(normalizedGender) && !"F".equals(normalizedGender) && !"O".equals(normalizedGender)) {
                    return Future.failedFuture(new BadRequestException("gender는 M, F, O 또는 비워두세요."));
                }
                updates.put("gender", normalizedGender);
            }
        }
        if (updates.isEmpty()) {
            return Future.succeededFuture();
        }
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }
                if (user.getUpdatedAt() != null && user.getUpdatedAt().isAfter(DateUtils.now().minusDays(7))) {
                    return Future.failedFuture(new BadRequestException("내 정보는 7일에 한 번만 변경할 수 있습니다."));
                }
                return userRepository.updateMeProfile(pool, userId, updates);
            });
    }
    
    /**
     * 레퍼럴 코드 생성 (6자리 영문+숫자)
     */
    public Future<ReferralCodeResponseDto> generateReferralCode(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new BadRequestException("사용자를 찾을 수 없습니다."));
                }
                
                if (user.getReferralCode() != null && !user.getReferralCode().isEmpty()) {
                    return Future.failedFuture(new BadRequestException("이미 레퍼럴 코드가 존재합니다."));
                }
                
                // 레퍼럴 코드 생성 (최대 10번 시도)
                return generateUniqueReferralCode(0);
            })
            .compose(referralCode -> {
                // 레퍼럴 코드 업데이트
                return userRepository.updateReferralCode(pool, userId, referralCode)
                    .map(updatedUser -> ReferralCodeResponseDto.builder()
                        .referralCode(referralCode)
                        .referralLink(frontendBaseUrl + "/ref/" + referralCode)
                        .build());
            });
    }
    
    /**
     * 중복되지 않는 레퍼럴 코드 생성
     */
    private Future<String> generateUniqueReferralCode(int attempt) {
        if (attempt >= 10) {
            return Future.failedFuture(new BadRequestException("레퍼럴 코드 생성에 실패했습니다."));
        }
        
        String referralCode = generateRandomCode(6);
        
        return userRepository.existsByReferralCode(pool, referralCode)
            .compose(exists -> {
                if (exists) {
                    // 중복이면 재시도
                    return generateUniqueReferralCode(attempt + 1);
                } else {
                    return Future.succeededFuture(referralCode);
                }
            });
    }
    
    /**
     * 랜덤 코드 생성 (영문 대문자 + 숫자)
     */
    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return code.toString();
    }

    private Future<String> generateUniqueExternalLinkCode(int attempt) {
        if (attempt >= 5) {
            return Future.failedFuture(new BadRequestException("연동 코드 생성에 실패했습니다."));
        }
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("연동 코드 생성 기능을 사용할 수 없습니다."));
        }
        String code = generateRandomCode(EXTERNAL_LINK_CODE_LENGTH);
        String key = EXTERNAL_LINK_CODE_PREFIX + code;
        return redisApi.get(key)
            .compose(res -> {
                if (res != null && res.toString() != null && !res.toString().isBlank()) {
                    return generateUniqueExternalLinkCode(attempt + 1);
                }
                return Future.succeededFuture(code);
            });
    }

    public Future<ExternalLinkCodeResponseDto> issueExternalLinkCode(Long userId, String provider) {
        if (provider == null || provider.isBlank()) {
            return Future.failedFuture(new BadRequestException("provider가 필요합니다."));
        }
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("연동 코드 생성 기능을 사용할 수 없습니다."));
        }
        String normalizedProvider = provider.trim().toUpperCase();
        return generateUniqueExternalLinkCode(0)
            .compose(code -> {
                String key = EXTERNAL_LINK_CODE_PREFIX + code;
                JsonObject payload = new JsonObject()
                    .put("userId", userId)
                    .put("provider", normalizedProvider);
                return redisApi.setex(key, String.valueOf(EXTERNAL_LINK_CODE_TTL_SECONDS), payload.encode())
                    .map(res -> ExternalLinkCodeResponseDto.builder()
                        .linkCode(code)
                        .expiresIn(EXTERNAL_LINK_CODE_TTL_SECONDS)
                        .provider(normalizedProvider)
                        .build());
            });
    }

    public Future<ExternalLinkStatusResponseDto> getExternalLinkStatus(Long userId, String provider) {
        if (provider == null || provider.isBlank()) {
            return Future.failedFuture(new BadRequestException("provider가 필요합니다."));
        }
        String normalizedProvider = provider.trim().toUpperCase();
        return userExternalIdRepository.getExternalIdByUserIdAndProvider(pool, userId, normalizedProvider)
            .map(externalId -> ExternalLinkStatusResponseDto.builder()
                .linked(externalId != null && !externalId.isBlank())
                .provider(normalizedProvider)
                .externalId(externalId)
                .build());
    }
    
    /**
     * 사용자의 추천인 코드 조회
     */
    public Future<ReferralCodeResponseDto> getReferralCode(Long userId) {
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다."));
                }
                
                String referralCode = user.getReferralCode();
                if (referralCode == null || referralCode.isEmpty()) {
                    return Future.failedFuture(new com.foxya.coin.common.exceptions.BadRequestException("레퍼럴 코드가 없습니다."));
                }
                
                String referralLink = frontendBaseUrl + "/ref/" + referralCode;
                
                return Future.succeededFuture(ReferralCodeResponseDto.builder()
                    .referralCode(referralCode)
                    .referralLink(referralLink)
                    .build());
            });
    }

    /**
     * 현재 로그인한 사용자의 이메일 정보 조회.
     * email_verifications에 인증된 이메일이 없으면, user.login_id가 이메일 형식(@ 포함)일 때 해당 값을 이메일로 반환.
     */
    public Future<EmailInfoDto> getEmailInfo(Long userId) {
        return emailVerificationRepository.getLatestByUserId(pool, userId)
            .compose(ev -> {
                if (ev != null && Boolean.TRUE.equals(ev.isVerified)) {
                    return Future.succeededFuture(EmailInfoDto.builder()
                        .email(ev.email)
                        .verified(true)
                        .build());
                }
                // email_verifications 없거나 미인증 시, 가입 시 사용한 login_id(이메일) 폴백
                return userRepository.getUserById(pool, userId)
                    .map(user -> {
                        if (user == null) {
                            return EmailInfoDto.builder().email(null).verified(false).build();
                        }
                        String loginId = user.getLoginId();
                        if (loginId != null && loginId.contains("@")) {
                            return EmailInfoDto.builder().email(loginId).verified(true).build();
                        }
                        return EmailInfoDto.builder().email(null).verified(false).build();
                    });
            });
    }

    /**
     * 이메일 설정/변경 시 비밀번호 확인용. 본인 확인 후 프론트에서 인증 코드 발송 버튼 활성화 등에 사용.
     */
    public Future<Void> confirmPasswordForEmail(Long userId, String password) {
        if (password == null || password.isBlank()) {
            return Future.failedFuture(new BadRequestException("비밀번호를 입력해주세요."));
        }
        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }
                String hash = user.getPasswordHash();
                if (hash == null || hash.isBlank()) {
                    return Future.failedFuture(new UnauthorizedException("비밀번호로 로그인한 계정만 사용할 수 있습니다."));
                }
                if (!BCrypt.checkpw(password, hash)) {
                    return Future.failedFuture(new UnauthorizedException("비밀번호가 일치하지 않습니다."));
                }
                return Future.succeededFuture();
            })
            .mapEmpty();
    }

    /**
     * 이메일 인증 코드 발송
     */
    public Future<Void> sendEmailVerificationCode(Long userId, String email) {
        return emailVerificationRepository.findVerifiedEmailUserId(pool, email)
            .compose(existingUserId -> {
                if (existingUserId != null && !existingUserId.equals(userId)) {
                    return Future.failedFuture(new BadRequestException("이미 다른 사용자가 사용 중인 이메일입니다."));
                }
                String code = emailService.generateVerificationCode();
                return emailVerificationRepository.upsertVerification(pool, userId, email, code, com.foxya.coin.common.utils.DateUtils.now().plusMinutes(10))
                    .compose(saved -> {
                        if (!saved) {
                            return Future.failedFuture(new BadRequestException("이메일 인증 코드를 저장하지 못했습니다."));
                        }
                        return emailService.sendVerificationCode(email, code);
                    });
            })
            .mapEmpty();
    }

    /**
     * 이메일 인증 및 등록. 성공 시 login_id를 새 이메일로 변경하여 새 이메일로 로그인 가능.
     */
    public Future<Void> verifyEmail(Long userId, String email, String code) {
        return emailVerificationRepository.findVerifiedEmailUserId(pool, email)
            .compose(existingUserId -> {
                if (existingUserId != null && !existingUserId.equals(userId)) {
                    return Future.failedFuture(new BadRequestException("이미 다른 사용자가 사용 중인 이메일입니다."));
                }
                return emailVerificationRepository.verifyEmail(pool, userId, email, code)
                    .compose(success -> {
                        if (!success) {
                            return Future.failedFuture(new BadRequestException("인증 코드가 유효하지 않거나 만료되었습니다."));
                        }
                        return userRepository.updateLoginId(pool, userId, email).mapEmpty();
                    });
            });
    }

    /**
     * 거래 비밀번호 설정/변경 (이메일 인증 코드 기반)
     */
    public Future<Void> setTransactionPassword(Long userId, String code, String newPassword) {
        // newPassword: 숫자 6자리
        if (newPassword == null || !newPassword.matches("^\\d{6}$")) {
            return Future.failedFuture(new BadRequestException("거래 비밀번호는 숫자 6자리여야 합니다."));
        }

        return emailVerificationRepository.getLatestByUserId(pool, userId)
            .compose(ev -> {
                if (ev == null || ev.verificationCode == null) {
                    return Future.failedFuture(new BadRequestException("이메일 인증이 필요합니다."));
                }
                if (ev.email == null || ev.email.isBlank()) {
                    return Future.failedFuture(new BadRequestException("이메일 인증이 필요합니다."));
                }
                if (!ev.verificationCode.equals(code) || ev.expiresAt == null || ev.expiresAt.isBefore(java.time.LocalDateTime.now())) {
                    return Future.failedFuture(new BadRequestException("인증 코드가 유효하지 않거나 만료되었습니다."));
                }

                String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
                return userRepository.updateTransactionPassword(pool, userId, hash).mapEmpty();
            });
    }

    /**
     * 로그인 비밀번호 변경 (이메일 인증 코드 기반)
     * 소셜 계정도 이메일 인증 후 비밀번호 설정 가능.
     */
    public Future<Void> changeLoginPassword(Long userId, String code, String newPassword) {
        // newPassword: 8자 이상
        if (newPassword == null || newPassword.length() < 8) {
            return Future.failedFuture(new BadRequestException("비밀번호는 8자 이상이어야 합니다."));
        }

        return userRepository.getUserById(pool, userId)
            .compose(user -> {
                if (user == null) {
                    return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                }

                return emailVerificationRepository.getLatestByUserId(pool, userId)
                    .compose(ev -> {
                        if (ev == null || ev.verificationCode == null) {
                            return Future.failedFuture(new BadRequestException("이메일 인증 정보가 없습니다."));
                        }
                        if (!ev.verificationCode.equals(code) || ev.expiresAt == null || ev.expiresAt.isBefore(java.time.LocalDateTime.now())) {
                            return Future.failedFuture(new BadRequestException("인증 코드가 유효하지 않거나 만료되었습니다."));
                        }

                        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
                        return userRepository.updateLoginPassword(pool, userId, hash).mapEmpty();
                    });
            });
    }
}
