package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 블록체인 체인 타입 Enum
 */
@Getter
public enum ChainType {
    TRON("TRON", "트론"),
    ETH("ETH", "이더리움");
    
    private final String value;
    private final String description;
    
    ChainType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static ChainType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ChainType chainType : values()) {
            if (chainType.value.equals(value)) {
                return chainType;
            }
        }
        return null;
    }
}

