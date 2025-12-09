package com.foxya.coin.notice.entities;

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
public class Notice {
    private Long id;
    private String title;
    private String content;
    private Boolean isImportant;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

