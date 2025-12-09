package com.foxya.coin.review;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.review.entities.Review;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

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
        String sql = """
            SELECT COUNT(*) as count
            FROM reviews
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
    
    public Future<Review> getReview(SqlClient client, Long userId) {
        String sql = """
            SELECT id, user_id, platform, review_id, reviewed_at, created_at, updated_at
            FROM reviews
            WHERE user_id = #{userId}
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return REVIEW_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
    
    public Future<Review> createReview(SqlClient client, Long userId, String platform, String reviewId) {
        String sql = """
            INSERT INTO reviews (user_id, platform, review_id, reviewed_at)
            VALUES (#{userId}, #{platform}, #{reviewId}, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id) DO NOTHING
            RETURNING id, user_id, platform, review_id, reviewed_at, created_at, updated_at
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("platform", platform);
        params.put("reviewId", reviewId);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return REVIEW_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

