package com.foxya.coin.auth;

import com.foxya.coin.common.BaseRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SocialLinkRepository extends BaseRepository {
    
    public Future<Boolean> hasSocialLink(SqlClient client, Long userId) {
        String sql = """
            SELECT COUNT(*) as count
            FROM social_links
            WHERE user_id = #{userId}
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return query(client, sql, params)
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
        String sql = """
            INSERT INTO social_links (user_id, provider, provider_user_id, email)
            VALUES (#{userId}, #{provider}, #{providerUserId}, #{email})
            ON CONFLICT (user_id, provider) DO NOTHING
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("provider", provider);
        params.put("providerUserId", providerUserId);
        params.put("email", email);
        
        return query(client, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }
}

