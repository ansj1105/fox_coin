package com.foxya.coin.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * API 메트릭 수집기
 * HTTP 요청 수, 응답 시간 등을 수집합니다.
 */
@Slf4j
public class MetricsCollector {
    
    private final PrometheusMeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> responseTimers = new ConcurrentHashMap<>();
    private final Counter totalRequests;
    private final Counter totalErrors;
    
    public MetricsCollector() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        // 전체 요청 수 카운터
        this.totalRequests = Counter.builder("http_requests_total")
            .description("Total number of HTTP requests")
            .register(registry);
        
        // 전체 에러 수 카운터
        this.totalErrors = Counter.builder("http_errors_total")
            .description("Total number of HTTP errors")
            .register(registry);
    }
    
    /**
     * HTTP 요청 메트릭 기록
     */
    public void recordRequest(String method, String path, int statusCode, long durationMs) {
        try {
            // 전체 요청 수 증가
            totalRequests.increment();
            
            // 상태 코드별 카운터
            String statusKey = String.format("%s_%s_%d", method, normalizePath(path), statusCode);
            Counter counter = requestCounters.computeIfAbsent(statusKey, key -> {
                String[] parts = key.split("_", 3);
                return Counter.builder("http_requests_by_method_path_status")
                    .description("HTTP requests by method, path, and status code")
                    .tag("method", parts[0])
                    .tag("path", parts[1])
                    .tag("status", parts[2])
                    .register(registry);
            });
            counter.increment();
            
            // 응답 시간 기록
            String timerKey = String.format("%s_%s", method, normalizePath(path));
            Timer timer = responseTimers.computeIfAbsent(timerKey, key -> {
                String[] parts = key.split("_", 2);
                return Timer.builder("http_request_duration_seconds")
                    .description("HTTP request duration in seconds")
                    .tag("method", parts[0])
                    .tag("path", parts[1])
                    .register(registry);
            });
            timer.record(durationMs, TimeUnit.MILLISECONDS);
            
            // 에러 카운트 (4xx, 5xx)
            if (statusCode >= 400) {
                totalErrors.increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record metrics", e);
        }
    }
    
    /**
     * 경로 정규화 (동적 파라미터를 일반화)
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        
        // 숫자 ID를 :id로 변환
        path = path.replaceAll("/\\d+", "/:id");
        
        // UUID를 :id로 변환
        path = path.replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/:id");
        
        // 경로 정리
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path.isEmpty() ? "root" : path;
    }
    
    /**
     * Prometheus 메트릭 출력
     */
    public String scrape() {
        return registry.scrape();
    }
    
    /**
     * MeterRegistry 반환 (커스텀 메트릭 추가용)
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}

