package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class UserRepository extends BaseRepository {
    
    private final RowMapper<User> userMapper = row -> User.builder()
        .id(getLongColumnValue(row, "id"))
        .loginId(getStringColumnValue(row, "login_id"))
        .passwordHash(getStringColumnValue(row, "password_hash"))
        .referralCode(getStringColumnValue(row, "referral_code"))
        .status(getStringColumnValue(row, "status"))
        .level(getIntegerColumnValue(row, "level"))
        .exp(getBigDecimalColumnValue(row, "exp"))
        .countryCode(getStringColumnValue(row, "country_code"))
        .deletedAt(getLocalDateTimeColumnValue(row, "deleted_at"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    public Future<User> createUser(SqlClient client, CreateUserDto dto) {
        String sql = QueryBuilder.insert("users", dto, "*");
        return query(client, sql, dto.toMap())
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 생성 실패: {}", throwable.getMessage()));
    }
    
    public Future<User> getUserByLoginId(SqlClient client, String loginId) {
        String sql = QueryBuilder
            .select("users")
            .where("login_id", Op.Equal, "login_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        return query(client, sql, Collections.singletonMap("login_id", loginId))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - loginId: {}", loginId));
    }
    
    public Future<User> getUserById(SqlClient client, Long id) {
        String sql = QueryBuilder
            .select("users")
            .whereById()
            .andWhere("deleted_at", Op.IsNull)
            .build();

        return query(client, sql, Collections.singletonMap("id", id))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - id: {}", id));
    }
    
    /**
     * 삭제되지 않은 사용자 조회 (not_deleted 전용)
     */
    public Future<User> getUserByIdNotDeleted(SqlClient client, Long id) {
        return getUserById(client, id);
    }
    
    /**
     * 삭제되지 않은 사용자 조회 (loginId로)
     */
    public Future<User> getUserByLoginIdNotDeleted(SqlClient client, String loginId) {
        return getUserByLoginId(client, loginId);
    }
    
    /**
     * 삭제되지 않은 사용자 조회 (레퍼럴 코드로)
     */
    public Future<User> getUserByReferralCodeNotDeleted(SqlClient client, String referralCode) {
        String sql = QueryBuilder
            .select("users")
            .where("referral_code", Op.Equal, "referral_code")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        return query(client, sql, Collections.singletonMap("referral_code", referralCode))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - referralCode: {}", referralCode));
    }
    
    /**
     * 사용자 Soft Delete (회원 탈퇴)
     */
    public Future<User> softDeleteUser(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("users", "deleted_at", "updated_at", "status")
            .whereById()
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");
        
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("deleted_at", DateUtils.now());
        params.put("updated_at", DateUtils.now());
        params.put("status", "DELETED");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 Soft Delete 실패 - userId: {}", userId));
    }
    
    /**
     * 레퍼럴 코드로 사용자 조회
     */
    public Future<User> getUserByReferralCode(SqlClient client, String referralCode) {
        String sql = QueryBuilder
            .select("users")
            .where("referral_code", Op.Equal, "referral_code")
            .build();

        return query(client, sql, Collections.singletonMap("referral_code", referralCode))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - referralCode: {}", referralCode));
    }
    
    /**
     * 레퍼럴 코드로 사용자 존재 여부 확인
     */
    public Future<Boolean> existsByReferralCode(SqlClient client, String referralCode) {
        String sql = QueryBuilder
            .select("users", "COUNT(*) as count")
            .where("referral_code", Op.Equal, "referral_code")
            .build();
        
        return query(client, sql, Collections.singletonMap("referral_code", referralCode))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long count = getLongColumnValue(rows.iterator().next(), "count");
                    return count != null && count > 0;
                }
                return false;
            })
            .onFailure(throwable -> log.error("레퍼럴 코드 존재 여부 확인 실패: {}", referralCode));
    }
    
    /**
     * 레퍼럴 코드 업데이트
     */
    public Future<User> updateReferralCode(SqlClient client, Long userId, String referralCode) {
        String sql = QueryBuilder
            .update("users", "referral_code", "updated_at")
            .whereById()
            .returning("*");
        
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("referral_code", referralCode);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("레퍼럴 코드 업데이트 실패 - userId: {}, referralCode: {}", userId, referralCode));
    }

    /**
     * 거래 비밀번호(해시) 업데이트
     */
    public Future<User> updateTransactionPassword(SqlClient client, Long userId, String transactionPasswordHash) {
        String sql = QueryBuilder
            .update("users", "transaction_password_hash", "updated_at")
            .whereById()
            .returning("*");

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("transaction_password_hash", transactionPasswordHash);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("거래 비밀번호 업데이트 실패 - userId: {}", userId));
    }

    /**
     * 로그인 비밀번호(해시) 업데이트
     */
    public Future<Void> updateLoginPassword(SqlClient client, Long userId, String passwordHash) {
        String sql = QueryBuilder
            .update("users", "password_hash", "updated_at")
            .whereById()
            .build();

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("password_hash", passwordHash);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> (Void) null)
            .onFailure(throwable -> log.error("로그인 비밀번호 업데이트 실패 - userId: {}", userId));
    }

    /**
     * 이메일로 회원 조회 (아이디 찾기용)
     * - login_id = email
     * - email_verifications.email (is_verified) 
     * - social_links.email
     */
    public Future<User> getUserByEmailForFindLoginId(SqlClient client, String email) {
        String sql = """
            SELECT u.* FROM users u
            WHERE u.deleted_at IS NULL
              AND (
                u.login_id = #{email}
                OR EXISTS (
                  SELECT 1 FROM email_verifications ev
                  WHERE ev.user_id = u.id AND ev.email = #{email} AND ev.is_verified = true
                    AND (ev.deleted_at IS NULL)
                )
                OR EXISTS (
                  SELECT 1 FROM social_links sl
                  WHERE sl.user_id = u.id AND sl.email = #{email}
                    AND (sl.deleted_at IS NULL)
                )
              )
            LIMIT 1
            """;
        String built = QueryBuilder.selectStringQuery(sql).build();
        return query(client, built, Collections.singletonMap("email", email))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("이메일로 회원 조회 실패 (find-login-id) - email: {}", email, throwable));
    }

    /**
     * 이메일로 회원 조회 (임시 비밀번호 발송용)
     * - login_id = email 또는 email_verifications.email (is_verified) 만.
     * - social_links 만으로 찾은 회원(소셜 전용)은 제외.
     */
    public Future<User> getUserByEmailForSendTempPassword(SqlClient client, String email) {
        String sql = """
            SELECT u.* FROM users u
            WHERE u.deleted_at IS NULL
              AND u.password_hash IS NOT NULL
              AND (
                u.login_id = #{email}
                OR EXISTS (
                  SELECT 1 FROM email_verifications ev
                  WHERE ev.user_id = u.id AND ev.email = #{email} AND ev.is_verified = true
                    AND (ev.deleted_at IS NULL)
                )
              )
            LIMIT 1
            """;
        String built = QueryBuilder.selectStringQuery(sql).build();
        return query(client, built, Collections.singletonMap("email", email))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("이메일로 회원 조회 실패 (send-temp-password) - email: {}", email, throwable));
    }
}
