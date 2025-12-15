package com.foxya.coin.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 이메일 설정 정보 DTO
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailInfoDto {
    /**
     * 현재 등록된 이메일 (없으면 null)
     */
    private String email;

    /**
     * 인증 여부
     */
    private boolean verified;
}


