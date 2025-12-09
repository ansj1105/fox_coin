package com.foxya.coin.bonus.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBonus {
    private Long id;
    private Long userId;
    private String bonusType;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private Integer currentCount;
    private Integer maxCount;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

