package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
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
        .username(getStringColumnValue(row, "username"))
        .email(getStringColumnValue(row, "email"))
        .passwordHash(getStringColumnValue(row, "password_hash"))
        .phone(getStringColumnValue(row, "phone"))
        .status(getStringColumnValue(row, "status"))
        .referralCode(getStringColumnValue(row, "referral_code"))
        .referredBy(getLongColumnValue(row, "referred_by"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    public Future<User> createUser(SqlClient client, CreateUserDto dto) {
        String sql = QueryBuilder.insert("users", dto, "*");
        return query(client, sql, dto.toMap())
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 생성 실패: {}", throwable.getMessage()));
    }
    
    public Future<User> getUserByUsername(SqlClient client, String username) {
        String sql = QueryBuilder
            .select("users")
            .where("username", Op.Equal, "username")
            .build();
        
        return query(client, sql, Collections.singletonMap("username", username))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패 - username: {}", username));
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
}

