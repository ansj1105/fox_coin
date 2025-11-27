package com.foxya.coin.common.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import com.foxya.coin.common.exceptions.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorHandler {
    
    public static void handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        int statusCode = ctx.statusCode();
        
        if (failure instanceof BadRequestException) {
            statusCode = 400;
        } else if (failure instanceof UnauthorizedException) {
            statusCode = 401;
        } else if (failure instanceof ForbiddenException) {
            statusCode = 403;
        } else if (failure instanceof NotFoundException) {
            statusCode = 404;
        } else if (statusCode == -1) {
            statusCode = 500;
        }
        
        String message = failure != null ? failure.getMessage() : "Internal Server Error";
        
        log.error("Error occurred: {}", message, failure);
        
        JsonObject error = new JsonObject()
            .put("status", "ERROR")
            .put("message", message)
            .put("code", statusCode);
        
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(error.encode());
    }
}

