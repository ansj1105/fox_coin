package com.foxya.coin.security.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflinePayAttestationEvidenceDto {
    private String platform;
    private String challengeToken;
    private LocalDateTime challengeExpiresAt;
    private List<String> androidCertificateChain;
    private String iosAppAttestKeyId;
    private String iosAppAttestAssertion;
    private String iosAppAttestClientData;
}
