package com.foxya.coin.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterRequestDto {
    private String email;
    private String code;
    private String password;
    private String referralCode;
    private String nickname;
    private String name;
    private String country;
    private String countryCode;
    private String gender;
}
