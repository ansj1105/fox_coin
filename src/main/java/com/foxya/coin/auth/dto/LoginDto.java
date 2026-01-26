package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginDto {
    private String loginId;
    private String password;
    private String deviceId;
    private String deviceType;
    private String deviceOs;
    private String appVersion;
    private String clientIp;
    private String userAgent;
}
