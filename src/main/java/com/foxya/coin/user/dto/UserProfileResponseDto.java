package com.foxya.coin.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponseDto {
    private Long id;
    private String loginId;
    private String nickname;
    private String profileImageUrl;
    private Integer level;
    private String referralCode;
    /** 국가 코드 (예: KR, US). 없으면 null → 타인 프로필에서 국가 행 미표시 */
    private String country;
}
