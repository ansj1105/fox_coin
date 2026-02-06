package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String loginId;
    private Integer isTest;
    /** 앱 최소 필요 버전 (구버전 업데이트 유도용). 없으면 null */
    private String minAppVersion;
}
