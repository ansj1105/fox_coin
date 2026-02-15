package com.foxya.coin.mining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiningInfoResponseDto {
    private BigDecimal todayMiningAmount;
    private BigDecimal totalBalance;
    private Integer bonusEfficiency;
    private String remainingTime;
    private Boolean isActive;
    private BigDecimal dailyMaxMining;
    private Integer currentLevel;
    private BigDecimal nextLevelRequired;
    private Integer adWatchCount;
    private Integer maxAdWatchCount;
    /** 현재 채굴 세션 종료 시각 (ISO-8601). 이 시각 이전에는 부스터 버튼 비활성화 (1시간 후에만 다음 영상 시청 가능) */
    private String miningUntil;
    /** 현재 채굴 속도 (시간당 KORI). 활성 세션이 있으면 rate_per_hour, 없으면 0 */
    private BigDecimal ratePerHour;
    /** 초대 인원 수에 따른 채굴 보너스 배율 (1.0 ~ 1.22). 영상 1회당 채굴량에 곱함 */
    private BigDecimal inviteBonusMultiplier;
    /** 유효 직접 초대 수 (이메일 인증+채굴 기록 있는 referred 수). 채굴 보너스 % 구간용 */
    private Integer validDirectReferralCount;
    /** 경고 여부 */
    private Boolean warning;
    /** 채굴정지 여부 */
    private Boolean miningSuspended;
    /** 계정차단 여부 */
    private Boolean accountBlocked;
}
