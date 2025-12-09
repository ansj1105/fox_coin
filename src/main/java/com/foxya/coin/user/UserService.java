package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.dto.LoginDto;
import com.foxya.coin.user.dto.LoginResponseDto;
import com.foxya.coin.user.dto.ReferralCodeResponseDto;
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
    
    public UserService(PgPool pool, UserRepository userRepository, JWTAuth jwtAuth, JsonObject jwtConfig, JsonObject frontendConfig) {
        super(pool);
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
        this.jwtConfig = jwtConfig;
        this.frontendBaseUrl = frontendConfig != null ? frontendConfig.getString("baseUrl", "http://localhost") : "http://localhost";
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
            .map(user -> {
                if (user == null) {
                    throw new com.foxya.coin.common.exceptions.NotFoundException("사용자를 찾을 수 없습니다.");
                }
                
                String referralCode = user.getReferralCode();
                if (referralCode == null || referralCode.isEmpty()) {
                    throw new com.foxya.coin.common.exceptions.BadRequestException("레퍼럴 코드가 없습니다.");
                }
                
                String referralLink = frontendBaseUrl + "/ref/" + referralCode;
                
                return ReferralCodeResponseDto.builder()
                    .referralCode(referralCode)
                    .referralLink(referralLink)
                    .build();
            });
    }
}

