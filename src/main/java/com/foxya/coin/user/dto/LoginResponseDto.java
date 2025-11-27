package com.foxya.coin.user.dto;

import lombok.*;

@Getter
@AllArgsConstructor
@Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String loginId;
}
