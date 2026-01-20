package com.foxya.coin.wallet.dto;

import lombok.Getter;

@Getter
public class RegisterWalletRequestDto {
    private String currencyCode;
    private String address;
    private String chain;
    private String signature;
}
