package com.foxya.coin.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflinePayTrustCenterDto {
    private String platform;
    private String deviceName;
    private Boolean teeAvailable;
    private Boolean keySigningActive;
    private String keyProvider;
    private Boolean hardwareBackedKey;
    private Boolean userPresenceProtected;
    private String secureHardwareLevel;
    private String attestationClass;
    private String attestationVerdict;
    private String serverVerifiedTrustLevel;
    private String deviceRegistrationId;
    private String sourceDeviceId;
    private String deviceBindingKey;
    private String appVersion;
    private LocalDateTime collectedAt;
    private Boolean faceAvailable;
    private Boolean fingerprintAvailable;
    private String authBindingKey;
    private String lastVerifiedAuthMethod;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime lastSyncedAt;
    private String syncStatus;
    private LocalDateTime updatedAt;
    private List<OfflinePayTrustCenterLogDto> proofLogs;
    private List<OfflinePayTrustCenterLogDto> statusLogs;
    private OfflinePayAttestationEvidenceDto attestationEvidence;
    private String serverAttestationStatus;
    private String serverAttestationVerificationMode;
    private String serverAttestationFailureReason;
    private LocalDateTime serverAttestationVerifiedAt;
    // Derived contract fields — not stored in DB, computed on build
    private Boolean trustContractMet;
    private String contractRequirements;
    // Snapshot refresh hint — set at response time, not stored
    private LocalDateTime snapshotRefreshedAt;
    private Long staleAfterMs;
}
