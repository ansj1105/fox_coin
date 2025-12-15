package com.foxya.coin.referral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 현재 로그인한 사용자를 추천한 사람의 추천인 코드 응답 DTO
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CurrentReferralCodeDto {
    /**
     * 나를 추천한 사용자의 추천인 코드
     * 추천인이 없으면 null
     */
    private String referralCode;
}


