package com.foxya.coin.security.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflinePayAttestationChallengeDto {
    private String token;
    private String platform;
    private String sourceDeviceId;
    private LocalDateTime expiresAt;
}
