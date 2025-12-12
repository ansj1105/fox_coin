package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 거래 유형 Enum
 */
@Getter
public enum TransactionType {
    WITHDRAW("WITHDRAW", "출금"),
    TOKEN_DEPOSIT("TOKEN_DEPOSIT", "토큰 입금"),
    PAYMENT_DEPOSIT("PAYMENT_DEPOSIT", "결제 입금"),
    SWAP("SWAP", "스왑"),
    EXCHANGE("EXCHANGE", "환전");
    
    private final String value;
    private final String description;
    
    TransactionType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static TransactionType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TransactionType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

