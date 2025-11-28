package com.foxya.coin.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 현재 시간을 LocalDateTime으로 반환
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
    
    /**
     * 현재 시간을 문자열로 반환
     */
    public static String nowAsString() {
        return LocalDateTime.now().format(DEFAULT_FORMATTER);
    }
    
    /**
     * LocalDateTime을 문자열로 변환
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime.format(DEFAULT_FORMATTER);
    }
    
    /**
     * LocalDateTime을 지정된 포맷으로 변환
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }
    
    /**
     * 문자열을 LocalDateTime으로 변환
     */
    public static LocalDateTime parse(String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, DEFAULT_FORMATTER);
    }
    
    /**
     * 문자열을 지정된 포맷으로 LocalDateTime으로 변환
     */
    public static LocalDateTime parse(String dateTimeString, String pattern) {
        return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern(pattern));
    }
}

