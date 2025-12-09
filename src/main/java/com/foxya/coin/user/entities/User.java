package com.foxya.coin.user.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    private Long id;
    private String loginId;
    private String passwordHash;
    private String referralCode;
    private String status;
    private Integer level;
    private BigDecimal exp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

