package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountResponseDto {
    private String status;
    private String message;
}

