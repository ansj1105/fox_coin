package com.foxya.coin.auth.dto;

import lombok.Getter;

@Getter
public class RecoveryChallengeRequestDto {
    private String address;
    private String chain;
}
