package com.foxya.coin.event;

/**
 * 이벤트 타입 정의
 */
public enum EventType {
    // 트랜잭션 이벤트
    TRANSACTION_PENDING("transaction:pending"),
    TRANSACTION_CONFIRMED("transaction:confirmed"),
    TRANSACTION_FAILED("transaction:failed"),
    
    // 출금 이벤트
    WITHDRAWAL_REQUESTED("withdrawal:requested"),
    WITHDRAWAL_PROCESSING("withdrawal:processing"),
    WITHDRAWAL_COMPLETED("withdrawal:completed"),
    WITHDRAWAL_FAILED("withdrawal:failed"),
    
    // 입금 이벤트
    DEPOSIT_DETECTED("deposit:detected"),
    DEPOSIT_CONFIRMED("deposit:confirmed"),
    
    // 레퍼럴 이벤트
    REFERRAL_REGISTERED("referral:registered"),
    REFERRAL_REWARD("referral:reward");
    
    private final String channel;
    
    EventType(String channel) {
        this.channel = channel;
    }
    
    public String getChannel() {
        return channel;
    }
}

