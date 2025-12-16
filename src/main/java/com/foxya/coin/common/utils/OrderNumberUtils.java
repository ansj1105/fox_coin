package com.foxya.coin.common.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 주문번호 생성 유틸
 */
public class OrderNumberUtils {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("UTC"));
    
    /**
     * 주문번호 생성 (형식: ORD + 날짜시간(UTC) + 랜덤문자 8자)
     */
    public static String generateOrderNumber() {
        String timestamp = FORMATTER.format(Instant.now());
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase().replace("-", "");
        return "ORD" + timestamp + random;
    }
}

