package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 사용자 상태 Enum
 */
@Getter
public enum UserStatus {
    ACTIVE("ACTIVE", "활성"),
    INACTIVE("INACTIVE", "비활성"),
    SUSPENDED("SUSPENDED", "정지"),
    DELETED("DELETED", "삭제됨");
    
    private final String value;
    private final String description;
    
    UserStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static UserStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (UserStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}

