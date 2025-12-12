package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 채굴 유형 Enum
 */
@Getter
public enum MiningType {
    BROADCAST_PROGRESS("BROADCAST_PROGRESS", "방송진행"),
    BROADCAST_WATCH("BROADCAST_WATCH", "방송시청");
    
    private final String value;
    private final String description;
    
    MiningType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static MiningType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MiningType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

