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
    
    @JsonProperty("senderId")
    private Long senderId;
    
    @JsonProperty("receiverId")
    private Long receiverId;
    
    @JsonProperty("toAddress")
    private String toAddress;           // 외부 전송 시
    
    @JsonProperty("currencyCode")
    private String currencyCode;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("fee")
    private BigDecimal fee;
    
    @JsonProperty("networkFee")
    private BigDecimal networkFee;      // 외부 전송 시
    
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

