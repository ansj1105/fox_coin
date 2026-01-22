package com.foxya.coin.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserTokenRequestDto {
    private String provider;
    private String externalId;
}
