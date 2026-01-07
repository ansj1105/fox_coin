package com.foxya.coin.airdrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 에어드랍 Phase DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirdropPhaseDto {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("phase")
    private Integer phase;
    
    @JsonProperty("status")
    private String status;              // RELEASED, PROCESSING
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("unlockDate")
    private LocalDateTime unlockDate;
    
    @JsonProperty("daysRemaining")
    private Integer daysRemaining;
}

