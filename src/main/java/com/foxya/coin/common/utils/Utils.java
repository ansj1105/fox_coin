package com.foxya.coin.common.utils;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class Utils {
    
    public static Map<String, Object> getMapFromJsonObject(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        if (json != null) {
            json.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        }
        return map;
    }
}

