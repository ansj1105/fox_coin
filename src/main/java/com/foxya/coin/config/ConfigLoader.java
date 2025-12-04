package com.foxya.coin.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigLoader {
    
    // 설정 파일 경로 (환경변수로 오버라이드 가능)
    private static final String DEFAULT_CONFIG_PATH = "config.json";
    private static final String DEV_CONFIG_PATH = "src/main/resources/config.json";
    
    /**
     * 환경별 설정 로드
     * 환경 변수 ENV 또는 config.json의 env 필드로 환경 결정
     * 기본값: local
     */
    public static Future<JsonObject> load(Vertx vertx) {
        // 설정 파일 경로 결정: 환경변수 > 기본 경로 > 개발 경로
        String configPath = System.getenv("CONFIG_PATH");
        if (configPath == null || configPath.isEmpty()) {
            configPath = DEFAULT_CONFIG_PATH;
        }
        
        final String finalConfigPath = configPath;
        log.info("Loading config from: {}", finalConfigPath);
        
        return vertx.fileSystem()
            .readFile(finalConfigPath)
            .recover(err -> {
                // 기본 경로에서 실패하면 개발 경로 시도
                log.warn("Config not found at {}, trying dev path: {}", finalConfigPath, DEV_CONFIG_PATH);
                return vertx.fileSystem().readFile(DEV_CONFIG_PATH);
            })
            .map(buffer -> {
                JsonObject fullConfig = new JsonObject(buffer.toString());
                
                // 환경 결정: 환경변수 > config.env > 기본값(local)
                String env = System.getenv("APP_ENV");
                if (env == null || env.isEmpty()) {
                    env = System.getenv("ENV");
                }
                if (env == null || env.isEmpty()) {
                    env = fullConfig.getString("env", "local");
                }
                
                log.info("Loading config for environment: {}", env);
                
                // 해당 환경의 설정 추출
                JsonObject envConfig = fullConfig.getJsonObject(env);
                if (envConfig == null) {
                    log.warn("Environment '{}' not found in config, using 'local'", env);
                    envConfig = fullConfig.getJsonObject("local");
                }
                
                // env 필드 추가
                envConfig.put("env", env);
                
                return envConfig;
            })
            .onSuccess(config -> log.info("Config loaded successfully"))
            .onFailure(throwable -> log.error("Failed to load config", throwable));
    }
    
    /**
     * 테스트용: 특정 파일과 환경의 설정 로드
     */
    public static Future<JsonObject> loadForEnv(Vertx vertx, String configPath, String env) {
        return vertx.fileSystem()
            .readFile(configPath)
            .map(buffer -> {
                JsonObject fullConfig = new JsonObject(buffer.toString());
                
                log.info("Loading config for environment: {} from {}", env, configPath);
                
                JsonObject envConfig = fullConfig.getJsonObject(env);
                if (envConfig == null) {
                    throw new IllegalArgumentException("Environment '" + env + "' not found in config at " + configPath);
                }
                
                envConfig.put("env", env);
                
                return envConfig;
            })
            .onSuccess(config -> log.info("Config loaded successfully for env: {}", env))
            .onFailure(throwable -> log.error("Failed to load config for env: {} from {}", env, configPath, throwable));
    }
    
    /**
     * 테스트용: 특정 환경의 설정 로드 (기본 경로)
     */
    public static Future<JsonObject> loadForEnv(Vertx vertx, String env) {
        return loadForEnv(vertx, "src/main/resources/config.json", env);
    }
}

