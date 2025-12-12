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
public class MiningHistory {
    private Long id;
    private Long userId;
    private Integer level;
    private BigDecimal amount;
    private String type;  // BROADCAST_PROGRESS, BROADCAST_WATCH
    private String status;  // COMPLETED, FAILED, CANCELLED
    private LocalDateTime createdAt;
}

