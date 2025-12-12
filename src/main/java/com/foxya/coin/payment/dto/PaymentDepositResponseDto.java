package com.foxya.coin.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 입금 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDepositResponseDto {
    
    @JsonProperty("depositId")
    private String depositId;
    
    @JsonProperty("orderNumber")
    private String orderNumber;
    
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("depositMethod")
    private String depositMethod;
    
    @JsonProperty("paymentAmount")
    private BigDecimal paymentAmount;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}

