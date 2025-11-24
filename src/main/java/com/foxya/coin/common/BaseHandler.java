package com.foxya.coin.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.json.schema.SchemaParser;
import io.vertx.json.schema.SchemaRouter;
import io.vertx.json.schema.SchemaRouterOptions;
import com.foxya.coin.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseHandler {
    
    private static final String DEFAULT_SUCCESS_MESSAGE = "요청이 완료되었습니다.";
    private static final String DEFAULT_FAIL_MESSAGE = "요청이 실패했습니다.";
    private static final String JSON = "application/json";
    
    private final Vertx vertx;
    
    public Vertx getVertx() {
        return vertx;
    }
    
    protected SchemaParser createSchemaParser() {
        return SchemaParser.createDraft7SchemaParser(
            SchemaRouter.create(getVertx(), new SchemaRouterOptions())
        );
    }
    
    public abstract Router getRouter();
    
    public static JsonObject getQueryAsJson(RoutingContext ctx) {
        MultiMap multiMap = ctx.queryParams();
        JsonObject json = new JsonObject();
        multiMap.forEach(entry -> json.put(entry.getKey(), entry.getValue()));
        return json;
    }
    
    public static ObjectMapper getObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    protected <T> void response(RoutingContext ctx, Future<T> future) {
        response(ctx, future, result -> result);
    }
    
    protected <T, R> void response(RoutingContext ctx, Future<T> future, Function<T, R> mapper) {
        future
            .onSuccess(result -> {
                try {
                    Object obj = mapper.apply(result);
                    success(ctx, obj);
                } catch (Exception e) {
                    ctx.fail(e);
                }
            })
            .onFailure(ctx::fail);
    }
    
    protected void success(RoutingContext ctx, Object dto) {
        ApiResponse<?> resData = new ApiResponse<>("OK", DEFAULT_SUCCESS_MESSAGE, dto);
        
        ctx.response()
            .setChunked(true)
            .setStatusCode(HttpResponseStatus.OK.code())
            .putHeader(HttpHeaders.CONTENT_TYPE, JSON)
            .end(Json.encode(resData));
    }
    
    protected void fail(RoutingContext ctx, Object dto) {
        ApiResponse<?> resData = new ApiResponse<>("FAIL", DEFAULT_FAIL_MESSAGE, dto);
        
        ctx.response()
            .setChunked(true)
            .setStatusCode(HttpResponseStatus.OK.code())
            .putHeader(HttpHeaders.CONTENT_TYPE, JSON)
            .end(Json.encode(resData));
    }
}

