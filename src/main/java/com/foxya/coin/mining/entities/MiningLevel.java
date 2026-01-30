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
    /** 레벨별 일일 시청 가능 영상 수 (MINING_AND_LEVEL_SPEC) */
    private Integer dailyMaxVideos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

