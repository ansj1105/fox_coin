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

    public Future<String> getMinAppVersion(SqlClient client) {
        return getByKey(client, KEY_MIN_APP_VERSION);
    }
}
