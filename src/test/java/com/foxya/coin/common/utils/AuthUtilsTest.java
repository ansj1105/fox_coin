package com.foxya.coin.common.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUtilsTest {

    @Test
    void getUserIdOfSupportsStringClaim() {
        User user = mock(User.class);
        when(user.principal()).thenReturn(new JsonObject().put("userId", "123"));

        assertEquals(123L, AuthUtils.getUserIdOf(user));
    }

    @Test
    void getUserIdOfSupportsNumericClaim() {
        User user = mock(User.class);
        when(user.principal()).thenReturn(new JsonObject().put("userId", 456L));

        assertEquals(456L, AuthUtils.getUserIdOf(user));
    }

    @Test
    void getUserIdOfReturnsNullForMissingClaim() {
        User user = mock(User.class);
        when(user.principal()).thenReturn(new JsonObject());

        assertNull(AuthUtils.getUserIdOf(user));
    }
}
