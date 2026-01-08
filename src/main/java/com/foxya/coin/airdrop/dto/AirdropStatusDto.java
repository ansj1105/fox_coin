package com.foxya.coin.airdrop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirdropStatusDto {
    private BigDecimal totalReceived;
    private BigDecimal totalReward;
    private Integer nextUnlockDays;
    private List<AirdropPhaseDto> phases;
}

