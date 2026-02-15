package com.foxya.coin.notification;

import com.foxya.coin.device.DeviceRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

/**
 * FCM 푸시 알림 발송.
 * 환경 변수 GOOGLE_APPLICATION_CREDENTIALS 또는 FCM_CREDENTIALS_PATH 에 서비스 계정 JSON 경로 지정.
 * 미설정 시 푸시 생략(알림 DB 인서트만 수행).
 */
@Slf4j
public class FcmService {

    private static final String ENV_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";
    private static final String ENV_CREDENTIALS_PATH = "FCM_CREDENTIALS_PATH";

    private final Vertx vertx;
    private final PgPool pool;
    private final DeviceRepository deviceRepository;
    private final boolean enabled;

    public FcmService(Vertx vertx, PgPool pool, DeviceRepository deviceRepository) {
        this.vertx = vertx;
        this.pool = pool;
        this.deviceRepository = deviceRepository;
        this.enabled = initFirebase();
    }

    private static boolean initFirebase() {
        try {
            if (FirebaseApp.getApps() != null && !FirebaseApp.getApps().isEmpty()) {
                return true;
            }
        } catch (Exception ignored) { }
        String path = System.getenv(ENV_CREDENTIALS_PATH);
        if (path == null || path.isBlank()) {
            path = System.getenv(ENV_CREDENTIALS);
        }
        if (path == null || path.isBlank()) {
            log.info("FCM: {} / {} not set, push disabled", ENV_CREDENTIALS, ENV_CREDENTIALS_PATH);
            return false;
        }
        try {
            try (FileInputStream is = new FileInputStream(path)) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(is))
                    .build();
                FirebaseApp.initializeApp(options);
            }
            log.info("FCM initialized with credentials: {}", path);
            return true;
        } catch (Exception e) {
            log.warn("FCM init failed, push disabled: {}", e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 해당 유저의 등록된 디바이스로 푸시 발송 (비동기, 실패해도 알림 생성에는 영향 없음).
     */
    public Future<Void> sendToUser(Long userId, String title, String body, Map<String, String> data) {
        if (!enabled || userId == null) {
            return Future.succeededFuture((Void) null);
        }
        return deviceRepository.getFcmTokensByUserId(pool, userId)
            .compose(tokens -> {
                if (tokens == null || tokens.isEmpty()) {
                    return Future.succeededFuture((Void) null);
                }
                return vertx.executeBlocking(promise -> {
                    try {
                        FirebaseMessaging fcm = FirebaseMessaging.getInstance();
                        for (String token : tokens) {
                            try {
                                Message.Builder msgBuilder = Message.builder()
                                    .setToken(token)
                                    .setNotification(Notification.builder()
                                        .setTitle(title)
                                        .setBody(body)
                                        .build());
                                if (data != null && !data.isEmpty()) {
                                    for (Map.Entry<String, String> e : data.entrySet()) {
                                        if (e.getKey() != null && e.getValue() != null) {
                                            msgBuilder.putData(e.getKey(), e.getValue());
                                        }
                                    }
                                }
                                fcm.send(msgBuilder.build());
                            } catch (Exception e) {
                                log.warn("FCM send failed for token {}: {}", token.substring(0, Math.min(20, token.length())) + "...", e.getMessage());
                            }
                        }
                        promise.complete();
                    } catch (Exception e) {
                        log.warn("FCM sendToUser failed: {}", e.getMessage());
                        promise.fail(e);
                    }
                }).mapEmpty();
            })
            .recover(err -> {
                log.warn("FCM sendToUser recover: {}", err.getMessage());
                return Future.succeededFuture((Void) null);
            });
    }
}
