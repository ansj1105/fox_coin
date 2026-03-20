package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 거래 유형 Enum
 */
@Getter
public enum TransactionType {
    WITHDRAW("WITHDRAW", "출금"),
    TOKEN_DEPOSIT("TOKEN_DEPOSIT", "토큰 입금"),
    REFERRAL_REWARD("REFERRAL_REWARD", "래퍼럴 수익"),
    AIRDROP_TRANSFER("AIRDROP_TRANSFER", "에어드랍 전송"),
    PAYMENT_DEPOSIT("PAYMENT_DEPOSIT", "결제 입금"),
    SWAP("SWAP", "스왑"),
    EXCHANGE("EXCHANGE", "포인트 환전"),
    OFFLINE_PAY_SETTLEMENT("OFFLINE_PAY_SETTLEMENT", "오프라인 정산"),
    OFFLINE_PAY_CONFLICT("OFFLINE_PAY_CONFLICT", "오프라인 충돌");
    
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
