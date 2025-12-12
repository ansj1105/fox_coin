package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 레퍼럴 팀 탭 Enum
 */
@Getter
public enum ReferralTeamTab {
    MEMBERS("MEMBERS", "멤버"),
    REVENUE("REVENUE", "수익");
    
    private final String value;
    private final String description;
    
    ReferralTeamTab(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static ReferralTeamTab fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return MEMBERS;
        }
        for (ReferralTeamTab tab : values()) {
            if (tab.value.equals(value)) {
                return tab;
            }
        }
        return MEMBERS;
    }
}

