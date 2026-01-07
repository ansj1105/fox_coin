package com.foxya.coin.airdrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 에어드랍 전송 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirdropTransferRequestDto {
    
    @JsonProperty("amount")
    private BigDecimal amount;
}

