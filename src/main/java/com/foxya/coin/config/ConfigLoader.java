package com.foxya.coin.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigLoader {
    
    public static Future<JsonObject> load(Vertx vertx) {
        return vertx.fileSystem()
            .readFile("src/main/resources/config.json")
            .map(buffer -> new JsonObject(buffer.toString()))
            .onSuccess(config -> log.info("Config loaded successfully"))
            .onFailure(throwable -> log.error("Failed to load config", throwable));
    }
}

