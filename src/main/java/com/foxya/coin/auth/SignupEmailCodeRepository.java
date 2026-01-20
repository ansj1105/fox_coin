package com.foxya.coin.auth;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SignupEmailCodeRepository extends BaseRepository {

    public static class SignupEmailCode {
        public Long id;
        public String email;
        public String code;
        public LocalDateTime expiresAt;
        public LocalDateTime usedAt;
        public LocalDateTime createdAt;
    }

    private final RowMapper<SignupEmailCode> mapper = row -> {
        SignupEmailCode r = new SignupEmailCode();
        r.id = getLongColumnValue(row, "id");
        r.email = getStringColumnValue(row, "email");
        r.code = getStringColumnValue(row, "code");
        r.expiresAt = getLocalDateTimeColumnValue(row, "expires_at");
        r.usedAt = getLocalDateTimeColumnValue(row, "used_at");
        r.createdAt = getLocalDateTimeColumnValue(row, "created_at");
        return r;
    };

    /** send-code: upsert (email당 1행, 재발송 시 code/expires 갱신, used_at=null) */
    public Future<Boolean> upsert(SqlClient client, String email, String code, LocalDateTime expiresAt) {
        String sql = QueryBuilder
            .insert("signup_email_codes", "email", "code", "expires_at")
            .onConflict("email")
            .doUpdateCustom("code = EXCLUDED.code, expires_at = EXCLUDED.expires_at, used_at = NULL, updated_at = CURRENT_TIMESTAMP")
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("code", code);
        params.put("expires_at", expiresAt);
        return query(client, sql, params).map(rows -> rows.rowCount() > 0);
    }

    /** verify/register: email+code 일치, 만료 전, 미사용 조회 */
    public Future<SignupEmailCode> findValid(SqlClient client, String email, String code) {
        String sql = QueryBuilder
            .select("signup_email_codes")
            .where("email", Op.Equal, "email")
            .andWhere("code", Op.Equal, "code")
            .andWhere("used_at", Op.IsNull)
            .andWhere("expires_at", Op.GreaterThanOrEqual, "now")
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("code", code);
        params.put("now", LocalDateTime.now());
        return query(client, sql, params)
            .map(rows -> fetchOne(mapper, rows));
    }

    /** register 성공 시 1회용 소진 */
    public Future<Void> markUsed(SqlClient client, String email) {
        String sql = """
            UPDATE signup_email_codes
            SET used_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE email = #{email}
            """;
        String q = com.foxya.coin.utils.QueryBuilder.selectStringQuery(sql).build();
        return query(client, q, Collections.singletonMap("email", email)).map(v -> null);
    }
}
