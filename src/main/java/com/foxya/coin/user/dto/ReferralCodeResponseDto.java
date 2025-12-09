package com.foxya.coin.user.dto;

import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReferralCodeResponseDto {
    private String referralCode;
    private String referralLink;
}

