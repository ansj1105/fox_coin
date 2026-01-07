package com.foxya.coin.airdrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 에어드랍 전송 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirdropTransferResponseDto {
    
    @JsonProperty("transferId")
    private String transferId;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}

