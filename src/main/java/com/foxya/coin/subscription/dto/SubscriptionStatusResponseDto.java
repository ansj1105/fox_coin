package com.foxya.coin.subscription.dto;

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
public class SubscriptionStatusResponseDto {
    private Boolean isSubscribed;
    private LocalDateTime expiresAt;
    private String packageType;
}

