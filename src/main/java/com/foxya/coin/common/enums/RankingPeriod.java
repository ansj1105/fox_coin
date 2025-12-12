package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 랭킹 기간 Enum
 */
@Getter
public enum RankingPeriod {
    ALL("ALL", "전체"),
    TODAY("TODAY", "오늘"),
    WEEK("WEEK", "이번 주"),
    MONTH("MONTH", "이번 달"),
    YEAR("YEAR", "올해");
    
    private final String value;
    private final String description;
    
    RankingPeriod(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static RankingPeriod fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return TODAY;
        }
        for (RankingPeriod period : values()) {
            if (period.value.equals(value)) {
                return period;
            }
        }
        return TODAY;
    }
}

