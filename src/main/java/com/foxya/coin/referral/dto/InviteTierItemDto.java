package com.foxya.coin.referral.dto;

import lombok.*;

/**
 * 친구 초대 티어 1개: N명 달성 시 +X% 채굴 보너스.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InviteTierItemDto {
    private Integer inviteCount;
    private Integer bonusPercent;
}
