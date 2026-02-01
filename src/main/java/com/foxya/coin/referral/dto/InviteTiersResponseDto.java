package com.foxya.coin.referral.dto;

import lombok.*;

import java.util.List;

/**
 * GET /api/v1/referrals/invite-tiers 응답.
 * 친구 초대 → 채굴 속도 보너스 티어 목록 + 현재 유저의 유효 직접 초대 수.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InviteTiersResponseDto {
    private List<InviteTierItemDto> tiers;
    private Integer validDirectReferralCount;
}
