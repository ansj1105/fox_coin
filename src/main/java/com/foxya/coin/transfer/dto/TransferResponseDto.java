package com.foxya.coin.transfer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 전송 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponseDto {
    
    @JsonProperty("transferId")
    private String transferId;
    
    @JsonProperty("transferType")
    private String transferType;        // INTERNAL, EXTERNAL
    
    @JsonProperty("transactionType")
    private String transactionType;     // WITHDRAW, TOKEN_DEPOSIT, PAYMENT_DEPOSIT, SWAP, EXCHANGE
    
    @JsonProperty("orderNumber")
    private String orderNumber;         // 주문번호
    
    @JsonProperty("senderId")
    private Long senderId;
    
    @JsonProperty("receiverId")
    private Long receiverId;
    
    @JsonProperty("toAddress")
    private String toAddress;           // 외부 전송 시
    
    @JsonProperty("senderAddress")
    private String senderAddress;       // 입금 시 송신 지갑 주소
    
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    @JsonProperty("fromCurrencyCode")
    private String fromCurrencyCode;    // 스왑/환전 시 FROM 토큰
    
    @JsonProperty("toCurrencyCode")
    private String toCurrencyCode;       // 스왑/환전 시 TO 토큰
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("fromAmount")
    private BigDecimal fromAmount;       // 스왑/환전 시 FROM 금액
    
    @JsonProperty("toAmount")
    private BigDecimal toAmount;         // 스왑/환전 시 TO 금액
    
    @JsonProperty("fee")
    private BigDecimal fee;
    
    @JsonProperty("networkFee")
    private BigDecimal networkFee;      // 외부 전송 시
    
    @JsonProperty("network")
    private String network;             // 네트워크 (Ether, TRON 등)
    
    @JsonProperty("depositMethod")
    private String depositMethod;       // 입금 방법 (CARD, BANK_TRANSFER, PAY)
    
    @JsonProperty("paymentAmount")
    private BigDecimal paymentAmount;    // 결제금액 (결제 입금 시)
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("txHash")
    private String txHash;              // 외부 전송 시
    
    @JsonProperty("memo")
    private String memo;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("completedAt")
    private LocalDateTime completedAt;
}

