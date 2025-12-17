package com.foxya.coin.transfer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 외부 전송 (출금) 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTransferRequestDto {
    
    /**
     * 수신 주소 (외부 지갑 주소)
     */
    @JsonProperty("toAddress")
    private String toAddress;
    
    /**
     * 통화 코드 (예: KORI, USDT)
     */
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    /**
     * 전송 금액
     */
    @JsonProperty("amount")
    private BigDecimal amount;
    
    /**
     * 체인 (TRON, ETH 등)
     */
    @JsonProperty("chain")
    private String chain;
    
    /**
     * 메모 (선택)
     */
    @JsonProperty("memo")
    private String memo;
}

