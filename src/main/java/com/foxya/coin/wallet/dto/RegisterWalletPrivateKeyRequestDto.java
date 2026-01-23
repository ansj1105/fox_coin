package com.foxya.coin.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterWalletPrivateKeyRequestDto {
    private String currencyCode;
    private String chain;
    private String privateKey;
}
