package com.foxya.coin.transfer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 전송 내역 조회 응답 (OpenAPI TransferHistory 스키마)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferHistoryResponseDto {

    @JsonProperty("transfers")
    private List<TransferResponseDto> transfers;

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;
}
