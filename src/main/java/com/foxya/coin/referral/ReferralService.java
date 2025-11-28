package com.foxya.coin.referral;

import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.referral.dto.ReferralStatsDto;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReferralService extends BaseService {
    
    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    
    public ReferralService(PgPool pool, ReferralRepository referralRepository, UserRepository userRepository) {
        super(pool);
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * 레퍼럴 코드 등록
     */
    public Future<Void> registerReferralCode(Long userId, String referralCode) {
        // 1. 이미 레퍼럴 관계가 있는지 확인
        return referralRepository.existsReferralRelation(pool, userId)
            .compose(exists -> {
                if (exists) {
                    return Future.failedFuture(new BadRequestException("이미 레퍼럴 코드가 등록되어 있습니다."));
                }
                
                // 2. 레퍼럴 코드로 추천인 찾기
                return userRepository.getUserByReferralCode(pool, referralCode);
            })
            .compose(referrer -> {
                if (referrer == null) {
                    return Future.failedFuture(new BadRequestException("유효하지 않은 레퍼럴 코드입니다."));
                }
                
                if (referrer.getId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("자신의 레퍼럴 코드는 등록할 수 없습니다."));
                }
                
                // 3. 레퍼럴 관계 생성 (level 1 = 직접 추천)
                return referralRepository.createReferralRelation(pool, referrer.getId(), userId, 1);
            })
            .compose(relation -> {
                // 4. 추천인의 통계 업데이트
                return updateReferrerStats(relation.getReferrerId());
            })
            .mapEmpty();
    }
    
    /**
     * 레퍼럴 통계 조회
     */
    public Future<ReferralStatsDto> getReferralStats(Long userId) {
        return Future.all(
            referralRepository.getDirectReferralCount(pool, userId),
            referralRepository.getActiveTeamCount(pool, userId),
            referralRepository.getOrCreateStats(pool, userId)
        ).map(result -> {
            Integer directCount = result.resultAt(0);
            Integer activeTeamCount = result.resultAt(1);
            var stats = result.<com.foxya.coin.referral.entities.ReferralStats>resultAt(2);
            
            return ReferralStatsDto.builder()
                .userId(userId)
                .directCount(directCount)
                .activeTeamCount(activeTeamCount)
                .totalReward(stats.getTotalReward())
                .todayReward(stats.getTodayReward())
                .build();
        });
    }
    
    /**
     * 추천인의 통계 업데이트
     */
    private Future<Void> updateReferrerStats(Long referrerId) {
        return Future.all(
            referralRepository.getDirectReferralCount(pool, referrerId),
            referralRepository.getActiveTeamCount(pool, referrerId)
        ).compose(result -> {
            Integer directCount = result.resultAt(0);
            Integer activeTeamCount = result.resultAt(1);
            
            return referralRepository.updateStats(pool, referrerId, directCount, activeTeamCount);
        }).mapEmpty();
    }
}

