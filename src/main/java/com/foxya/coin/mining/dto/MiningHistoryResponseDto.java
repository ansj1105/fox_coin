package com.foxya.coin.mining.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MiningHistoryResponseDto {
    private List<MiningHistoryItem> items;
    private Long total;
    private BigDecimal totalAmount;       // 체굴 + 레퍼럴 수익 합계
    private BigDecimal totalMinedAmount;  // 체굴만 합계
    private BigDecimal totalReferralAmount; // 레퍼럴 수익만 합계
    private Integer limit;
    private Integer offset;
    
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MiningHistoryItem {
        private Long id;
        private Integer level;
        private String nickname;
        private BigDecimal amount;
        private Integer efficiency;
        private String type;
        private String status;
        private LocalDateTime createdAt;
    }
}
