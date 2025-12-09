package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LinkSocialRequestDto {
    private String provider; // KAKAO, GOOGLE, EMAIL
    private String token;
}

