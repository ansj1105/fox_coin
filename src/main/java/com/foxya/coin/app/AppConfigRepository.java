package com.foxya.coin.app;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

@Slf4j
public class AppConfigRepository extends BaseRepository {

    private static final String KEY_MIN_APP_VERSION = "min_app_version";

    /**
     * config_key에 해당하는 config_value 반환. 없거나 빈 값이면 null.
     */
    public Future<String> getByKey(SqlClient client, String configKey) {
        String sql = QueryBuilder.select("app_config")
            .where("config_key", Op.Equal, "configKey")
            .build();
        Map<String, Object> params = Collections.singletonMap("configKey", configKey);
        return query(client, sql, params)
            .map(rows -> {
                if (rows.size() == 0) return null;
                String value = rows.iterator().next().getString("config_value");
                return (value != null && !value.isBlank()) ? value.trim() : null;
            });
    }

    /**
     * min_app_version 설정값 조회.
     * Android: config_value 사용, iOS: config_value_apple 사용 (비어 있으면 config_value 사용).
     */
    public Future<String> getMinAppVersion(SqlClient client, boolean isIos) {
        String sql = QueryBuilder.select("app_config", "config_value", "config_value_apple")
            .where("config_key", Op.Equal, "configKey")
            .build();
        Map<String, Object> params = Collections.singletonMap("configKey", KEY_MIN_APP_VERSION);
        return query(client, sql, params)
            .map(rows -> {
                if (rows.size() == 0) return null;
                var row = rows.iterator().next();
                String value = row.getString("config_value");
                String valueApple = getStringColumnValue(row, "config_value_apple");
                if (isIos && valueApple != null && !valueApple.isBlank()) {
                    return valueApple.trim();
                }
                return (value != null && !value.isBlank()) ? value.trim() : null;
            });
    }

    /**
     * min_app_version 설정값 조회 (Android 기준 config_value).
     * @deprecated 플랫폼별 버전이 필요하면 {@link #getMinAppVersion(SqlClient, boolean)} 사용
     */
    @Deprecated
    public Future<String> getMinAppVersion(SqlClient client) {
        return getMinAppVersion(client, false);
    }
}
