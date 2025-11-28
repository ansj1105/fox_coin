package com.foxya.coin.referral.entities;

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
public class ReferralRelation {
    private Long id;
    private Long referrerId;
    private Long referredId;
    private Integer level;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}

