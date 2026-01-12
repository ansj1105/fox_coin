package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountRequestDto {
    private String password;
}

