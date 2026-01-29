package com.foxya.coin.common.utils;

import com.foxya.coin.common.exceptions.SocialSignupExpiredException;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrorHandlerTest {

    @Mock
    private RoutingContext ctx;

    @Mock
    private HttpServerResponse response;

    @Test
    @DisplayName("SocialSignupExpiredException 시 400 + errorCode SOCIAL_SIGNUP_EXPIRED 반환")
    void handleSocialSignupExpiredException() {
        SocialSignupExpiredException failure = new SocialSignupExpiredException("소셜 가입 정보가 만료되었습니다.");
        when(ctx.failure()).thenReturn(failure);
        when(ctx.statusCode()).thenReturn(-1);
        when(ctx.response()).thenReturn(response);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
        when(response.end(anyString())).thenReturn(Future.succeededFuture());

        ErrorHandler.handle(ctx);

        verify(response).setStatusCode(400);
        verify(response).putHeader("Content-Type", "application/json");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).end(bodyCaptor.capture());
        JsonObject body = new JsonObject(bodyCaptor.getValue());
        assertThat(body.getInteger("code")).isEqualTo(400);
        assertThat(body.getString("status")).isEqualTo("ERROR");
        assertThat(body.getString("errorCode")).isEqualTo(SocialSignupExpiredException.ERROR_CODE);
        assertThat(body.getString("message")).contains("만료");
    }
}
