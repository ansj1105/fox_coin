package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 전송 타입 Enum
 */
@Getter
public enum TransferType {
    INTERNAL("INTERNAL", "내부 전송"),
    REFERRAL_REWARD("REFERRAL_REWARD", "추천인 보상"),
    ADMIN_GRANT("ADMIN_GRANT", "관리자 지급");
    
    private final String value;
    private final String description;
    
    TransferType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static TransferType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TransferType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

