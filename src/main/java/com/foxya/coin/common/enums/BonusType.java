package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 보너스 타입 Enum
 */
@Getter
public enum BonusType {
    SOCIAL_LINK("SOCIAL_LINK", "소셜 연동"),
    PHONE_VERIFICATION("PHONE_VERIFICATION", "전화번호 인증"),
    AD_WATCH("AD_WATCH", "광고 시청"),
    REFERRAL("REFERRAL", "추천인"),
    PREMIUM_SUBSCRIPTION("PREMIUM_SUBSCRIPTION", "프리미엄 구독"),
    REVIEW("REVIEW", "리뷰 작성"),
    AGENCY("AGENCY", "에이전시"),
    REFERRAL_CODE_INPUT("REFERRAL_CODE_INPUT", "추천인 코드 입력");
    
    private final String value;
    private final String description;
    
    BonusType(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static BonusType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (BonusType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

