package com.foxya.coin.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RegisterWalletChallengeResponseDto {
    private String message;
    private int expiresInSeconds;
}
