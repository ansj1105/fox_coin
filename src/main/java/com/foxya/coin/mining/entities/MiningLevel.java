package com.foxya.coin.mining.entities;

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
public class MiningLevel {
    private Integer id;
    private Integer level;
    private BigDecimal dailyMaxMining;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

