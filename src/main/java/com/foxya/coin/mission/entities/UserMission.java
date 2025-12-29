package com.foxya.coin.mission.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserMission {
    private Long id;
    private Long userId;
    private Long missionId;
    private LocalDate missionDate;
    private Integer currentCount;
    private LocalDateTime resetAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

