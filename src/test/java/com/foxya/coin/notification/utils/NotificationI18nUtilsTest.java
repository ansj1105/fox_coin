package com.foxya.coin.notification.utils;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationI18nUtilsTest {

    @Test
    void buildMetadata_withVariables_includesKeysAndVariables() {
        String encoded = NotificationI18nUtils.buildMetadata(
            "notifications.referralAirdrop.title",
            "notifications.referralAirdrop.message",
            new JsonObject()
                .put("amount", "2")
                .put("unlockDays", 7)
        );

        JsonObject metadata = new JsonObject(encoded);
        assertThat(metadata.getString("titleKey")).isEqualTo("notifications.referralAirdrop.title");
        assertThat(metadata.getString("messageKey")).isEqualTo("notifications.referralAirdrop.message");
        assertThat(metadata.getString("amount")).isEqualTo("2");
        assertThat(metadata.getInteger("unlockDays")).isEqualTo(7);
    }

    @Test
    void buildMetadata_ignoresReservedVariableKeys() {
        String encoded = NotificationI18nUtils.buildMetadata(
            "notifications.levelUp.title",
            "notifications.levelUp.message",
            new JsonObject()
                .put("titleKey", "malicious.override")
                .put("messageKey", "malicious.override")
                .put("newLevel", 5)
        );

        JsonObject metadata = new JsonObject(encoded);
        assertThat(metadata.getString("titleKey")).isEqualTo("notifications.levelUp.title");
        assertThat(metadata.getString("messageKey")).isEqualTo("notifications.levelUp.message");
        assertThat(metadata.getInteger("newLevel")).isEqualTo(5);
    }
}
