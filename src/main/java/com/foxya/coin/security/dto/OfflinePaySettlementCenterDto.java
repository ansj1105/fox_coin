package com.foxya.coin.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflinePaySettlementCenterDto {
    private LocalDateTime updatedAt;
    private List<OfflinePayActivityLogDto> logs;
}
