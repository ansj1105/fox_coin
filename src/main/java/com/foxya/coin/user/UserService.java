package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.auth.EmailVerificationRepository;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.utils.EmailService;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import com.foxya.coin.user.dto.LoginResponseDto;
import com.foxya.coin.user.dto.ReferralCodeResponseDto;
import com.foxya.coin.user.dto.EmailInfoDto;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

@Slf4j
public class UserService extends BaseService {
    
    private final UserRepository userRepository;
    private final JWTAuth jwtAuth;
    private final JsonObject jwtConfig;
    private final String frontendBaseUrl;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    
    public UserService(PgPool pool,
                       UserRepository userRepository,
                       JWTAuth jwtAuth,
                       JsonObject jwtConfig,
                       JsonObject frontendConfig,
                       EmailVerificationRepository emailVerificationRepository,
                       EmailService emailService) {
        super(pool);
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
        this.frontendBaseUrl = frontendConfig != null ? frontendConfig.getString("baseUrl", "http://localhost") : "http://localhost";
        this.emailVerificationRepository = emailVerificationRepository;
        this.emailService = emailService;
    }
    
    public Future<User> createUser(CreateUserDto dto) {
        // 비밀번호 해시
        String passwordHash = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());
        dto.setPasswordHash(passwordHash);
        
        return userRepository.createUser(pool, dto);
    }
    
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
                
                // JWT 토큰 생성 (기본 USER 권한)
                String accessToken = com.foxya.coin.common.utils.AuthUtils.generateAccessToken(
                    jwtAuth,
                    user.getId(),
                    com.foxya.coin.common.enums.UserRole.USER
                );
                
                String refreshToken = com.foxya.coin.common.utils.AuthUtils.generateRefreshToken(
                    jwtAuth,
                    user.getId(),
                    com.foxya.coin.common.enums.UserRole.USER
                );
                
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
    
    public Future<User> getUserById(Long id) {
        return userRepository.getUserById(pool, id);
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
     * 현재 로그인한 사용자의 이메일 정보 조회
     */
    public Future<EmailInfoDto> getEmailInfo(Long userId) {
        return emailVerificationRepository.getLatestByUserId(pool, userId)
            .map(ev -> {
                if (ev == null) {
                    // 이메일 정보가 없으면 email=null, verified=false
                    return EmailInfoDto.builder()
                        .email(null)
                        .verified(false)
                        .build();
                }
                return EmailInfoDto.builder()
                    .email(ev.email)
                    .verified(Boolean.TRUE.equals(ev.isVerified))
                    .build();
            });
    }

    /**
     * 이메일 인증 코드 발송
     */
    public Future<Void> sendEmailVerificationCode(Long userId, String email) {
        // 인증 코드 생성
        String code = emailService.generateVerificationCode();

        // 만료 시간: 현재 기준 10분 뒤
        return emailVerificationRepository.upsertVerification(pool, userId, email, code, com.foxya.coin.common.utils.DateUtils.now().plusMinutes(10))
            .compose(saved -> {
                if (!saved) {
                    return Future.failedFuture(new BadRequestException("이메일 인증 코드를 저장하지 못했습니다."));
                }
                // 이메일 발송 (현재는 로그만 출력)
                return emailService.sendVerificationCode(email, code);
            })
            .mapEmpty();
    }

    /**
     * 이메일 인증 및 등록
     */
    public Future<Void> verifyEmail(Long userId, String email, String code) {
        return emailVerificationRepository.verifyEmail(pool, userId, email, code)
            .compose(success -> {
                if (!success) {
                    return Future.failedFuture(new BadRequestException("인증 코드가 유효하지 않거나 만료되었습니다."));
                }
                return Future.succeededFuture();
            })
            .mapEmpty();
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
                    return Future.failedFuture(new BadRequestException("이메일 인증 정보가 없습니다."));
                }
                if (!ev.verificationCode.equals(code) || ev.expiresAt == null || ev.expiresAt.isBefore(java.time.LocalDateTime.now())) {
                    return Future.failedFuture(new BadRequestException("인증 코드가 유효하지 않거나 만료되었습니다."));
                }

                String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
                return userRepository.updateTransactionPassword(pool, userId, hash).mapEmpty();
            });
    }
}

