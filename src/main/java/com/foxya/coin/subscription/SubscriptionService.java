package com.foxya.coin.subscription;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.subscription.dto.SubscriptionStatusResponseDto;
import com.foxya.coin.subscription.entities.Subscription;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
public class SubscriptionService extends BaseService {
    
    private final SubscriptionRepository subscriptionRepository;
    
    public SubscriptionService(PgPool pool, SubscriptionRepository subscriptionRepository) {
        super(pool);
        this.subscriptionRepository = subscriptionRepository;
    }
    
    public Future<SubscriptionStatusResponseDto> getSubscriptionStatus(Long userId) {
        return subscriptionRepository.getActiveSubscription(pool, userId)
            .map(subscription -> {
                if (subscription == null) {
                    return SubscriptionStatusResponseDto.builder()
                        .isSubscribed(false)
                        .expiresAt(null)
                        .packageType(null)
                        .build();
                }
                
                boolean isExpired = subscription.getExpiresAt() != null && 
                                  subscription.getExpiresAt().isBefore(LocalDateTime.now());
                boolean isSubscribed = subscription.getIsActive() && !isExpired;
                
                return SubscriptionStatusResponseDto.builder()
                    .isSubscribed(isSubscribed)
                    .expiresAt(subscription.getExpiresAt())
                    .packageType(subscription.getPackageType())
                    .build();
            });
    }
    
    public Future<Subscription> subscribe(Long userId, String packageType, Integer months) {
        LocalDateTime expiresAt = months != null && months > 0 
            ? LocalDateTime.now().plusMonths(months)
            : null; // 무제한 구독
        
        return subscriptionRepository.createSubscription(pool, userId, packageType, expiresAt);
    }
}

