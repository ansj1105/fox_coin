package com.foxya.coin.user;

import com.foxya.coin.common.exceptions.BadRequestException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;

@Slf4j
public class ProfileImageModerationService {

    private static final Set<String> BLOCKED_LEVELS = Set.of("LIKELY", "VERY_LIKELY");
    private static final String MODERATION_FAIL_MESSAGE = "이미지 검수 서비스 오류로 업로드할 수 없습니다.";
    private static final String INAPPROPRIATE_IMAGE_MESSAGE = "선정적/폭력적 이미지로 판단되어 업로드할 수 없습니다.";
    private static final int MAX_ERROR_BODY_LOG_LENGTH = 1000;

    private final WebClient webClient;
    private final boolean enabled;
    private final String visionApiKey;

    public ProfileImageModerationService(WebClient webClient, boolean enabled, String visionApiKey) {
        this.webClient = webClient;
        this.enabled = enabled;
        this.visionApiKey = visionApiKey;
    }

    public Future<Void> validate(Path imagePath) {
        if (!enabled) {
            return Future.succeededFuture();
        }
        if (webClient == null || visionApiKey == null || visionApiKey.isBlank()) {
            log.warn("Profile image moderation is enabled but Google Vision API key/webClient is missing.");
            return Future.failedFuture(new BadRequestException(MODERATION_FAIL_MESSAGE));
        }

        return readAsBase64(imagePath)
            .compose(base64 -> requestSafeSearch(base64))
            .compose(safeSearch -> {
                if (isBlocked(safeSearch)) {
                    return Future.failedFuture(new BadRequestException(INAPPROPRIATE_IMAGE_MESSAGE));
                }
                return Future.<Void>succeededFuture();
            })
            .recover(throwable -> {
                if (throwable instanceof BadRequestException) {
                    log.warn("Profile image moderation rejected upload: {}", throwable.getMessage());
                    return Future.<Void>failedFuture(throwable);
                }
                log.warn("Profile image moderation failed", throwable);
                return Future.<Void>failedFuture(new BadRequestException(MODERATION_FAIL_MESSAGE));
            });
    }

    private Future<String> readAsBase64(Path imagePath) {
        io.vertx.core.Context context = Vertx.currentContext();
        if (context == null) {
            try {
                return Future.succeededFuture(Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath)));
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        }
        return context.owner().<String>executeBlocking(promise -> {
            try {
                String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
                promise.complete(base64);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    private Future<JsonObject> requestSafeSearch(String imageBase64) {
        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + visionApiKey;

        JsonObject request = new JsonObject()
            .put("requests", new JsonArray()
                .add(new JsonObject()
                    .put("image", new JsonObject().put("content", imageBase64))
                    .put("features", new JsonArray().add(new JsonObject().put("type", "SAFE_SEARCH_DETECTION")))));

        return webClient
            .postAbs(url)
            .as(BodyCodec.string())
            .sendJsonObject(request)
            .compose(response -> {
                String rawBody = response.body();
                JsonObject body = parseJsonObject(rawBody);
                if (response.statusCode() >= 400) {
                    String apiMessage = extractVisionErrorMessage(body);
                    log.warn(
                        "Vision SafeSearch request failed - status: {}, message: {}, body: {}",
                        response.statusCode(),
                        apiMessage != null ? apiMessage : "N/A",
                        truncateForLog(rawBody)
                    );
                    return Future.failedFuture(new RuntimeException("Vision SafeSearch HTTP error: status=" + response.statusCode()));
                }
                JsonArray responses = body.getJsonArray("responses", new JsonArray());
                if (responses.isEmpty()) {
                    return Future.failedFuture(new RuntimeException("Vision SafeSearch response is empty"));
                }
                Object firstValue = responses.getValue(0);
                JsonObject first = firstValue instanceof JsonObject ? (JsonObject) firstValue : new JsonObject();
                if (first.containsKey("error")) {
                    log.warn("Vision SafeSearch API error: {}", first.getJsonObject("error"));
                    return Future.failedFuture(new RuntimeException("Vision SafeSearch API error"));
                }
                JsonObject safeSearch = first.getJsonObject("safeSearchAnnotation", new JsonObject());
                return Future.succeededFuture(safeSearch);
            });
    }

    static JsonObject parseJsonObject(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new JsonObject();
        }
        try {
            return new JsonObject(rawBody);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    static String extractVisionErrorMessage(JsonObject body) {
        if (body == null) {
            return null;
        }
        JsonObject error = body.getJsonObject("error");
        if (error == null) {
            return null;
        }
        return error.getString("message");
    }

    private static String truncateForLog(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= MAX_ERROR_BODY_LOG_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_BODY_LOG_LENGTH) + "...(truncated)";
    }

    private boolean isBlocked(JsonObject safeSearch) {
        String adult = safeSearch.getString("adult", "UNKNOWN");
        String racy = safeSearch.getString("racy", "UNKNOWN");
        String violence = safeSearch.getString("violence", "UNKNOWN");
        return BLOCKED_LEVELS.contains(adult)
            || BLOCKED_LEVELS.contains(racy)
            || BLOCKED_LEVELS.contains(violence);
    }
}
