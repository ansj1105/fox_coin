package com.foxya.coin.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
public class RedisJsonCache {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RedisAPI redis;
    private final String namespace;

    public RedisJsonCache(RedisAPI redis, String namespace) {
        this.redis = redis;
        this.namespace = (namespace == null || namespace.isBlank()) ? "foxya:cache" : namespace.trim();
    }

    public <T> Future<T> getOrLoad(String key, int ttlSeconds, Class<T> type, Supplier<Future<T>> loader) {
        return getOrLoad(key, ttlSeconds, payload -> {
            if (type == JsonObject.class) {
                return type.cast(new JsonObject(payload));
            }
            return MAPPER.readValue(payload, type);
        }, loader);
    }

    public <T> Future<T> getOrLoad(String key, int ttlSeconds, TypeReference<T> type, Supplier<Future<T>> loader) {
        return getOrLoad(key, ttlSeconds, payload -> MAPPER.readValue(payload, type), loader);
    }

    public Future<Void> delete(String key) {
        if (redis == null) {
            return Future.succeededFuture();
        }
        return redis.del(List.of(buildKey(key)))
            .<Void>mapEmpty()
            .recover(err -> {
                log.warn("Redis cache delete failed. key={}, cause={}", buildKey(key), err.getMessage());
                return Future.succeededFuture();
            });
    }

    private <T> Future<T> getOrLoad(String key, int ttlSeconds, CacheDecoder<T> decoder, Supplier<Future<T>> loader) {
        if (redis == null) {
            return loader.get();
        }

        String cacheKey = buildKey(key);
        return redis.get(cacheKey)
            .compose(cached -> {
                if (cached != null && cached.toString() != null && !cached.toString().isBlank()) {
                    try {
                        return Future.succeededFuture(decoder.decode(cached.toString()));
                    } catch (Exception e) {
                        log.warn("Redis cache decode failed. key={}, cause={}", cacheKey, e.getMessage());
                    }
                }
                return loadAndStore(cacheKey, ttlSeconds, loader);
            })
            .recover(err -> {
                log.warn("Redis cache read failed. key={}, cause={}", cacheKey, err.getMessage());
                return loader.get();
            });
    }

    private <T> Future<T> loadAndStore(String cacheKey, int ttlSeconds, Supplier<Future<T>> loader) {
        return loader.get()
            .compose(value -> {
                if (value == null || ttlSeconds <= 0 || redis == null) {
                    return Future.succeededFuture(value);
                }
                final String payload;
                try {
                    payload = value instanceof JsonObject jsonObject
                        ? jsonObject.encode()
                        : MAPPER.writeValueAsString(value);
                } catch (Exception e) {
                    log.warn("Redis cache encode failed. key={}, cause={}", cacheKey, e.getMessage());
                    return Future.succeededFuture(value);
                }
                return redis.setex(cacheKey, String.valueOf(ttlSeconds), payload)
                    .map(value)
                    .recover(err -> {
                        log.warn("Redis cache write failed. key={}, cause={}", cacheKey, err.getMessage());
                        return Future.succeededFuture(value);
                    });
            });
    }

    private String buildKey(String key) {
        String suffix = key == null ? "" : key.trim();
        return namespace + ":" + suffix;
    }

    @FunctionalInterface
    private interface CacheDecoder<T> {
        T decode(String payload) throws Exception;
    }
}
