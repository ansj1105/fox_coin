package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 입금 방법 Enum
 */
@Getter
public enum DepositMethod {
    CARD("CARD", "카드"),
    BANK_TRANSFER("BANK_TRANSFER", "계좌이체"),
    PAY("PAY", "페이");
    
    private final String value;
    private final String description;
    
    DepositMethod(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static DepositMethod fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (DepositMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        return null;
    }
}

