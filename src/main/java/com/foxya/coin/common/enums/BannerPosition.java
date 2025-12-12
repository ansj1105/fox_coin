package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 배너 위치 Enum
 */
@Getter
public enum BannerPosition {
    RANKING_TOP("RANKING_TOP", "랭킹 상단"),
    HOME_TOP("HOME_TOP", "홈 상단"),
    HOME_MIDDLE("HOME_MIDDLE", "홈 중단"),
    HOME_BOTTOM("HOME_BOTTOM", "홈 하단");
    
    private final String value;
    private final String description;
    
    BannerPosition(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static BannerPosition fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return RANKING_TOP;
        }
        for (BannerPosition position : values()) {
            if (position.value.equals(value)) {
                return position;
            }
        }
        return RANKING_TOP;
    }
}

