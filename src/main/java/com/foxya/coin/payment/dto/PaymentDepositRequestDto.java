package com.foxya.coin.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 결제 입금 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDepositRequestDto {
    
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("depositMethod")
    private String depositMethod;       // CARD, BANK_TRANSFER, PAY
    
    @JsonProperty("paymentAmount")
    private BigDecimal paymentAmount;    // 결제 금액 (원화 등)
}

