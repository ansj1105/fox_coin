package com.foxya.coin.wallet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfflinePayNotifyCircuitBreakerTest {

    @Test
    void opensAfterThresholdAndRejectsDuringCooldown() {
        OfflinePayNotifyCircuitBreaker breaker = new OfflinePayNotifyCircuitBreaker(true, 2, 5_000L);

        assertThat(breaker.tryAcquire(1_000L)).isEqualTo(OfflinePayNotifyCircuitBreaker.PermitDecision.ALLOW);
        assertThat(breaker.recordFailure(1_000L)).isEqualTo(OfflinePayNotifyCircuitBreaker.Transition.NONE);
        assertThat(breaker.recordFailure(1_100L)).isEqualTo(OfflinePayNotifyCircuitBreaker.Transition.OPENED);

        assertThat(breaker.isOpen()).isTrue();
        assertThat(breaker.tryAcquire(2_000L)).isEqualTo(OfflinePayNotifyCircuitBreaker.PermitDecision.REJECT_OPEN);
        assertThat(breaker.openRemainingMs(2_000L)).isGreaterThan(0L);
    }

    @Test
    void closesAfterHalfOpenSuccess() {
        OfflinePayNotifyCircuitBreaker breaker = new OfflinePayNotifyCircuitBreaker(true, 1, 1_000L);

        assertThat(breaker.recordFailure(1_000L)).isEqualTo(OfflinePayNotifyCircuitBreaker.Transition.OPENED);
        assertThat(breaker.tryAcquire(1_500L)).isEqualTo(OfflinePayNotifyCircuitBreaker.PermitDecision.REJECT_OPEN);
        assertThat(breaker.tryAcquire(2_100L)).isEqualTo(OfflinePayNotifyCircuitBreaker.PermitDecision.ALLOW);
        assertThat(breaker.recordSuccess()).isEqualTo(OfflinePayNotifyCircuitBreaker.Transition.CLOSED);
        assertThat(breaker.isOpen()).isFalse();
    }
}
