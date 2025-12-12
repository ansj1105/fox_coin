package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 랭킹 범위 Enum
 */
@Getter
public enum RankingScope {
    REGIONAL("REGIONAL", "지역별"),
    GLOBAL("GLOBAL", "세계별");
    
    private final String value;
    private final String description;
    
    RankingScope(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static RankingScope fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return REGIONAL;
        }
        for (RankingScope scope : values()) {
            if (scope.value.equals(value)) {
                return scope;
            }
        }
        return REGIONAL;
    }
}

