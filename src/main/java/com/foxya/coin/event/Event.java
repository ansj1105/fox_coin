package com.foxya.coin.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 이벤트 데이터 모델
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {
    private String id;              // 이벤트 고유 ID
    private EventType type;         // 이벤트 타입
    private Map<String, Object> payload;  // 이벤트 데이터
    private LocalDateTime createdAt;      // 생성 시간
    private Integer retryCount;     // 재시도 횟수
    private String status;          // 상태 (PENDING, PROCESSING, COMPLETED, FAILED)
}

