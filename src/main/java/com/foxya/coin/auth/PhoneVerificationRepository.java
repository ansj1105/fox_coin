package com.foxya.coin.auth;

import com.foxya.coin.common.BaseRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PhoneVerificationRepository extends BaseRepository {
    
    public Future<Boolean> isVerified(SqlClient client, Long userId) {
        String sql = """
            SELECT is_verified
            FROM phone_verifications
            WHERE user_id = #{userId}
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getBoolean("is_verified");
                }
                return false;
            });
    }
    
    public Future<Boolean> verifyPhone(SqlClient client, Long userId, String phoneNumber, String verificationCode) {
        String sql = """
            INSERT INTO phone_verifications (user_id, phone_number, verification_code, is_verified, verified_at, expires_at)
            VALUES (#{userId}, #{phoneNumber}, #{verificationCode}, true, CURRENT_TIMESTAMP, #{expiresAt})
            ON CONFLICT (user_id)
            DO UPDATE SET
                phone_number = EXCLUDED.phone_number,
                verification_code = EXCLUDED.verification_code,
                is_verified = true,
                verified_at = CURRENT_TIMESTAMP,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("phoneNumber", phoneNumber);
        params.put("verificationCode", verificationCode);
        params.put("expiresAt", LocalDateTime.now().plusMinutes(10));
        
        return query(client, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }
}

