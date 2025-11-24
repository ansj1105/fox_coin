package com.foxya.coin;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MainVerticle 테스트
 */
@ExtendWith(VertxExtension.class)
public class MainVerticleTest {
    
    @BeforeEach
    void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
    }
    
    @Test
    void verticle_deployed(Vertx vertx, VertxTestContext testContext) {
        testContext.completeNow();
    }
}

