package com.foxya.coin.review;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.review.entities.Review;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ReviewRepository extends BaseRepository {
    
    private static final RowMapper<Review> REVIEW_MAPPER = row -> Review.builder()
        .id(row.getLong("id"))
        .userId(row.getLong("user_id"))
        .platform(row.getString("platform"))
        .reviewId(row.getString("review_id"))
        .reviewedAt(row.getLocalDateTime("reviewed_at"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
    
    public Future<Boolean> hasReview(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .count("reviews")
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
    
    public Future<Review> getReview(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("reviews", "id", "user_id", "platform", "review_id", "reviewed_at", "created_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return REVIEW_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    public Future<Review> createReview(SqlClient client, Long userId, String platform, String reviewId) {
        // ON CONFLICT는 PostgreSQL 특화 기능으로 QueryBuilder에서 직접 지원하지 않으므로 selectStringQuery 사용
        String sql = """
            INSERT INTO reviews (user_id, platform, review_id, reviewed_at)
            VALUES (#{userId}, #{platform}, #{reviewId}, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id) DO NOTHING
            RETURNING id, user_id, platform, review_id, reviewed_at, created_at, updated_at
            """;
        
        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("platform", platform);
        params.put("reviewId", reviewId);
        
        return query(client, query, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return REVIEW_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

