package com.foxya.coin.level.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserLevelResponseDto {
    private Integer currentLevel;
    private BigDecimal currentExp;
    private BigDecimal nextLevelExp;
    private Double progress;
}

