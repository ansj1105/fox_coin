package com.foxya.coin.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalLinkCodeResponseDto {
    private String linkCode;
    private Integer expiresIn;
    private String provider;
}
