package com.foxya.coin.notification.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foxya.coin.notification.enums.NotificationType;
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
public class Notification {
    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private Boolean isRead;
    private Long relatedId;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


