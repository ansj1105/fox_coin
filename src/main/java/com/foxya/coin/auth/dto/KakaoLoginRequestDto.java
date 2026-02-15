package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KakaoLoginRequestDto {
    private String code;
    private String accessToken;
    private String platform;
    private String deviceId;
    private String deviceType;
    private String deviceOs;
    private String appVersion;
    private String clientIp;
    private String userAgent;
}
