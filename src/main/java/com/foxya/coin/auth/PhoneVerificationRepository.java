package com.foxya.coin.auth;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import io.vertx.core.Future;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PhoneVerificationRepository extends BaseRepository {
    
    public Future<Boolean> isVerified(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("phone_verifications", "is_verified")
            .where("user_id", Op.Equal, "userId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getBoolean("is_verified");
                }
                return false;
            });
    }
    
    public Future<Boolean> verifyPhone(SqlClient client, Long userId, String phoneNumber, String verificationCode) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        LocalDateTime verifiedAt = LocalDateTime.now();
        
        String sql = QueryBuilder
            .insert("phone_verifications", "user_id", "phone_number", "verification_code", "is_verified", "verified_at", "expires_at")
            .onConflict("user_id")
            .doUpdateCustom("phone_number = EXCLUDED.phone_number, verification_code = EXCLUDED.verification_code, is_verified = true, verified_at = CURRENT_TIMESTAMP, expires_at = EXCLUDED.expires_at, updated_at = CURRENT_TIMESTAMP")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("phone_number", phoneNumber);
        params.put("verification_code", verificationCode);
        params.put("is_verified", true);
        params.put("verified_at", verifiedAt);
        params.put("expires_at", expiresAt);
        
        return query(client, sql, params)
            .map(rows -> rows.rowCount() > 0);
    }
    
    /**
     * 사용자의 전화번호 인증 Soft Delete
     */
    public Future<Void> softDeletePhoneVerificationByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("phone_verifications", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deleted_at", java.time.LocalDateTime.now());
        params.put("updated_at", java.time.LocalDateTime.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Phone verification soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("전화번호 인증 Soft Delete 실패 - userId: {}", userId, throwable));
    }
}

