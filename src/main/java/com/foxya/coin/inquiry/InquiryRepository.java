package com.foxya.coin.inquiry;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.inquiry.entities.Inquiry;
import com.foxya.coin.inquiry.enums.InquiryStatus;
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
}

