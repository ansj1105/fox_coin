package com.foxya.coin.notification;

import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.device.DeviceRepository;
import com.foxya.coin.retry.FcmRetryJob;
import com.foxya.coin.retry.RetryQueuePublisher;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

/**
 * FCM 푸시 알림 발송.
 * 환경 변수 GOOGLE_APPLICATION_CREDENTIALS 또는 FCM_CREDENTIALS_PATH 에 서비스 계정 JSON 경로 지정.
 * 미설정 시 푸시 생략(알림 DB 인서트만 수행).
 */
@Slf4j
public class FcmService {

    private static final String ENV_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";
    private static final String ENV_CREDENTIALS_PATH = "FCM_CREDENTIALS_PATH";
    private static final String CFG_KEY_FCM_RETRY_MAX_RETRIES = "fcm_retry_max_retries";

    private final Vertx vertx;
    private final PgPool pool;
    private final DeviceRepository deviceRepository;
    private final AppConfigRepository appConfigRepository;
    private final RetryQueuePublisher retryQueuePublisher;
    private final int fallbackMaxRetries;
    private final boolean enabled;

    public FcmService(Vertx vertx, PgPool pool, DeviceRepository deviceRepository) {
        this(vertx, pool, deviceRepository, null, null);
    }

    public FcmService(Vertx vertx, PgPool pool, DeviceRepository deviceRepository, RetryQueuePublisher retryQueuePublisher, AppConfigRepository appConfigRepository) {
        this.vertx = vertx;
        this.pool = pool;
        this.deviceRepository = deviceRepository;
        this.retryQueuePublisher = retryQueuePublisher;
        this.appConfigRepository = appConfigRepository;
        this.fallbackMaxRetries = parseIntEnv("FCM_RETRY_MAX_RETRIES", FcmRetryJob.DEFAULT_MAX_RETRIES, 1, 20);
        this.enabled = initFirebase();
    }

