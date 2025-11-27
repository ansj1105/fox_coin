package com.foxya.coin;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import com.foxya.coin.config.ConfigLoader;
import com.foxya.coin.verticle.ApiVerticle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainVerticle extends AbstractVerticle {
    
    private final String environment;
    
    public MainVerticle() {
        this.environment = null;
    }
    
    public MainVerticle(String environment) {
        this.environment = environment;
    }
    
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting MainVerticle...");
        
        // 환경이 지정되어 있으면 해당 환경 로드, 아니면 기본 로드
        Future<JsonObject> configFuture = (environment != null) 
            ? ConfigLoader.loadForEnv(vertx, environment)
            : ConfigLoader.load(vertx);
        
        configFuture
            .compose(config -> {
                // ApiVerticle 배포
                return vertx.deployVerticle(
                    new ApiVerticle(),
                    new io.vertx.core.DeploymentOptions().setConfig(config)
                );
            })
            .onSuccess(id -> {
                log.info("ApiVerticle deployed successfully with ID: {}", id);
                startPromise.complete();
            })
            .onFailure(throwable -> {
                log.error("Failed to start MainVerticle", throwable);
                startPromise.fail(throwable);
            });
    }
    
    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        log.info("Stopping MainVerticle...");
        stopPromise.complete();
    }
}

