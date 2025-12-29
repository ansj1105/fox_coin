package com.foxya.coin.mission.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foxya.coin.mission.enums.MissionType;
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
public class Mission {
    private Long id;
    private String title;
    private String description;
    private MissionType type;
    private Integer requiredCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

