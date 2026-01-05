package com.foxya.coin.monitoring;

import com.foxya.coin.common.BaseHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.extern.slf4j.Slf4j;

/**
 * 모니터링 페이지 접근 핸들러
 * API 키 인증 없이 접근 가능
 */
@Slf4j
public class MonitoringHandler extends BaseHandler {
    
    private final WebClient webClient;
    
    public MonitoringHandler(Vertx vertx, String apiKey) {
        super(vertx);
        // WebClient 옵션 설정 (타임아웃 증가, 리다이렉트는 수동 처리)
        WebClientOptions options = new WebClientOptions()
            .setConnectTimeout(10000)  // 10초 (연결 타임아웃)
            .setIdleTimeout(120)      // 2분 (유휴 타임아웃 - Grafana 응답 대기)
            .setKeepAlive(true)
            .setKeepAliveTimeout(30)   // Keep-Alive 타임아웃 30초
            .setMaxPoolSize(10)
            .setMaxWaitQueueSize(20)   // 대기 큐 크기
            .setReuseAddress(true)     // 주소 재사용
            .setReusePort(true)        // 포트 재사용
            .setTcpKeepAlive(true)     // TCP Keep-Alive
            .setFollowRedirects(false); // 리다이렉트는 수동 처리 (무한 루프 방지)
        this.webClient = WebClient.create(vertx, options);
        // API 키는 더 이상 사용하지 않지만 호환성을 위해 파라미터 유지
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // API 키 확인 제거 - 모든 요청 허용
        
        // Grafana 프록시
        router.get("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        router.post("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        router.put("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        router.delete("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        
        // Grafana 정적 파일 프록시 (Grafana가 /public/... 경로로 요청하는 경우)
        // Referer 헤더를 확인하여 Grafana에서 온 요청만 처리 (프론트엔드 보호)
        router.get("/public/*").handler(ctx -> {
            String referer = ctx.request().getHeader("Referer");
            // Grafana에서 온 요청인지 확인 (referer에 /6s9ex74204/grafana 포함)
            if (referer != null && referer.contains("/6s9ex74204/grafana")) {
                proxyToGrafana(ctx);
            } else {
                // 프론트엔드 요청이면 다음 핸들러로 전달 (404 반환)
                ctx.response().setStatusCode(404).end();
            }
        });
        
        // Prometheus 프록시
        router.get("/6s9ex74204/prometheus/*").handler(this::proxyToPrometheus);
        router.post("/6s9ex74204/prometheus/*").handler(this::proxyToPrometheus);
        
        // 루트 경로는 Grafana로 리다이렉트
        router.get("/6s9ex74204").handler(ctx -> {
            ctx.response()
                .setStatusCode(302)
                .putHeader("Location", "/6s9ex74204/grafana/")
                .end();
        });
        
        return router;
    }
    
    /**
     * Grafana 리다이렉트 Location 헤더를 내부 경로로 변환
     */
    private String convertGrafanaRedirectLocation(String location) {
        if (location == null || location.isEmpty()) {
            return location;
        }
        
        // 외부 URL 또는 내부 URL을 내부 경로로 변환
        if (location.contains("https://korion.io.kr/6s9ex74204/grafana")) {
            return location.replace("https://korion.io.kr", "");
        } else if (location.contains("http://korion.io.kr/6s9ex74204/grafana")) {
            return location.replace("http://korion.io.kr", "");
        } else if (location.contains("http://localhost:8080/6s9ex74204/grafana")) {
            // 내부 경로로 설정된 경우도 변환
            return location.replace("http://localhost:8080", "");
        } else if (location.contains("/6s9ex74204/grafana")) {
            // 이미 subpath가 포함되어 있으면 그대로 사용
            return location;
        } else if (location.startsWith("/")) {
            // 상대 경로인 경우 subpath 추가
            return "/6s9ex74204/grafana" + location;
        }
        
        // 절대 URL이지만 위 패턴에 맞지 않는 경우 그대로 반환
        return location;
    }
    
    /**
     * Grafana로 프록시
     */
    private void proxyToGrafana(RoutingContext ctx) {
        String requestPath = ctx.request().path();
        String path;
        
        // /public/... 또는 /api/... 경로인 경우 (Grafana 정적 파일/API 요청)
        if (requestPath.startsWith("/public/") || requestPath.startsWith("/api/")) {
            path = requestPath;  // 경로 그대로 사용
        } else {
            // /6s9ex74204/grafana/... 경로인 경우
            path = requestPath.replace("/6s9ex74204/grafana", "");
            if (path.isEmpty() || path.equals("/")) {
                path = "/";
            }
        }
        
        final String queryString = ctx.request().query() != null && !ctx.request().query().isEmpty() 
            ? "?" + ctx.request().query() : "";
        final String targetUrl = "http://grafana:3000" + path + queryString;
        
        log.info("Proxying to Grafana: {} {} (path: {})", ctx.request().method(), targetUrl, path);
        
        long startTime = System.currentTimeMillis();
        
        var request = webClient.request(ctx.request().method(), 3000, "grafana", path);
        
        // 타임아웃 설정 (15초로 설정하여 빠른 실패)
        request.timeout(15000);
        
        // 헤더 복사 (Host 제외)
        ctx.request().headers().forEach(entry -> {
            if (!entry.getKey().equalsIgnoreCase("Host") && 
                !entry.getKey().equalsIgnoreCase("Content-Length")) {
                request.putHeader(entry.getKey(), entry.getValue());
            }
        });
        
        // Body 전송
        if (ctx.body() != null && ctx.body().length() > 0) {
            request.sendBuffer(ctx.body().buffer())
                .onSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Grafana response received: status={}, duration={}ms", response.statusCode(), duration);
                    
                    // 301/302 리다이렉트 처리
                    if (response.statusCode() == 301 || response.statusCode() == 302) {
                        String location = response.getHeader("Location");
                        if (location != null && !location.isEmpty()) {
                            String newLocation = convertGrafanaRedirectLocation(location);
                            log.info("Grafana redirect detected: {} -> {}", location, newLocation);
                            ctx.response()
                                .setStatusCode(response.statusCode())
                                .putHeader("Location", newLocation)
                                .end();
                            return;
                        }
                    }
                    
                    // 응답 헤더 복사
                    response.headers().forEach(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        
                        // Location 헤더 수정
                        if (key.equalsIgnoreCase("Location") && value.startsWith("/")) {
                            ctx.response().putHeader("Location", "/6s9ex74204/grafana" + value);
                        } else {
                            ctx.response().putHeader(key, value);
                        }
                    });
                    
                    ctx.response()
                        .setStatusCode(response.statusCode())
                        .end(response.body());
                })
                .onFailure(err -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Failed to proxy to Grafana: {} - Error: {}, Class: {}, Duration: {}ms", 
                        targetUrl, err.getMessage(), err.getClass().getSimpleName(), duration, err);
                    if (!ctx.response().ended()) {
                        ctx.response()
                            .setStatusCode(502)
                            .putHeader("Content-Type", "text/plain")
                            .end("Grafana 서버에 연결할 수 없습니다: " + err.getMessage());
                    }
                });
        } else {
            request.send()
                .onSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Grafana response received: status={}, duration={}ms", response.statusCode(), duration);
                    
                    // 301/302 리다이렉트 처리
                    if (response.statusCode() == 301 || response.statusCode() == 302) {
                        String location = response.getHeader("Location");
                        if (location != null && !location.isEmpty()) {
                            String newLocation = convertGrafanaRedirectLocation(location);
                            log.info("Grafana redirect detected: {} -> {}", location, newLocation);
                            ctx.response()
                                .setStatusCode(response.statusCode())
                                .putHeader("Location", newLocation)
                                .end();
                            return;
                        }
                    }
                    
                    // 응답 헤더 복사
                    response.headers().forEach(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        
                        // Location 헤더 수정
                        if (key.equalsIgnoreCase("Location")) {
                            String newLocation = convertGrafanaRedirectLocation(value);
                            ctx.response().putHeader("Location", newLocation);
                        } else {
                            ctx.response().putHeader(key, value);
                        }
                    });
                    
                    ctx.response()
                        .setStatusCode(response.statusCode())
                        .end(response.body());
                })
                .onFailure(err -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Failed to proxy to Grafana: {} - Error: {}, Class: {}, Duration: {}ms", 
                        targetUrl, err.getMessage(), err.getClass().getSimpleName(), duration, err);
                    if (!ctx.response().ended()) {
                        ctx.response()
                            .setStatusCode(502)
                            .putHeader("Content-Type", "text/plain")
                            .end("Grafana 서버에 연결할 수 없습니다: " + err.getMessage());
                    }
                });
        }
    }
    
    /**
     * Prometheus로 프록시
     */
    private void proxyToPrometheus(RoutingContext ctx) {
        String path = ctx.request().path().replace("/6s9ex74204/prometheus", "");
        if (path.isEmpty() || path.equals("/")) {
            path = "/";
        }
        
        String targetUrl = "http://prometheus:9090" + path;
        if (ctx.request().query() != null && !ctx.request().query().isEmpty()) {
            targetUrl += "?" + ctx.request().query();
        }
        
        log.debug("Proxying to Prometheus: {} {}", ctx.request().method(), targetUrl);
        
        var request = webClient.request(ctx.request().method(), 9090, "prometheus", path);
        
        // 헤더 복사 (Host 제외)
        ctx.request().headers().forEach(entry -> {
            if (!entry.getKey().equalsIgnoreCase("Host") && 
                !entry.getKey().equalsIgnoreCase("Content-Length")) {
                request.putHeader(entry.getKey(), entry.getValue());
            }
        });
        
        // Body 전송
        if (ctx.body() != null && ctx.body().length() > 0) {
            request.sendBuffer(ctx.body().buffer())
                .onSuccess(response -> {
                    // 응답 헤더 복사
                    response.headers().forEach(entry -> {
                        ctx.response().putHeader(entry.getKey(), entry.getValue());
                    });
                    
                    ctx.response()
                        .setStatusCode(response.statusCode())
                        .end(response.body());
                })
                .onFailure(err -> {
                    log.error("Failed to proxy to Prometheus", err);
                    ctx.response()
                        .setStatusCode(502)
                        .end("Prometheus 서버에 연결할 수 없습니다.");
                });
        } else {
            request.send()
                .onSuccess(response -> {
                    // 응답 헤더 복사
                    response.headers().forEach(entry -> {
                        ctx.response().putHeader(entry.getKey(), entry.getValue());
                    });
                    
                    ctx.response()
                        .setStatusCode(response.statusCode())
                        .end(response.body());
                })
                .onFailure(err -> {
                    log.error("Failed to proxy to Prometheus", err);
                    ctx.response()
                        .setStatusCode(502)
                        .end("Prometheus 서버에 연결할 수 없습니다.");
                });
        }
    }
}
