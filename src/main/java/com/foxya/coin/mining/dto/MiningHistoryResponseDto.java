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
    private BigDecimal totalAmount;
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
        private String type;
        private String status;
        private LocalDateTime createdAt;
    }
}

