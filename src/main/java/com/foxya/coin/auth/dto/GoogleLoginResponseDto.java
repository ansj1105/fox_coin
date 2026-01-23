package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String loginId;
    private Boolean isNewUser;
    private Integer isTest;
}
