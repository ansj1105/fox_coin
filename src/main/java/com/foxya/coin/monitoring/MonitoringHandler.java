package com.foxya.coin.monitoring;

import com.foxya.coin.common.BaseHandler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

/**
 * 모니터링 페이지 접근 핸들러
 * API 키로 인증
 */
@Slf4j
public class MonitoringHandler extends BaseHandler {
    
    private final WebClient webClient;
    private final String apiKey;
    
    public MonitoringHandler(Vertx vertx, String apiKey) {
        super(vertx);
        this.webClient = WebClient.create(vertx);
        this.apiKey = apiKey;
    }
    
    @Override
    public Router getRouter() {
        Router router = Router.router(getVertx());
        
        // 모든 /6s9ex74204 경로에 대해 API 키 확인
        router.route("/6s9ex74204/*").handler(this::checkApiKey);
        
        // Grafana 프록시
        router.get("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        router.post("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        router.put("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        router.delete("/6s9ex74204/grafana/*").handler(this::proxyToGrafana);
        
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
     * API 키 확인
     */
    private void checkApiKey(RoutingContext ctx) {
        // 헤더에서 API 키 확인 (X-API-Key 또는 Authorization)
        String apiKeyHeader = ctx.request().getHeader("X-API-Key");
        if (apiKeyHeader == null || apiKeyHeader.isEmpty()) {
            apiKeyHeader = ctx.request().getHeader("Authorization");
            if (apiKeyHeader != null && apiKeyHeader.startsWith("Bearer ")) {
                apiKeyHeader = apiKeyHeader.substring(7);
            }
        }
        
        // 쿼리 파라미터에서도 확인
        if (apiKeyHeader == null || apiKeyHeader.isEmpty()) {
            apiKeyHeader = ctx.request().getParam("apiKey");
        }
        
        // API 키 확인
        if (apiKeyHeader == null || !apiKeyHeader.equals(apiKey)) {
            log.warn("Invalid API key attempt from {}", ctx.request().remoteAddress());
            ctx.response()
                .setStatusCode(401)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Unauthorized")
                    .put("message", "유효한 API 키가 필요합니다.")
                    .encode());
            return;
        }
        
        ctx.next();
    }
    
    /**
     * Grafana로 프록시
     */
    private void proxyToGrafana(RoutingContext ctx) {
        String path = ctx.request().path().replace("/6s9ex74204/grafana", "");
        if (path.isEmpty() || path.equals("/")) {
            path = "/";
        }
        
        String targetUrl = "http://grafana:3000" + path;
        if (ctx.request().query() != null && !ctx.request().query().isEmpty()) {
            targetUrl += "?" + ctx.request().query();
        }
        
        log.debug("Proxying to Grafana: {} {}", ctx.request().method(), targetUrl);
        
        var request = webClient.request(ctx.request().method(), 3000, "grafana", path);
        
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
                    log.error("Failed to proxy to Grafana", err);
                    ctx.response()
                        .setStatusCode(502)
                        .end("Grafana 서버에 연결할 수 없습니다.");
                });
        } else {
            request.send()
                .onSuccess(response -> {
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
                    log.error("Failed to proxy to Grafana", err);
                    ctx.response()
                        .setStatusCode(502)
                        .end("Grafana 서버에 연결할 수 없습니다.");
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
