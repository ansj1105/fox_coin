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
        // ON CONFLICT는 PostgreSQL 특화 기능으로 QueryBuilder에서 직접 지원하지 않으므로 selectStringQuery 사용
        String sql = """
            INSERT INTO social_links (user_id, provider, provider_user_id, email)
            VALUES (#{userId}, #{provider}, #{providerUserId}, #{email})
            ON CONFLICT (user_id, provider) DO NOTHING
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("provider", provider);
        params.put("providerUserId", providerUserId);
        params.put("email", email);
        
        return query(client, query, params)
            .map(rows -> rows.rowCount() > 0);
    }
}

