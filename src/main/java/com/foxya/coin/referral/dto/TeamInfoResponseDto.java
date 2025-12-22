package com.foxya.coin.referral.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamInfoResponseDto {
    private SummaryInfo summary;
    private List<MemberInfo> members;
    private List<RevenueInfo> revenues;
    private Long total;
    private Integer limit;
    private Integer offset;
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SummaryInfo {
        private BigDecimal totalRevenue;
        private BigDecimal todayRevenue;
        private BigDecimal weekRevenue;
        private BigDecimal monthRevenue;
        private BigDecimal yearRevenue;
        private BigDecimal periodRevenue; // 선택된 period에 따른 수익
        private Long totalMembers;
        private Long newMembersToday;
    }
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemberInfo {
        private Long userId;
        private Integer level;
        private String nickname;
        private LocalDateTime registeredAt;
    }
    
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RevenueInfo {
        private Long userId;
        private Integer level;
        private String nickname;
        private LocalDateTime date;
        private BigDecimal todayRevenue;
        private BigDecimal totalRevenue;
    }
}

