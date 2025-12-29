package com.foxya.coin.mission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foxya.coin.mission.enums.MissionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MissionResponseDto {
    private Long id;
    private String title;
    private String description;
    private Integer currentCount;
    private Integer requiredCount;
    private Boolean isCompleted;
    private MissionType type;
}

