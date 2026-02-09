package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppleLoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String loginId;
    private Boolean isNewUser;
    private String signupToken;
    private Integer isTest;
    /** 앱 최소 필요 버전 (구버전 업데이트 유도용). 없으면 null */
    private String minAppVersion;
}
