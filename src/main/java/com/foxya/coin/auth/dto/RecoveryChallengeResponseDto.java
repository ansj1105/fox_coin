package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RecoveryChallengeResponseDto {
    private String message;
    private int expiresInSeconds;
}
