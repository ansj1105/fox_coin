package com.foxya.coin.deposit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 토큰 입금 목록 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenDepositListResponseDto {
    
    @JsonProperty("deposits")
    private List<TokenDepositInfo> deposits;
    
    @JsonProperty("total")
    private Long total;
    
    @JsonProperty("limit")
    private Integer limit;
    
    @JsonProperty("offset")
    private Integer offset;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenDepositInfo {
        
        @JsonProperty("depositId")
        private String depositId;
        
        @JsonProperty("orderNumber")
        private String orderNumber;
        
        @JsonProperty("currencyCode")
        private String currencyCode;
        
        @JsonProperty("amount")
        private BigDecimal amount;
        
        @JsonProperty("network")
        private String network;
        
        @JsonProperty("senderAddress")
        private String senderAddress;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("createdAt")
        private LocalDateTime createdAt;
    }
}

