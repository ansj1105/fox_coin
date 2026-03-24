package com.foxya.coin.wallet;

final class OfflinePayNotifyCircuitBreaker {

    enum PermitDecision {
        ALLOW,
        REJECT_OPEN
    }

    enum Transition {
        NONE,
        OPENED,
        CLOSED
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final boolean enabled;
    private final int failureThreshold;
    private final long openDurationMs;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAtMs = 0L;

    OfflinePayNotifyCircuitBreaker(boolean enabled, int failureThreshold, long openDurationMs) {
        this.enabled = enabled;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1000L, openDurationMs);
    }

    synchronized PermitDecision tryAcquire(long nowMs) {
        if (!enabled) {
            return PermitDecision.ALLOW;
        }

        if (state == State.OPEN) {
            if (nowMs - openedAtMs >= openDurationMs) {
                state = State.HALF_OPEN;
                return PermitDecision.ALLOW;
            }
            return PermitDecision.REJECT_OPEN;
        }

        return PermitDecision.ALLOW;
    }

    synchronized Transition recordSuccess() {
        if (!enabled) {
            return Transition.NONE;
        }

        Transition transition = state == State.HALF_OPEN || state == State.OPEN
            ? Transition.CLOSED
            : Transition.NONE;
        state = State.CLOSED;
        consecutiveFailures = 0;
        openedAtMs = 0L;
        return transition;
    }

    synchronized Transition recordFailure(long nowMs) {
        if (!enabled) {
            return Transition.NONE;
        }

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            openedAtMs = nowMs;
            consecutiveFailures = failureThreshold;
            return Transition.OPENED;
        }

        consecutiveFailures += 1;
        if (consecutiveFailures >= failureThreshold) {
            boolean wasOpen = state == State.OPEN;
            state = State.OPEN;
            openedAtMs = nowMs;
            return wasOpen ? Transition.NONE : Transition.OPENED;
        }

        return Transition.NONE;
    }

    synchronized boolean isOpen() {
        return enabled && state == State.OPEN;
    }

    synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    synchronized long openRemainingMs(long nowMs) {
        if (!enabled || state != State.OPEN) {
            return 0L;
        }
        return Math.max(0L, openDurationMs - (nowMs - openedAtMs));
    }
}
