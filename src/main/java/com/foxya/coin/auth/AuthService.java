package com.foxya.coin.auth;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.UserService;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.auth.dto.LoginDto;
import com.foxya.coin.auth.dto.ApiKeyDto;
import com.foxya.coin.auth.dto.LoginResponseDto;
import com.foxya.coin.auth.dto.ApiKeyResponseDto;
import com.foxya.coin.auth.dto.TokenResponseDto;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

@Slf4j
public class AuthService extends BaseService {
    
    private final UserRepository userRepository;
    private final UserService userService;
    private final JWTAuth jwtAuth;
    private final JsonObject jwtConfig;
    private final SocialLinkRepository socialLinkRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    
    public AuthService(PgPool pool, UserRepository userRepository, UserService userService, JWTAuth jwtAuth, JsonObject jwtConfig,
                      SocialLinkRepository socialLinkRepository, PhoneVerificationRepository phoneVerificationRepository) {
        super(pool);
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
        this.socialLinkRepository = socialLinkRepository;
        this.phoneVerificationRepository = phoneVerificationRepository;
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
     */
    public Future<Void> logout(Long userId) {
        // TODO: Redis에 토큰 블랙리스트 추가
        
        log.info("User logged out: {}", userId);
        return Future.succeededFuture();
    }
    
    /**
     * 소셜 계정 연동
     */
    public Future<Boolean> linkSocial(Long userId, String provider, String token) {
        // TODO: 토큰 검증 및 사용자 정보 조회 (KAKAO, GOOGLE, EMAIL)
        String providerUserId = "provider_user_id_from_token"; // 실제로는 토큰에서 추출
        String email = provider.equals("EMAIL") ? token : null; // EMAIL의 경우 token이 이메일 주소
        
        return socialLinkRepository.createSocialLink(pool, userId, provider, providerUserId, email);
    }
    
    /**
     * 본인인증(휴대폰) 등록
     */
    public Future<Boolean> verifyPhone(Long userId, String phoneNumber, String verificationCode) {
        // TODO: 인증 코드 검증 로직 추가
        return phoneVerificationRepository.verifyPhone(pool, userId, phoneNumber, verificationCode);
    }
}

