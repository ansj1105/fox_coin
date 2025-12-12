package com.foxya.coin.banner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BannerListResponseDto {
    private List<BannerInfo> banners;
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BannerInfo {
        private Long id;
        private String title;
        private String imageUrl;
        private String linkUrl;
        private String position;
        private Boolean isActive;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }
}

