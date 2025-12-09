package com.foxya.coin.wallet.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Wallet {
    private Long id;
    private Long userId;
    private Integer currencyId;
    private String address;
    private BigDecimal balance;
    private BigDecimal lockedBalance;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

