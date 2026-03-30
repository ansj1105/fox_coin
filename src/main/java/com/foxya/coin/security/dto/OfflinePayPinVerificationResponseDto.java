package com.foxya.coin.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflinePayPinVerificationResponseDto {
    private Boolean verified;
    private String message;
    private String email;
    private Boolean emailVerified;
    private Boolean transactionPasswordRegistered;
    private Integer pinFailedAttempts;
    private Integer pinRemainingAttempts;
    private Boolean pinLocked;
    private LocalDateTime pinLockedAt;
}
