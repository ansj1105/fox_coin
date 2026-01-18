package com.foxya.coin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginRequestDto {
    private String code;
    @JsonProperty("code_verifier")
    private String codeVerifier;
}
