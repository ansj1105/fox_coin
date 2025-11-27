package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@AllArgsConstructor
@Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
}

