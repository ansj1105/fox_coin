package com.foxya.coin.airdrop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirdropTransferResponseDto {
    private String transferId;
    private BigDecimal amount;
    private String currencyCode;
    private String status;
    private LocalDateTime createdAt;
}

