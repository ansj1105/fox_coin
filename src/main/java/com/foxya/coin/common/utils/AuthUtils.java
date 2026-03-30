package com.foxya.coin.common.utils;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.ForbiddenException;
import com.foxya.coin.common.DeviceGuard;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public abstract class AuthUtils {
    
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static volatile DeviceGuard deviceGuard;

    public static void configureDeviceGuard(DeviceGuard guard) {
        deviceGuard = guard;
    }
    
    /**
     * 요청 클라이언트의 권한을 확인하는 handler 반환
     */
    public static Handler<RoutingContext> hasRole(UserRole... roles) {
        return ctx -> {
            DeviceGuard guard = deviceGuard;
            if (guard != null) {
                guard.validate(ctx)
                    .onSuccess(v -> checkRole(ctx, roles))
                    .onFailure(ctx::fail);
                return;
            }
            checkRole(ctx, roles);
        };
    }

    private static void checkRole(RoutingContext ctx, UserRole... roles) {
        User user = ctx.user();
        if (user == null) {
            throw new ForbiddenException("인증이 필요합니다.");
        }

        String userRole = getUserRoleOf(user);
        boolean hasPermission = Arrays.stream(roles)
            .anyMatch(role -> role.name().equalsIgnoreCase(userRole));

        if (hasPermission) {
            ctx.next();
        } else {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
    }
    
    /**
     * 클라이언트 토큰에서 사용자 ID를 찾아서 반환
     */
    public static Long getUserIdOf(User user) {
        try {
            JsonObject principal = user != null ? user.principal() : null;
            if (principal == null) {
                return null;
            }

            Object rawUserId = principal.getValue(CLAIM_USER_ID);
            if (rawUserId instanceof Number number) {
                return number.longValue();
            }
            if (rawUserId instanceof String userId && !userId.isBlank()) {
                return Long.parseLong(userId);
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get user ID from token", e);
            return null;
        }
    }
    
    /**
     * 클라이언트 토큰에서 사용자 권한을 찾아서 반환
     */
    public static String getUserRoleOf(User user) {
        try {
            JsonObject principal = user != null ? user.principal() : null;
            if (principal == null) {
                return null;
            }

            Object rawRole = principal.getValue(CLAIM_ROLE);
            return rawRole != null ? String.valueOf(rawRole) : null;
        } catch (Exception e) {
            log.error("Failed to get user role from token", e);
            return null;
        }
    }

    /**
     * 클라이언트 토큰 타입(access/refresh) 반환
     */
    public static String getTokenTypeOf(User user) {
        try {
            JsonObject principal = user != null ? user.principal() : null;
            if (principal == null) {
                return null;
            }

            Object rawTokenType = principal.getValue(CLAIM_TOKEN_TYPE);
            return rawTokenType != null ? String.valueOf(rawTokenType) : null;
        } catch (Exception e) {
            log.error("Failed to get token type from token", e);
            return null;
        }
    }

    /**
     * Access 토큰 여부
     */
    public static boolean isAccessToken(User user) {
        return TOKEN_TYPE_ACCESS.equalsIgnoreCase(getTokenTypeOf(user));
    }

    /**
     * Refresh 토큰 여부
     */
    public static boolean isRefreshToken(User user) {
        return TOKEN_TYPE_REFRESH.equalsIgnoreCase(getTokenTypeOf(user));
    }
    
    /**
     * 관리자 권한 확인
     */
    public static boolean isAdmin(User user) {
        return UserRole.ADMIN.name().equalsIgnoreCase(getUserRoleOf(user));
    }
    
    /**
     * JWT 토큰 생성
     */
    public static String generateToken(JWTAuth jwtAuth, Long userId, UserRole role, int expiresInSeconds) {
        return generateToken(jwtAuth, userId, role, expiresInSeconds, null);
    }

    /**
     * JWT 토큰 생성 (tokenType 포함)
     */
    public static String generateToken(JWTAuth jwtAuth, Long userId, UserRole role, int expiresInSeconds, String tokenType) {
        JsonObject payload = new JsonObject()
            .put(CLAIM_USER_ID, userId.toString())
            .put(CLAIM_ROLE, role.name());
        if (tokenType != null && !tokenType.isBlank()) {
            payload.put(CLAIM_TOKEN_TYPE, tokenType);
        }
        
        return jwtAuth.generateToken(payload, new JWTOptions().setExpiresInSeconds(expiresInSeconds));
    }
    
    /** Access Token 기본 만료(초): 1시간. config 없을 때 사용 */
    private static final int DEFAULT_ACCESS_TOKEN_EXPIRE_SECONDS = 3600;
    /** Refresh Token 기본 만료(초): 10일. config 없을 때 사용 */
    private static final int DEFAULT_REFRESH_TOKEN_EXPIRE_SECONDS = 864000;

    /**
     * Access Token 생성 (만료 시간 지정)
     */
    public static String generateAccessToken(JWTAuth jwtAuth, Long userId, UserRole role, int expiresInSeconds) {
        return generateToken(jwtAuth, userId, role, expiresInSeconds, TOKEN_TYPE_ACCESS);
    }

    /**
     * Access Token 생성 (기본 1시간). config를 쓰지 않는 호출자용
     */
    public static String generateAccessToken(JWTAuth jwtAuth, Long userId, UserRole role) {
        return generateToken(jwtAuth, userId, role, DEFAULT_ACCESS_TOKEN_EXPIRE_SECONDS, TOKEN_TYPE_ACCESS);
    }

    /**
     * Refresh Token 생성 (만료 시간 지정)
     */
    public static String generateRefreshToken(JWTAuth jwtAuth, Long userId, UserRole role, int expiresInSeconds) {
        return generateToken(jwtAuth, userId, role, expiresInSeconds, TOKEN_TYPE_REFRESH);
    }

    /**
     * Refresh Token 생성 (기본 10일). config를 쓰지 않는 호출자용
     */
    public static String generateRefreshToken(JWTAuth jwtAuth, Long userId, UserRole role) {
        return generateToken(jwtAuth, userId, role, DEFAULT_REFRESH_TOKEN_EXPIRE_SECONDS, TOKEN_TYPE_REFRESH);
    }
}
