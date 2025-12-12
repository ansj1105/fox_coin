package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 전송 상태 Enum
 */
@Getter
public enum TransferStatus {
    PENDING("PENDING", "대기중"),
    PROCESSING("PROCESSING", "처리중"),
    SUBMITTED("SUBMITTED", "블록체인 제출됨"),
    CONFIRMED("CONFIRMED", "컨펌 완료"),
    COMPLETED("COMPLETED", "완료"),
    FAILED("FAILED", "실패"),
    CANCELLED("CANCELLED", "취소");
    
    private final String value;
    private final String description;
    
    TransferStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public static TransferStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TransferStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}

