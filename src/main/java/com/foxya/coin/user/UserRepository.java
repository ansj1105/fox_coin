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
            .build();
        
        return query(client, sql, Collections.singletonMap("login_id", loginId))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - loginId: {}", loginId));
    }
    
    public Future<User> getUserById(SqlClient client, Long id) {
        String sql = QueryBuilder
            .select("users")
            .whereById()
            .build();

        return query(client, sql, Collections.singletonMap("id", id))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - id: {}", id));
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
}
