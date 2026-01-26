package com.foxya.coin.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalLinkStatusResponseDto {
    private boolean linked;
    private String provider;
    private String externalId;
}
