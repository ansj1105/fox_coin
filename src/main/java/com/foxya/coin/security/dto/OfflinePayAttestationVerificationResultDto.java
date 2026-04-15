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
public class OfflinePayAttestationVerificationResultDto {
    private String status;
    private String verificationMode;
    private String failureReason;
    private String attestationClassOverride;
    private String attestationVerdictOverride;
    private String serverVerifiedTrustLevelOverride;
    private LocalDateTime verifiedAt;

    public static OfflinePayAttestationVerificationResultDto skipped(String mode, String reason) {
        return OfflinePayAttestationVerificationResultDto.builder()
            .status("SKIPPED")
            .verificationMode(mode)
            .failureReason(reason)
            .build();
    }

    public static OfflinePayAttestationVerificationResultDto failed(String mode, String reason) {
        return OfflinePayAttestationVerificationResultDto.builder()
            .status("FAILED")
            .verificationMode(mode)
            .failureReason(reason)
            .build();
    }

    public static OfflinePayAttestationVerificationResultDto verified(
        String mode,
        String attestationClassOverride,
        String attestationVerdictOverride,
        String serverVerifiedTrustLevelOverride
    ) {
        return OfflinePayAttestationVerificationResultDto.builder()
            .status("VERIFIED")
            .verificationMode(mode)
            .attestationClassOverride(attestationClassOverride)
            .attestationVerdictOverride(attestationVerdictOverride)
            .serverVerifiedTrustLevelOverride(serverVerifiedTrustLevelOverride)
            .verifiedAt(LocalDateTime.now())
            .build();
    }
}
