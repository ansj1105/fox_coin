package com.foxya.coin.client;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.client.entities.ApiKey;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ClientRepository extends BaseRepository {
    
    private final RowMapper<ApiKey> apiKeyMapper = row -> ApiKey.builder()
        .id(getLongColumnValue(row, "id"))
        .apiKey(getStringColumnValue(row, "api_key"))
        .apiSecret(getStringColumnValue(row, "api_secret"))
        .clientName(getStringColumnValue(row, "client_name"))
        .description(getStringColumnValue(row, "description"))
        .isActive(getBooleanColumnValue(row, "is_active"))
        .expiresAt(getLocalDateTimeColumnValue(row, "expires_at"))
        .lastUsedAt(getLocalDateTimeColumnValue(row, "last_used_at"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    /**
     * API Key로 조회
     */
    public Future<ApiKey> getApiKeyByKey(SqlClient client, String apiKey) {
        String sql = "SELECT * FROM api_keys WHERE api_key = #{api_key} AND is_active = true";
        
        return query(client, sql, Collections.singletonMap("api_key", apiKey))
            .map(rows -> fetchOne(apiKeyMapper, rows))
            .onFailure(throwable -> log.error("API Key 조회 실패 - apiKey: {}", apiKey));
    }
    
    /**
     * API Key와 Secret으로 조회 및 검증
     */
    public Future<ApiKey> getApiKeyByKeyAndSecret(SqlClient client, String apiKey, String apiSecret) {
        String sql = "SELECT * FROM api_keys WHERE api_key = #{api_key} AND api_secret = #{api_secret} AND is_active = true";
        
        Map<String, Object> params = new HashMap<>();
        params.put("api_key", apiKey);
        params.put("api_secret", apiSecret);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(apiKeyMapper, rows))
            .onFailure(throwable -> log.error("API Key 검증 실패 - apiKey: {}", apiKey));
    }
    
    /**
     * 마지막 사용 시간 업데이트
     */
    public Future<Void> updateLastUsedAt(SqlClient client, Long id) {
        String sql = "UPDATE api_keys SET last_used_at = #{last_used_at}, updated_at = #{updated_at} WHERE id = #{id}";
        
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("last_used_at", now);
        params.put("updated_at", now);
        
        return query(client, sql, params)
            .map(rows -> (Void) null)
            .onFailure(throwable -> log.error("API Key 마지막 사용 시간 업데이트 실패 - id: {}", id));
    }
}

