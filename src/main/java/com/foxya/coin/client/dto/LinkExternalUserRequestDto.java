package com.foxya.coin.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkExternalUserRequestDto {
    private String provider;
    private String externalId;
    private String linkCode;
}
