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
public class OfflinePaySettingsDto {
    private Boolean securityLevelHighEnabled;
    private Boolean faceIdSettingEnabled;
    private Boolean fingerprintSettingEnabled;
    private Boolean paymentOfflineEnabled;
    private Boolean paymentBleEnabled;
    private Boolean paymentNfcEnabled;
    private String paymentApprovalMode;
    private Boolean settlementAutoEnabled;
    private Integer settlementCycleMinutes;
    private Boolean storeOfflineEnabled;
    private Boolean storeBleEnabled;
    private Boolean storeNfcEnabled;
    private String storeMerchantLabel;
    private Boolean paymentCompletedAlertEnabled;
    private Boolean incomingRequestAlertEnabled;
    private Boolean failedAlertEnabled;
    private Boolean settlementCompletedAlertEnabled;
    private LocalDateTime updatedAt;
}
