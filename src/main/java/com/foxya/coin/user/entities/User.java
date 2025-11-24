package com.foxya.coin.user.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String phone;
    private String status;
    private String referralCode;
    private Long referredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

