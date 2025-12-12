package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 소셜 로그인 제공자 Enum
 */
@Getter
public enum SocialProvider {
    KAKAO("KAKAO", "카카오"),
    GOOGLE("GOOGLE", "구글"),
    EMAIL("EMAIL", "이메일");
    
    private final String value;
    private final String description;
    
    SocialProvider(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static SocialProvider fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SocialProvider provider : values()) {
            if (provider.value.equals(value)) {
                return provider;
            }
        }
        return null;
    }
}

