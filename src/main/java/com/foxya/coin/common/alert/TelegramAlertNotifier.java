package com.foxya.coin.common.alert;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class TelegramAlertNotifier {

    private final WebClient webClient;
    private final String botToken;
    private final String chatId;

    public TelegramAlertNotifier(WebClient webClient, String botToken, String chatId) {
        this.webClient = webClient;
        this.botToken = botToken == null ? "" : botToken.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
    }

    public boolean isEnabled() {
        return webClient != null && !botToken.isBlank() && !chatId.isBlank();
    }

    public Future<Void> sendMessage(String title, String body) {
        if (!isEnabled()) {
            return Future.succeededFuture();
        }

        JsonObject payload = new JsonObject()
            .put("chat_id", chatId)
            .put("text", formatMessage(title, body))
            .put("disable_web_page_preview", true);

        return webClient
            .post(443, "api.telegram.org", "/bot" + botToken + "/sendMessage")
            .ssl(true)
            .timeout(15_000)
            .sendJsonObject(payload)
            .compose(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return Future.succeededFuture();
                }

                String responseBody = response.bodyAsString();
                return Future.failedFuture(
                    "Telegram sendMessage failed with status " + response.statusCode() +
                        (responseBody == null || responseBody.isBlank() ? "" : ": " + responseBody)
                );
            });
    }

    private String formatMessage(String title, String body) {
        if (body == null || body.isBlank()) {
            return title == null ? "" : title.trim();
        }
        if (title == null || title.isBlank()) {
            return body.trim();
        }
        return title.trim() + "\n" + body.trim();
    }
}
