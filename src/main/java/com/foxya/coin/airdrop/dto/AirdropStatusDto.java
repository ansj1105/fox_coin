package com.foxya.coin.airdrop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 에어드랍 상태 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AirdropStatusDto {
    
    @JsonProperty("totalReceived")
    private BigDecimal totalReceived;
    
    @JsonProperty("totalReward")
    private BigDecimal totalReward;
    
    @JsonProperty("nextUnlockDays")
    private Integer nextUnlockDays;
    
    @JsonProperty("phases")
    private List<AirdropPhaseDto> phases;
}

