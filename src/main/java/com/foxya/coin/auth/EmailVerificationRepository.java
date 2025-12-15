package com.foxya.coin.auth;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class EmailVerificationRepository extends BaseRepository {

    public static class EmailVerification {
        public Long id;
        public Long userId;
        public String email;
        public String verificationCode;
        public Boolean isVerified;
        public LocalDateTime verifiedAt;
        public LocalDateTime expiresAt;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    private final RowMapper<EmailVerification> mapper = row -> {
        EmailVerification ev = new EmailVerification();
        ev.id = getLongColumnValue(row, "id");
        ev.userId = getLongColumnValue(row, "user_id");
        ev.email = getStringColumnValue(row, "email");
        ev.verificationCode = getStringColumnValue(row, "verification_code");
        ev.isVerified = getBooleanColumnValue(row, "is_verified");
        ev.verifiedAt = getLocalDateTimeColumnValue(row, "verified_at");
        ev.expiresAt = getLocalDateTimeColumnValue(row, "expires_at");
        ev.createdAt = getLocalDateTimeColumnValue(row, "created_at");
        ev.updatedAt = getLocalDateTimeColumnValue(row, "updated_at");
        return ev;
    };

    /**
     * 사용자별 최신 이메일 인증 정보 조회
     */
    public Future<EmailVerification> getLatestByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("email_verifications")
            .where("user_id", Op.Equal, "user_id")
            .orderBy("created_at", Sort.DESC)
            .limit(1)
            .build();

        return query(client, sql, Collections.singletonMap("user_id", userId))
            .map(rows -> fetchOne(mapper, rows))
            .onFailure(throwable -> log.error("이메일 인증 정보 조회 실패 - userId: {}", userId, throwable));
    }

    /**
     * 이메일 인증 코드 저장/갱신
     */
    public Future<Boolean> upsertVerification(SqlClient client, Long userId, String email, String code, LocalDateTime expiresAt) {
        String sql = """
            INSERT INTO email_verifications (user_id, email, verification_code, is_verified, verified_at, expires_at)
            VALUES (#{user_id}, #{email}, #{verification_code}, false, NULL, #{expires_at})
            ON CONFLICT (user_id)
            DO UPDATE SET
                email = EXCLUDED.email,
                verification_code = EXCLUDED.verification_code,
                is_verified = false,
                verified_at = NULL,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            """;

        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("email", email);
        params.put("verification_code", code);
        params.put("expires_at", expiresAt);

        return query(client, query, params)
            .map(rows -> rows.rowCount() > 0)
            .onFailure(throwable -> log.error("이메일 인증 코드 저장 실패 - userId: {}, email: {}", userId, email, throwable));
    }

    /**
     * 이메일 인증 코드 검증 및 인증 처리
     */
    public Future<Boolean> verifyEmail(SqlClient client, Long userId, String email, String code) {
        String sql = """
            UPDATE email_verifications
            SET is_verified = true,
                verified_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{user_id}
              AND email = #{email}
              AND verification_code = #{verification_code}
              AND expires_at >= CURRENT_TIMESTAMP
            """;

        String query = QueryBuilder.selectStringQuery(sql).build();
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("email", email);
        params.put("verification_code", code);

        return query(client, query, params)
            .map(rows -> rows.rowCount() > 0)
            .onFailure(throwable -> log.error("이메일 인증 코드 검증 실패 - userId: {}, email: {}", userId, email, throwable));
    }
}


