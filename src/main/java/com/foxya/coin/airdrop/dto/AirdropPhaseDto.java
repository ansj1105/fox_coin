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
public class AirdropPhaseDto {
    private Long id;
    private Integer phase;
    private String status;
    private BigDecimal amount;
    private Boolean claimed;  // true: Release 완료, false/미존재: Release 버튼 노출 가능
    private LocalDateTime unlockDate;
    private Integer daysRemaining;
    /** 지급받은 날(Phase 생성일). 필터 기준용 */
    private LocalDateTime createdAt;
}