    private record FcmSendResult(Set<String> invalidTokens, List<String> retryTokens) { }

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
                    log.info("FCM skipped: no active token(s) for userId={}", userId);
                    return Future.succeededFuture((Void) null);
                }
                return vertx.<FcmSendResult>executeBlocking(promise -> {
                    try {
                        FirebaseMessaging fcm = FirebaseMessaging.getInstance();
                        Set<String> invalidTokens = new LinkedHashSet<>();
                        List<String> retryTokens = new ArrayList<>();
                        for (String token : tokens) {
                            try {
                                fcm.send(buildMessage(token, title, body, data));
                            } catch (Exception e) {
                                if (isInvalidTokenError(e)) {
                                    invalidTokens.add(token);
                                } else {
                                    retryTokens.add(token);
                                }
                                log.warn("FCM send failed for token {}: {}", tokenPrefix(token), e.getMessage());
                            }
                        }
                        promise.complete(new FcmSendResult(invalidTokens, retryTokens));
                    } catch (Exception e) {
                        log.warn("FCM sendToUser failed: {}", e.getMessage());
                        promise.fail(e);
                    }
                }).compose(result -> invalidateTokens(userId, result.invalidTokens())
                    .compose(v -> enqueueRetryJobs(userId, title, body, data, result.retryTokens())))
                    .mapEmpty();
            })
            .recover(err -> {
                log.warn("FCM sendToUser recover: {}", err.getMessage());
                return Future.succeededFuture((Void) null);
            });
    }

    /**
     * Redis Stream 재시도 작업에서 꺼낸 FCM 발송 처리.
     * - 무효 토큰 오류는 토큰 제거 후 성공 처리(재시도 중단)
     * - 그 외 오류는 실패 반환(상위에서 재큐잉/DLQ 처리)
     */
    public Future<Void> processRetryJob(FcmRetryJob job) {
        if (job == null) {
            return Future.succeededFuture();
        }
        if (!enabled) {
            return Future.failedFuture("FCM disabled");
        }
        if (job.getToken() == null || job.getToken().isBlank()) {
            return Future.failedFuture("FCM retry token is empty");
        }
        return vertx.<Exception>executeBlocking(promise -> {
            try {
                FirebaseMessaging fcm = FirebaseMessaging.getInstance();
                Map<String, String> data = jsonToStringMap(job.getData());
                fcm.send(buildMessage(job.getToken(), job.getTitle(), job.getBody(), data));
                promise.complete(null);
            } catch (Exception e) {
                promise.complete(e);
            }
        }).compose(sendError -> {
            if (sendError == null) {
                return Future.succeededFuture();
            }
            if (isInvalidTokenError(sendError)) {
                return clearInvalidToken(job.getUserId(), job.getToken());
            }
            return Future.failedFuture(sendError);
        });
    }

    private Future<Void> invalidateTokens(Long userId, Set<String> invalidTokens) {
        if (invalidTokens == null || invalidTokens.isEmpty()) {
            return Future.succeededFuture((Void) null);
        }

        List<Future<?>> futures = new ArrayList<>();
        for (String token : invalidTokens) {
            Future<Integer> future = deviceRepository.clearFcmTokenByValue(pool, token)
                .map(rows -> {
                    if (rows > 0) {
                        log.info("FCM invalid token cleared: userId={}, tokenPrefix={}", userId, tokenPrefix(token));
                    }
                    return rows;
                });
            futures.add(future);
        }

        return Future.all(futures)
            .onFailure(err -> log.warn("Failed to clear invalid FCM tokens for userId={}: {}", userId, err.getMessage()))
            .mapEmpty();
    }

    private Future<Void> clearInvalidToken(Long userId, String token) {
        if (token == null || token.isBlank()) {
            return Future.succeededFuture();
        }
        return deviceRepository.clearFcmTokenByValue(pool, token)
            .map(rows -> {
                if (rows > 0) {
                    log.info("FCM invalid token cleared on retry. userId={}, tokenPrefix={}", userId, tokenPrefix(token));
                }
                return rows;
            })
            .recover(err -> {
                log.warn("Failed to clear invalid FCM token on retry. userId={}, tokenPrefix={}: {}",
                    userId, tokenPrefix(token), err.getMessage());
                return Future.succeededFuture(0);
            })
            .mapEmpty();
    }

    private Future<Void> enqueueRetryJobs(Long userId, String title, String body, Map<String, String> data, List<String> retryTokens) {
        if (retryQueuePublisher == null || retryTokens == null || retryTokens.isEmpty()) {
            return Future.succeededFuture();
        }

        return resolveRetryMaxRetries().compose(maxRetries -> {
            JsonObject dataJson = new JsonObject();
            if (data != null && !data.isEmpty()) {
                for (Map.Entry<String, String> e : data.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        dataJson.put(e.getKey(), e.getValue());
                    }
                }
            }

            List<Future<?>> enqueueFutures = new ArrayList<>();
            for (String token : retryTokens) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                FcmRetryJob job = FcmRetryJob.builder()
                    .userId(userId)
                    .token(token)
                    .title(title)
                    .body(body)
                    .data(dataJson.copy())
                    .retryCount(0)
                    .maxRetries(maxRetries)
                    .createdAt(LocalDateTime.now())
                    .build();
                enqueueFutures.add(retryQueuePublisher.enqueueFcmRetry(job));
            }
            if (enqueueFutures.isEmpty()) {
                return Future.succeededFuture();
            }
            return Future.all(enqueueFutures)
                .onSuccess(v -> log.info("FCM retry jobs enqueued. userId={}, count={}, maxRetries={}",
                    userId, enqueueFutures.size(), maxRetries))
                .onFailure(err -> log.error("Failed to enqueue FCM retry jobs. userId={}", userId, err))
                .mapEmpty();
        });
    }

    private Message buildMessage(String token, String title, String body, Map<String, String> data) {
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
        return msgBuilder.build();
    }

    private Map<String, String> jsonToStringMap(JsonObject json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new java.util.HashMap<>();
        for (String key : json.fieldNames()) {
            Object value = json.getValue(key);
            if (value != null) {
                map.put(key, String.valueOf(value));
            }
        }
        return map;
    }

    private boolean isInvalidTokenError(Exception e) {
        if (e instanceof FirebaseMessagingException firebaseMessagingException) {
            MessagingErrorCode code = firebaseMessagingException.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return true;
            }
            String msg = firebaseMessagingException.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                return lower.contains("requested entity was not found")
                    || lower.contains("not registered")
                    || lower.contains("invalid registration token");
            }
        }
        return false;
    }

    private String tokenPrefix(String token) {
        if (token == null || token.isBlank()) {
            return "(blank)";
        }
        return token.substring(0, Math.min(20, token.length())) + "...";
    }

    private static int parseIntEnv(String key, int defaultValue, int min, int max) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Future<Integer> resolveRetryMaxRetries() {
        if (appConfigRepository == null) {
            return Future.succeededFuture(fallbackMaxRetries);
        }
        return appConfigRepository.getByKey(pool, CFG_KEY_FCM_RETRY_MAX_RETRIES)
            .map(value -> {
                if (value == null || value.isBlank()) {
                    return fallbackMaxRetries;
                }
                try {
                    int parsed = Integer.parseInt(value.trim());
                    return Math.max(1, Math.min(20, parsed));
                } catch (Exception ignored) {
                    return fallbackMaxRetries;
                }
            })
            .recover(err -> {
                log.warn("Failed to read app_config({}), fallback to env/default. cause={}",
                    CFG_KEY_FCM_RETRY_MAX_RETRIES, err.getMessage());
                return Future.succeededFuture(fallbackMaxRetries);
            });
    }
}
