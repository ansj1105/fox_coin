package com.foxya.coin.currency.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Currency {
    private Integer id;
    private String name;
    private String symbol;
    private String chain;
    private String contractAddress;
    private Integer decimals;
    private String status;
    private BigDecimal withdrawFee;
    private BigDecimal minWithdraw;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

