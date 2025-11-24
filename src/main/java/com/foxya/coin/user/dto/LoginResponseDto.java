package com.foxya.coin.user.dto;

import lombok.*;

@Getter
@AllArgsConstructor
@Builder
public class LoginResponseDto {
    private String token;
    private Long userId;
    private String username;
}

