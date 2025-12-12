package com.foxya.coin.swap.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 스왑 정보 조회 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwapInfoDto {
    
    private BigDecimal fee; // 수수료 비율
    private BigDecimal spread; // 스프레드 비율
    private Map<String, BigDecimal> minSwapAmount; // 통화별 최소 스왑 금액
    private String priceSource; // 가격 소스 (예: "Oracle")
    private String note; // 참고 사항
}

