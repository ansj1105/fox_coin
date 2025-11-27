package com.foxya.coin.common.utils;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import com.foxya.coin.common.enums.UserRole;
import com.foxya.coin.common.exceptions.ForbiddenException;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public abstract class AuthUtils {
    
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    
    /**
     * 요청 클라이언트의 권한을 확인하는 handler 반환
     */
    public static Handler<RoutingContext> hasRole(UserRole... roles) {
        return ctx -> {
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
        };
    }
    
    /**
     * 클라이언트 토큰에서 사용자 ID를 찾아서 반환
     */
    public static Long getUserIdOf(User user) {
        try {
            String userId = user.principal().getString(CLAIM_USER_ID);
            return userId != null ? Long.parseLong(userId) : null;
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
            return user.principal().getString(CLAIM_ROLE);
        } catch (Exception e) {
            log.error("Failed to get user role from token", e);
            return null;
        }
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
        JsonObject payload = new JsonObject()
            .put(CLAIM_USER_ID, userId.toString())
            .put(CLAIM_ROLE, role.name());
        
        return jwtAuth.generateToken(payload, new JWTOptions().setExpiresInSeconds(expiresInSeconds));
    }
    
    /**
     * Access Token 생성 (30분)
     */
    public static String generateAccessToken(JWTAuth jwtAuth, Long userId, UserRole role) {
        return generateToken(jwtAuth, userId, role, 1800); // 30분
    }
    
    /**
     * Refresh Token 생성 (10일)
     */
    public static String generateRefreshToken(JWTAuth jwtAuth, Long userId, UserRole role) {
        return generateToken(jwtAuth, userId, role, 864000); // 10일
    }
}

