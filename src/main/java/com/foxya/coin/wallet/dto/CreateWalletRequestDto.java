package com.foxya.coin.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 지갑 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletRequestDto {
    
    /**
     * 통화 코드 (예: KRWT, USDT, ETH, KRO 등)
     */
    @JsonProperty("currencyCode")
    private String currencyCode;
}

