package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@AllArgsConstructor
@Builder
public class ApiKeyResponseDto {
    private String apiKey;
    private String name;
    private String description;
}

