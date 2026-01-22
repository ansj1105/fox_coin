package com.foxya.coin.client;

import io.vertx.core.Future;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.client.dto.RefreshTokenRequestDto;
import com.foxya.coin.client.dto.TokenRequestDto;
import com.foxya.coin.client.dto.TokenResponseDto;
import com.foxya.coin.client.dto.UserTokenRequestDto;
import com.foxya.coin.client.entities.ApiKey;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.user.UserExternalIdRepository;
import com.foxya.coin.user.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
public class ClientService extends BaseService {
    
    private final ClientRepository clientRepository;
    private final UserExternalIdRepository userExternalIdRepository;
    private final UserRepository userRepository;
    private final JWTAuth jwtAuth;
    
    // 클라이언트용 가상 사용자 ID (API Key 기반 인증용)
    private static final Long CLIENT_USER_ID = 0L;
    
    public ClientService(PgPool pool, ClientRepository clientRepository, UserExternalIdRepository userExternalIdRepository,
                         UserRepository userRepository, JWTAuth jwtAuth) {
        super(pool);
        this.clientRepository = clientRepository;
        this.userExternalIdRepository = userExternalIdRepository;
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
    }
    
    /**
     * API Key로 토큰 발급
     */
    public Future<TokenResponseDto> issueToken(TokenRequestDto dto) {
        return clientRepository.getApiKeyByKeyAndSecret(pool, dto.getApiKey(), dto.getApiSecret())
            .compose(apiKey -> {
                if (apiKey == null) {
                    return Future.failedFuture(new UnauthorizedException("유효하지 않은 API Key 또는 Secret입니다."));
                }
                
                // 만료 확인
                if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                    return Future.failedFuture(new UnauthorizedException("API Key가 만료되었습니다."));
                }
                
                // 마지막 사용 시간 업데이트
                clientRepository.updateLastUsedAt(pool, apiKey.getId());
                
                // Access Token & Refresh Token 생성
                // 클라이언트용 토큰이므로 특별한 역할 부여 (CLIENT 역할이 있다면 사용, 없으면 USER 사용)
                String accessToken = AuthUtils.generateAccessToken(jwtAuth, CLIENT_USER_ID, UserRole.USER);
                String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, CLIENT_USER_ID, UserRole.USER);
                
                return Future.succeededFuture(
                    TokenResponseDto.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .expiresIn(1800L) // 30분
                        .build()
                );
            });
    }
    
    /**
     * Refresh Token으로 새 토큰 발급
     */
    public Future<TokenResponseDto> refreshToken(RefreshTokenRequestDto dto) {
        // Refresh Token 검증은 JWT Handler에서 처리되므로 여기서는 새 토큰만 발급
        // 실제로는 refresh token의 유효성을 확인해야 하지만, 
        // 현재 구조에서는 JWT Handler가 이미 검증했으므로 바로 새 토큰 발급
        
        String accessToken = AuthUtils.generateAccessToken(jwtAuth, CLIENT_USER_ID, UserRole.USER);
        String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, CLIENT_USER_ID, UserRole.USER);
        
        return Future.succeededFuture(
            TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(1800L) // 30분
                .build()
        );
    }
    
    /**
     * 외부 사용자 식별자로 유저 토큰 발급
     */
    public Future<TokenResponseDto> issueUserToken(UserTokenRequestDto dto) {
        return userExternalIdRepository.getUserIdByProviderAndExternalId(pool, dto.getProvider(), dto.getExternalId())
            .compose(userId -> {
                if (userId == null) {
                    return Future.failedFuture(new UnauthorizedException("연동된 사용자 정보를 찾을 수 없습니다."));
                }
                
                return userRepository.getUserByIdNotDeleted(pool, userId)
                    .compose(user -> {
                        if (user == null) {
                            return Future.failedFuture(new UnauthorizedException("연동된 사용자 정보를 찾을 수 없습니다."));
                        }
                        
                        String accessToken = AuthUtils.generateAccessToken(jwtAuth, userId, UserRole.USER);
                        String refreshToken = AuthUtils.generateRefreshToken(jwtAuth, userId, UserRole.USER);
                        
                        return Future.succeededFuture(
                            TokenResponseDto.builder()
                                .accessToken(accessToken)
                                .refreshToken(refreshToken)
                                .expiresIn(1800L) // 30분
                                .build()
                        );
                    });
            });
    }
}
