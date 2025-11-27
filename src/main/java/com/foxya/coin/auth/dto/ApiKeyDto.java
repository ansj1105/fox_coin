package com.foxya.coin.auth.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiKeyDto {
    private String name;
    private String description;
}

