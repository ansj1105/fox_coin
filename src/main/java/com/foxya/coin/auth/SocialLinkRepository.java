package com.foxya.coin.auth;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SocialLinkRepository extends BaseRepository {
    
    public Future<Boolean> hasSocialLink(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .count("social_links")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = rows.iterator().next().getLong("count");
                    return count != null && count > 0;
                }
                return false;
            });
    }
    
    public Future<Boolean> createSocialLink(SqlClient client, Long userId, String provider, 
                                          String providerUserId, String email) {
        String sql = QueryBuilder
            .insert("social_links", "user_id", "provider", "provider_user_id", "email")
            .onConflict("user_id, provider")
            .doNothing()
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("provider", provider);
        params.put("provider_user_id", providerUserId);
        params.put("email", email);
        
        return query(client, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }
}

