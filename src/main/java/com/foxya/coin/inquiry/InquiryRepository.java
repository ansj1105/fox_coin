package com.foxya.coin.inquiry;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.inquiry.entities.Inquiry;
import com.foxya.coin.inquiry.enums.InquiryStatus;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class InquiryRepository extends BaseRepository {
    
    private final RowMapper<Inquiry> inquiryMapper = row -> Inquiry.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .subject(getStringColumnValue(row, "subject"))
        .content(getStringColumnValue(row, "content"))
        .email(getStringColumnValue(row, "email"))
        .status(InquiryStatus.valueOf(getStringColumnValue(row, "status")))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    /**
     * 문의 생성
     */
    public Future<Inquiry> createInquiry(SqlClient client, Long userId, String subject, 
                                         String content, String email) {
        String sql = com.foxya.coin.utils.QueryBuilder.insert(
            "inquiries", 
            Map.of(
                "user_id", userId,
                "subject", subject,
                "content", content,
                "email", email,
                "status", InquiryStatus.PENDING.name()
            ),
            "id, user_id, subject, content, email, status, created_at, updated_at"
        );
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("subject", subject);
        params.put("content", content);
        params.put("email", email);
        params.put("status", InquiryStatus.PENDING.name());
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return inquiryMapper.map(rows.iterator().next());
                }
                return null;
            })
            .onFailure(throwable -> log.error("문의 생성 실패: {}", throwable.getMessage()));
    }
    
    /**
     * 사용자의 모든 문의 Soft Delete
     */
    public Future<Inquiry> getInquiryById(SqlClient client, Long inquiryId) {
        String sql = QueryBuilder
            .select("inquiries", "id", "user_id", "subject", "content", "email", "status", "created_at", "updated_at")
            .where("id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("id", inquiryId);

        return query(client, sql, params)
            .map(rows -> rows.iterator().hasNext() ? inquiryMapper.map(rows.iterator().next()) : null);
    }

    public Future<Inquiry> updateInquiryStatus(SqlClient client, Long inquiryId, InquiryStatus status) {
        String sql = QueryBuilder
            .update("inquiries", "status", "updated_at")
            .where("id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .returning("id, user_id, subject, content, email, status, created_at, updated_at");

        Map<String, Object> params = new HashMap<>();
        params.put("id", inquiryId);
        params.put("status", status.name());
        params.put("updated_at", java.time.LocalDateTime.now());

        return query(client, sql, params)
            .map(rows -> rows.iterator().hasNext() ? inquiryMapper.map(rows.iterator().next()) : null);
    }

    public Future<Void> softDeleteInquiriesByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("inquiries", "deleted_at", "updated_at")
            .where("user_id", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "user_id")
            .andWhere("deleted_at", com.foxya.coin.utils.BaseQueryBuilder.Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", java.time.LocalDateTime.now());
        params.put("updated_at", java.time.LocalDateTime.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Inquiries soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("문의 Soft Delete 실패 - userId: {}", userId, throwable));
    }
}

