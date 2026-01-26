package com.foxya.coin.client;

import io.vertx.core.Future;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import io.vertx.core.json.JsonObject;
import com.foxya.coin.client.dto.RefreshTokenRequestDto;
import com.foxya.coin.client.dto.TokenRequestDto;
import com.foxya.coin.client.dto.TokenResponseDto;
import com.foxya.coin.client.dto.UserTokenRequestDto;
import com.foxya.coin.client.dto.LinkExternalUserRequestDto;
import com.foxya.coin.client.entities.ApiKey;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.ConflictException;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.user.UserExternalIdRepository;
import com.foxya.coin.user.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
public class ClientService extends BaseService {
    
    private final ClientRepository clientRepository;
    private final UserExternalIdRepository userExternalIdRepository;
    private final UserRepository userRepository;
    private final JWTAuth jwtAuth;
    private final RedisAPI redisApi;
    
    // 클라이언트용 가상 사용자 ID (API Key 기반 인증용)
    private static final Long CLIENT_USER_ID = 0L;
    private static final String EXTERNAL_LINK_CODE_PREFIX = "external-link:code:";
    
    public ClientService(PgPool pool, ClientRepository clientRepository, UserExternalIdRepository userExternalIdRepository,
                         UserRepository userRepository, JWTAuth jwtAuth, RedisAPI redisApi) {
        super(pool);
        this.clientRepository = clientRepository;
        this.userExternalIdRepository = userExternalIdRepository;
        this.userRepository = userRepository;
        this.jwtAuth = jwtAuth;
        this.redisApi = redisApi;
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

    public Future<JsonObject> linkExternalUser(LinkExternalUserRequestDto dto) {
        if (dto == null) {
            return Future.failedFuture(new BadRequestException("요청 값이 필요합니다."));
        }
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("연동 기능을 사용할 수 없습니다."));
        }
        String normalizedProvider = dto.getProvider() != null ? dto.getProvider().trim().toUpperCase() : null;
        if (normalizedProvider == null || normalizedProvider.isBlank()) {
            return Future.failedFuture(new BadRequestException("provider가 필요합니다."));
        }
        String linkCode = dto.getLinkCode();
        if (linkCode == null || linkCode.isBlank()) {
            return Future.failedFuture(new BadRequestException("linkCode가 필요합니다."));
        }
        String key = EXTERNAL_LINK_CODE_PREFIX + linkCode;
        return redisApi.get(key)
            .compose(res -> {
                if (res == null || res.toString() == null || res.toString().isBlank()) {
                    return Future.failedFuture(new BadRequestException("연동 코드가 유효하지 않거나 만료되었습니다."));
                }
                JsonObject payload = new JsonObject(res.toString());
                Long userId = payload.getLong("userId");
                String provider = payload.getString("provider");
                if (userId == null || provider == null || provider.isBlank()) {
                    return Future.failedFuture(new BadRequestException("연동 코드 데이터가 올바르지 않습니다."));
                }
                if (!provider.equals(normalizedProvider)) {
                    return Future.failedFuture(new BadRequestException("provider가 일치하지 않습니다."));
                }
                return userExternalIdRepository.getUserIdByProviderAndExternalId(pool, normalizedProvider, dto.getExternalId())
                    .compose(existingUserId -> {
                        if (existingUserId != null && !existingUserId.equals(userId)) {
                            return Future.failedFuture(new ConflictException("이미 다른 사용자와 연동된 계정입니다."));
                        }
                        return userExternalIdRepository.upsertUserExternalId(pool, userId, normalizedProvider, dto.getExternalId())
                            .compose(v -> redisApi.del(List.of(key)).recover(e -> Future.succeededFuture()))
                            .map(v -> new JsonObject()
                                .put("userId", userId)
                                .put("provider", normalizedProvider)
                                .put("externalId", dto.getExternalId()));
                    });
            });
    }
}
