package com.foxya.coin.user.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    private Long id;
    private String loginId;
    private String passwordHash;
    private String referralCode;
    private String status;
    private Integer level;
    private BigDecimal exp;
    private String transactionPasswordHash;
    private String countryCode;
    private String profileImageUrl;
    private String nickname;
    private String name;
    private String gender;
    private String phone;
    private Integer isTest;
    /** 추천인 등록 에어드랍 1회 지급 여부 (false=미지급, true=지급완료) */
    private Boolean referralAirdropRewarded;
    /** 앱 리뷰 보상 지급 완료 여부 (false=미완료, true=완료) */
    private Boolean appReviewRewarded;
    /** 리뷰 유도 팝업 다시 보지 않기 여부 */
    private Boolean reviewPromptDismissed;
    /** 리뷰 유도 팝업 마지막 노출 시각 */
    private LocalDateTime reviewPromptLastShownAt;
    /** 경고 여부 (0=해제, 1=경고). V41/coin_system V46 */
    private Integer isWarning;
    /** 채굴정지 여부 (0=해제, 1=정지). V41/coin_system V46 */
    private Integer isMiningSuspended;
    /** 계정차단 여부 (0=해제, 1=차단). V41/coin_system V46 */
    private Integer isAccountBlocked;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
