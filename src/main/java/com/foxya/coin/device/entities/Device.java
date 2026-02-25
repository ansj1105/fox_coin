package com.foxya.coin.device.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Device {
    private Long id;
    private Long userId;
    private String deviceId;
    private String deviceType;
    private String deviceOs;
    private String fcmToken;   // FCM 푸시 알림용 등록 토큰
    private Boolean pushEnabled; // 디바이스별 푸시 수신 동의 여부
    private String appVersion;
    private String userAgent;
    private String lastIp;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
