package com.foxya.coin.notice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class NoticeListResponseDto {
    private List<NoticeInfo> notices;
    private Long total;
    private Integer limit;
    private Integer offset;
    
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NoticeInfo {
        private Long id;
        private String title;
        private String content;
        private LocalDateTime createdAt;
        private Boolean isImportant;
    }
}

