package com.foxya.coin.common;

import com.foxya.coin.auth.AuthService;
import com.foxya.coin.common.exceptions.UnauthorizedException;
import com.foxya.coin.common.utils.AuthUtils;
import com.foxya.coin.device.DeviceRepository;
import com.foxya.coin.device.entities.Device;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.SqlClient;

public class DeviceGuard implements Handler<RoutingContext> {

    private static final String HEADER_DEVICE_ID = "X-Device-Id";
    private static final String HEADER_DEVICE_TYPE = "X-Device-Type";
    private static final String HEADER_DEVICE_OS = "X-Device-Os";

    private final SqlClient pool;
    private final DeviceRepository deviceRepository;
    private final AuthService authService;

    public DeviceGuard(SqlClient pool, DeviceRepository deviceRepository, AuthService authService) {
        this.pool = pool;
        this.deviceRepository = deviceRepository;
        this.authService = authService;
    }

    @Override
    public void handle(RoutingContext ctx) {
        validate(ctx)
            .onSuccess(v -> ctx.next())
            .onFailure(ctx::fail);
    }

    public Future<Void> validate(RoutingContext ctx) {
        if (ctx.user() == null) {
            return Future.failedFuture(new UnauthorizedException("인증이 필요합니다."));
        }

        String path = ctx.request().path();
        if (path.startsWith("/api/v1/admin") || path.startsWith("/api/v1/client")) {
            return Future.succeededFuture();
        }

        String token = extractBearerToken(ctx);
        if (token == null || token.isBlank()) {
            return Future.failedFuture(new UnauthorizedException("인증이 필요합니다."));
        }

        String deviceId = header(ctx, HEADER_DEVICE_ID);
        String deviceType = header(ctx, HEADER_DEVICE_TYPE);
        String deviceOs = header(ctx, HEADER_DEVICE_OS);
        if (deviceId == null || deviceType == null || deviceOs == null) {
            return Future.failedFuture(new UnauthorizedException("디바이스 정보가 필요합니다."));
        }

        Long userId = AuthUtils.getUserIdOf(ctx.user());
        if (userId == null) {
            return Future.failedFuture(new UnauthorizedException("인증이 필요합니다."));
        }
        String tokenType = AuthUtils.getTokenTypeOf(ctx.user());
        if (tokenType != null && !tokenType.isBlank() && !AuthUtils.isAccessToken(ctx.user())) {
            return Future.failedFuture(new UnauthorizedException("인증이 필요합니다."));
        }
        if (AuthUtils.isAdmin(ctx.user())) {
            return Future.succeededFuture();
        }

        return authService.isTokenBlacklisted(token)
            .compose(isBlacklisted -> {
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    return Future.failedFuture(new UnauthorizedException("세션이 만료되었습니다."));
                }
                return ensureActiveDevice(userId, deviceId, deviceType, deviceOs);
            });
    }

    private Future<Void> ensureActiveDevice(Long userId, String deviceId, String deviceType, String deviceOs) {
        String normalizedType = deviceType.toUpperCase();
        String normalizedOs = deviceOs.toUpperCase();
        // 웹 사용: deviceType=WEB 또는 deviceOs=WEB 이면 WEB 디바이스로 처리
        if ("WEB".equals(normalizedType) || "WEB".equals(normalizedOs)) {
            return deviceRepository.getActiveDeviceByUserAndType(pool, userId, "WEB")
                .compose(device -> validateDevice(device, deviceId));
        }
        if (!"IOS".equals(normalizedOs) && !"ANDROID".equals(normalizedOs)) {
            return Future.failedFuture(new UnauthorizedException("디바이스 정보가 올바르지 않습니다."));
        }
        return deviceRepository.getActiveDeviceByUserAndDeviceOs(pool, userId, normalizedOs)
            .compose(device -> validateDevice(device, deviceId));
    }

    private Future<Void> validateDevice(Device device, String deviceId) {
        if (device == null) {
            return Future.succeededFuture();
        }
        if (device.getDeviceId() == null || !device.getDeviceId().equals(deviceId)) {
            return Future.failedFuture(new UnauthorizedException("다른 기기에서 로그인했습니다. 다시 로그인해주세요."));
        }
        return Future.succeededFuture();
    }

    private static String header(RoutingContext ctx, String name) {
        String value = ctx.request().getHeader(name);
        return value != null ? value.trim() : null;
    }

    private static String extractBearerToken(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");
        if (authHeader == null) {
            return null;
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}
