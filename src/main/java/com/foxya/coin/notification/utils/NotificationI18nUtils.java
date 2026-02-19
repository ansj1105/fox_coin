package com.foxya.coin.notification.utils;

import io.vertx.core.json.JsonObject;

public final class NotificationI18nUtils {

    private NotificationI18nUtils() {
    }

    public static String buildMetadata(String titleKey, String messageKey) {
        return buildMetadata(titleKey, messageKey, null);
    }

    public static String buildMetadata(String titleKey, String messageKey, JsonObject variables) {
        JsonObject metadata = new JsonObject();

        if (titleKey != null && !titleKey.isBlank()) {
            metadata.put("titleKey", titleKey);
        }
        if (messageKey != null && !messageKey.isBlank()) {
            metadata.put("messageKey", messageKey);
        }

        if (variables != null) {
            for (String key : variables.fieldNames()) {
                if ("titleKey".equals(key) || "messageKey".equals(key)) {
                    continue;
                }
                metadata.put(key, variables.getValue(key));
            }
        }

        return metadata.encode();
    }
}
