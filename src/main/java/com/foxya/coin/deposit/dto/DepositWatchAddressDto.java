package com.foxya.coin.deposit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 입금 감시용 지갑 주소 (스캐너가 주기적으로 조회할 대상)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositWatchAddressDto {
    private Long userId;
    private Integer currencyId;
    private String address;
    private String network;  // TRON, ETH, BTC 등
}
