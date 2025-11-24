package com.foxya.coin;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import com.foxya.coin.config.ConfigLoader;
import com.foxya.coin.verticle.ApiVerticle;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting MainVerticle...");
        
        ConfigLoader.load(vertx)
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

