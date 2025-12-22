package com.foxya.coin.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foxya.coin.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationListResponseDto {
    private List<NotificationInfo> notifications;
    private Integer unreadCount;
    private Long total;
    private Integer limit;
    private Integer offset;
    
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NotificationInfo {
        private Long id;
        private NotificationType type;
        private String title;
        private String message;
        private Boolean isRead;
        private LocalDateTime createdAt;
        private Long relatedId;
        private JsonNode metadata;
    }
}


