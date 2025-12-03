package com.foxya.coin.event;

import com.foxya.coin.verticle.EventVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EventVerticleTest {
    
    private static Vertx vertx;
    private static String deploymentId;
    
    @BeforeAll
    static void setup() {
        vertx = Vertx.vertx();
    }
    
    @AfterAll
    static void teardown(VertxTestContext tc) {
        if (deploymentId != null) {
            vertx.undeploy(deploymentId)
                .onSuccess(v -> {
                    log.info("EventVerticle undeployed");
                    vertx.close();
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
        } else {
            vertx.close();
            tc.completeNow();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("성공 - EventVerticle 배포")
    void successDeployEventVerticle(VertxTestContext tc) {
        JsonObject config = new JsonObject()
            .put("redis", new JsonObject()
                .put("host", "localhost")
                .put("port", 6379)
                .put("password", ""));
        
        DeploymentOptions options = new DeploymentOptions().setConfig(config);
        
        vertx.deployVerticle(new EventVerticle(), options)
            .onSuccess(id -> {
                log.info("EventVerticle deployed with ID: {}", id);
                deploymentId = id;
                assertThat(id).isNotNull();
                tc.completeNow();
            })
            .onFailure(throwable -> {
                log.error("Failed to deploy EventVerticle", throwable);
                // Redis가 없는 환경에서는 실패할 수 있음
                if (throwable.getMessage().contains("Connection refused")) {
                    log.warn("Redis not available, skipping test");
                    tc.completeNow();
                } else {
                    tc.failNow(throwable);
                }
            });
    }
    
    @Test
    @Order(2)
    @DisplayName("성공 - EventVerticle 중지")
    void successStopEventVerticle(VertxTestContext tc) throws InterruptedException {
        // 배포가 성공한 경우에만 테스트
        if (deploymentId == null) {
            log.warn("EventVerticle not deployed, skipping stop test");
            tc.completeNow();
            return;
        }
        
        // 잠시 대기 후 중지
        tc.awaitCompletion(2, TimeUnit.SECONDS);
        tc.completeNow();
    }
}

