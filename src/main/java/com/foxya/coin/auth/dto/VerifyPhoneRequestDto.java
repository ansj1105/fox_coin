package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VerifyPhoneRequestDto {
    private String phoneNumber;
    private String verificationCode;
}

