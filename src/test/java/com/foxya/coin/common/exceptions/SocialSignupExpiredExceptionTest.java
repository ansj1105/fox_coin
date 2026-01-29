package com.foxya.coin.common.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialSignupExpiredExceptionTest {

    @Test
    @DisplayName("ERROR_CODE 상수 값 검증")
    void errorCodeConstant() {
        assertThat(SocialSignupExpiredException.ERROR_CODE).isEqualTo("SOCIAL_SIGNUP_EXPIRED");
    }

    @Test
    @DisplayName("생성자 - 메시지 전달 및 getMessage 검증")
    void constructorAndMessage() {
        String message = "소셜 가입 정보가 만료되었습니다.";
        SocialSignupExpiredException ex = new SocialSignupExpiredException(message);
        assertThat(ex).isInstanceOf(BadRequestException.class);
        assertThat(ex.getMessage()).isEqualTo(message);
    }
}
