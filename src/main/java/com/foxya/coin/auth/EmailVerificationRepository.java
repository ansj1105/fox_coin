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
        String sql = QueryBuilder
            .insert("email_verifications", "user_id", "email", "verification_code", "is_verified", "verified_at", "expires_at")
            .onConflict("user_id")
            .doUpdateCustom("email = EXCLUDED.email, verification_code = EXCLUDED.verification_code, is_verified = false, verified_at = NULL, expires_at = EXCLUDED.expires_at, updated_at = CURRENT_TIMESTAMP")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("email", email);
        params.put("verification_code", code);
        params.put("is_verified", false);
        params.put("verified_at", null);
        params.put("expires_at", expiresAt);

        return query(client, sql, params)
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
    
    /**
     * 이메일이 이미 다른 사용자에게 인증되어 있는지 확인
     * @return 다른 사용자 ID (없으면 null)
     */
    public Future<Long> findVerifiedEmailUserId(SqlClient client, String email) {
        String sql = QueryBuilder
            .select("email_verifications")
            .where("email", Op.Equal, "email")
            .andWhere("is_verified", Op.Equal, "is_verified")
            .orderBy("verified_at", Sort.DESC)
            .limit(1)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("is_verified", true);
        
        return query(client, sql, params)
            .map(rows -> {
                EmailVerification ev = fetchOne(mapper, rows);
                return ev != null ? ev.userId : null;
            })
            .onFailure(throwable -> log.error("이메일 중복 확인 실패 - email: {}", email, throwable));
    }
}


