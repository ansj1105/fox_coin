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
        String sql = QueryBuilder
            .insert("reviews", "user_id", "platform", "review_id", "reviewed_at")
            .onConflict("user_id")
            .doNothing()
            .returningColumns("id, user_id, platform, review_id, reviewed_at, created_at, updated_at")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("platform", platform);
        params.put("review_id", reviewId);
        params.put("reviewed_at", java.time.LocalDateTime.now());
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return REVIEW_MAPPER.map(rows.iterator().next());
                }
                return null;
            });
    }
}

