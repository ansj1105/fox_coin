package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginWithSeedRequestDto {
    private String address;
    private String chain;
    private String signature;
}
