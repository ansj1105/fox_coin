package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 구독 패키지 타입 Enum
 */
@Getter
public enum SubscriptionPackageType {
    BASIC("BASIC", "기본"),
    PREMIUM("PREMIUM", "프리미엄"),
    VIP("VIP", "VIP");
    
    private final String value;
    private final String description;
    
    SubscriptionPackageType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static SubscriptionPackageType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SubscriptionPackageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

