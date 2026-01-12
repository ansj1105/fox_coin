package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutResponseDto {
    private String status;
    private String message;
}

