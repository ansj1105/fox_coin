package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * POST /api/v1/auth/refresh 응답.
 * - accessToken: 항상 포함
 * - refreshToken: 로테이션 시에만 포함, 미갱신 시 null
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponseDto {
    private String accessToken;
    private String refreshToken;
}
