package com.foxya.coin.user.dto;

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
    /** 경고 여부 */
    private Boolean warning;
    /** 채굴정지 여부 */
    private Boolean miningSuspended;
    /** 계정차단 여부 */
    private Boolean accountBlocked;
}
