package com.foxya.coin.currency.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 통화 엔티티
 * DB 테이블: currency
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Currency {
    private Integer id;
    private String code;        // 통화 코드 (KRW, USDT, KORI 등)
    private String name;        // 통화 이름
    private String chain;       // 체인 (TRON, ETH, INTERNAL 등)
    private Boolean isActive;   // 활성화 여부
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

