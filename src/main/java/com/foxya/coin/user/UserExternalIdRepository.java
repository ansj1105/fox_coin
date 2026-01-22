package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UserExternalIdRepository extends BaseRepository {
    
    /**
     * provider + externalId로 사용자 ID 조회
     */
    public Future<Long> getUserIdByProviderAndExternalId(SqlClient client, String provider, String externalId) {
        String sql = "SELECT user_id FROM user_external_ids WHERE provider = #{provider} AND external_id = #{external_id}";
        
        Map<String, Object> params = new HashMap<>();
        params.put("provider", provider);
        params.put("external_id", externalId);
        
        return query(client, sql, params)
            .map(rows -> {
                if (!rows.iterator().hasNext()) {
                    return null;
                }
                return getLongColumnValue(rows.iterator().next(), "user_id");
            })
            .onFailure(throwable -> log.error("외부 사용자 매핑 조회 실패 - provider: {}, externalId: {}", provider, externalId));
    }
}
