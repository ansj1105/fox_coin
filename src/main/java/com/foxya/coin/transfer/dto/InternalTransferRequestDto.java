package com.foxya.coin.transfer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 내부 전송 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTransferRequestDto {
    
    /**
     * 수신자 식별 타입
     * - ADDRESS: 지갑 주소로 전송
     * - REFERRAL_CODE: 추천인 코드로 전송
     * - USER_ID: 유저 ID로 전송 (관리자용)
     */
    @JsonProperty("receiverType")
    private String receiverType;
    
    /**
     * 수신자 식별 값
     * - receiverType이 ADDRESS면 지갑 주소
     * - receiverType이 REFERRAL_CODE면 추천인 코드
     * - receiverType이 USER_ID면 유저 ID
     */
    @JsonProperty("receiverValue")
    private String receiverValue;
    
    /**
     * 통화 코드 (예: FOXYA, USDT)
     */
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    /**
     * 전송 금액
     */
    @JsonProperty("amount")
    private BigDecimal amount;
    
    /**
     * 메모 (선택)
     */
    @JsonProperty("memo")
    private String memo;
    
    // 수신자 타입 상수
    public static final String RECEIVER_TYPE_ADDRESS = "ADDRESS";
    public static final String RECEIVER_TYPE_REFERRAL_CODE = "REFERRAL_CODE";
    public static final String RECEIVER_TYPE_USER_ID = "USER_ID";
}

