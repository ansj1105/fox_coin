package com.foxya.coin.auth.dto;

import lombok.Getter;

@Getter
public class RecoveryResetRequestDto {
    private String address;
    private String chain;
    private String signature;
    private String newPassword;
}
