package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 리뷰 플랫폼 Enum
 */
@Getter
public enum ReviewPlatform {
    GOOGLE_PLAY("GOOGLE_PLAY", "구글 플레이"),
    APP_STORE("APP_STORE", "앱 스토어");
    
    private final String value;
    private final String description;
    
    ReviewPlatform(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static ReviewPlatform fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ReviewPlatform platform : values()) {
            if (platform.value.equals(value)) {
                return platform;
            }
        }
        return null;
    }
}

