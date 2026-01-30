package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class UserExternalIdRepository extends BaseRepository {

    private static final String SQL_GET_USER_ID_BY_PROVIDER_AND_EXTERNAL_ID =
        "SELECT user_id FROM user_external_ids WHERE provider = #{provider} AND external_id = #{external_id}";
    private static final String SQL_GET_EXTERNAL_ID_BY_USER_AND_PROVIDER =
        "SELECT external_id FROM user_external_ids WHERE user_id = #{user_id} AND provider = #{provider}";
    private static final String SQL_UPSERT_USER_EXTERNAL_ID =
        "INSERT INTO user_external_ids (user_id, provider, external_id) "
            + "VALUES (#{user_id}, #{provider}, #{external_id}) "
            + "ON CONFLICT (user_id, provider) DO UPDATE SET external_id = EXCLUDED.external_id, updated_at = CURRENT_TIMESTAMP";

    /**
     * provider + externalId로 사용자 ID 조회
     */
    public Future<Long> getUserIdByProviderAndExternalId(SqlClient client, String provider, String externalId) {
        String sql = SQL_GET_USER_ID_BY_PROVIDER_AND_EXTERNAL_ID;
        
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
        String sql = SQL_GET_EXTERNAL_ID_BY_USER_AND_PROVIDER;

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
        String sql = SQL_UPSERT_USER_EXTERNAL_ID;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("provider", provider);
        params.put("external_id", externalId);

        return query(client, sql, params)
            .map(rows -> (Void) null)
            .onFailure(throwable -> log.error("외부 사용자 매핑 upsert 실패 - userId: {}, provider: {}, externalId: {}", userId, provider, externalId));
    }
}
