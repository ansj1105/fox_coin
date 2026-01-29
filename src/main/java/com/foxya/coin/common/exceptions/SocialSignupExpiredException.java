package com.foxya.coin.common.exceptions;

/**
 * 소셜 가입 정보(Redis)가 만료되었을 때 사용.
 * 클라이언트는 errorCode "SOCIAL_SIGNUP_EXPIRED"로 스토리지 비우고 소셜 로그인 재시도 가능.
 */
public class SocialSignupExpiredException extends BadRequestException {
    public static final String ERROR_CODE = "SOCIAL_SIGNUP_EXPIRED";

    public SocialSignupExpiredException(String message) {
        super(message);
    }
}
