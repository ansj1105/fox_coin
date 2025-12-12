package com.foxya.coin.banner.entities;

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
public class Banner {
    private Long id;
    private String title;
    private String imageUrl;
    private String linkUrl;
    private String position;
    private Boolean isActive;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long clickCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

