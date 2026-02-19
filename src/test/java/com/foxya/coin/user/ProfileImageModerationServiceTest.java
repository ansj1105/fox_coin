package com.foxya.coin.user;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageModerationServiceTest {

    @Test
    void parseJsonObject_whenResponseIsValidJson_returnsJsonObject() {
        JsonObject body = ProfileImageModerationService.parseJsonObject("{\"error\":{\"message\":\"Forbidden\"}}");

        assertThat(body.getJsonObject("error").getString("message")).isEqualTo("Forbidden");
    }

    @Test
    void parseJsonObject_whenResponseIsInvalidJson_returnsEmptyJsonObject() {
        JsonObject body = ProfileImageModerationService.parseJsonObject("<html>403 Forbidden</html>");

        assertThat(body.isEmpty()).isTrue();
    }

    @Test
    void extractVisionErrorMessage_whenErrorMessageExists_returnsMessage() {
        String message = ProfileImageModerationService.extractVisionErrorMessage(
            new JsonObject().put("error", new JsonObject().put("message", "Request had insufficient authentication scopes."))
        );

        assertThat(message).isEqualTo("Request had insufficient authentication scopes.");
    }

    @Test
    void extractVisionErrorMessage_whenNoErrorField_returnsNull() {
        String message = ProfileImageModerationService.extractVisionErrorMessage(new JsonObject());

        assertThat(message).isNull();
    }
}
