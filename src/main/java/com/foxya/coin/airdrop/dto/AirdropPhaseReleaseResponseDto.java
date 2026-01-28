package com.foxya.coin.airdrop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirdropPhaseReleaseResponseDto {
    private Long phaseId;
    private BigDecimal amount;
}
