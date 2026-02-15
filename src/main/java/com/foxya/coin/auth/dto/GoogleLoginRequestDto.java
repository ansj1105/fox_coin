package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginRequestDto {
    private String code;
    private String idToken;
    @JsonProperty("code_verifier")
    private String codeVerifier;
    private String platform;
    private String deviceId;
    private String deviceType;
    private String deviceOs;
    private String appVersion;
    private String clientIp;
    private String userAgent;
}
