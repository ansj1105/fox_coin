package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.QueryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UserExternalIdRepository extends BaseRepository {

    /**
     * provider + externalId로 사용자 ID 조회
     */
    public Future<Long> getUserIdByProviderAndExternalId(SqlClient client, String provider, String externalId) {
        String sql = QueryBuilder
            .select("user_external_ids", "user_id")
            .where("provider", Op.Equal, "provider")
            .andWhere("external_id", Op.Equal, "external_id")
            .build();
        
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

    /**
     * user_id + provider로 external_id 조회
     */
    public Future<String> getExternalIdByUserIdAndProvider(SqlClient client, Long userId, String provider) {
        String sql = QueryBuilder
            .select("user_external_ids", "external_id")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("provider", Op.Equal, "provider")
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("provider", provider);

        return query(client, sql, params)
            .map(rows -> {
                if (!rows.iterator().hasNext()) {
                    return null;
                }
                return getStringColumnValue(rows.iterator().next(), "external_id");
            })
            .onFailure(throwable -> log.error("외부 사용자 매핑 조회 실패 - userId: {}, provider: {}", userId, provider));
    }

    /**
     * user_id + provider 기준으로 external_id upsert
     */
    public Future<Void> upsertUserExternalId(SqlClient client, Long userId, String provider, String externalId) {
        String sql = QueryBuilder
            .insert("user_external_ids", "user_id", "provider", "external_id")
            .onConflict("user_id, provider")
            .doUpdateExcludedWithCurrentTimestamp("external_id")
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("provider", provider);
        params.put("external_id", externalId);

        return query(client, sql, params)
            .map(rows -> (Void) null)
            .onFailure(throwable -> log.error("외부 사용자 매핑 upsert 실패 - userId: {}, provider: {}, externalId: {}", userId, provider, externalId));
    }
}
