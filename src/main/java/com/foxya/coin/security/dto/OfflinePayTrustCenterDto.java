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
    private String deviceRegistrationId;
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
}
