package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RecoveryResetResponseDto {
    private String status;
    private String message;
}
