package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequestDto {
    private String deviceId;
    private String deviceType;
    private String deviceOs;
    private String appVersion;
}
