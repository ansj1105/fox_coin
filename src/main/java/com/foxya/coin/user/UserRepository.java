package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Row;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class UserRepository extends BaseRepository {

    @lombok.Builder
    @lombok.Getter
    public static class LevelSyncCandidate {
        private Long userId;
        private Integer currentLevel;
        private Integer computedLevel;
    }
    
    private final RowMapper<User> userMapper = row -> User.builder()
        .id(getLongColumnValue(row, "id"))
        .loginId(getStringColumnValue(row, "login_id"))
        .passwordHash(getStringColumnValue(row, "password_hash"))
        .referralCode(getStringColumnValue(row, "referral_code"))
        .status(getStringColumnValue(row, "status"))
        .level(getIntegerColumnValue(row, "level"))
        .exp(getBigDecimalColumnValue(row, "exp"))
        .transactionPasswordHash(getStringColumnValue(row, "transaction_password_hash"))
        .countryCode(getStringColumnValue(row, "country_code"))
        .profileImageUrl(getStringColumnValue(row, "profile_image_url"))
        .nickname(getStringColumnValue(row, "nickname"))
        .name(getStringColumnValue(row, "name"))
        .gender(getStringColumnValue(row, "gender"))
        .phone(getStringColumnValue(row, "phone"))
        .isTest(getIntegerColumnValue(row, "is_test"))
        .referralAirdropRewarded(getBooleanColumnValue(row, "referral_airdrop_rewarded"))
        .appReviewRewarded(getBooleanColumnValue(row, "app_review_rewarded"))
        .isWarning(getIntegerColumnValue(row, "is_warning"))
        .isMiningSuspended(getIntegerColumnValue(row, "is_mining_suspended"))
        .isAccountBlocked(getIntegerColumnValue(row, "is_account_blocked"))
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

    public Future<User> getUserByLoginIdIncludingDeleted(SqlClient client, String loginId) {
        String sql = QueryBuilder
            .select("users")
            .where("login_id", Op.Equal, "login_id")
            .build();

        return query(client, sql, Collections.singletonMap("login_id", loginId))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패(삭제 포함) - loginId: {}", loginId));
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
     * 경험치(EXP) 증가 (친구 초대 1명당 +0.5 EXP 등)
     */
    public Future<User> addExp(SqlClient client, Long userId, java.math.BigDecimal delta) {
        if (delta == null || delta.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return getUserById(client, userId);
        }
        String sql = QueryBuilder.update("users")
            .setCustom("exp = COALESCE(exp, 0) + #{delta}")
            .setCustom("updated_at = #{updated_at}")
            .where("id", Op.Equal, "id")
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("delta", delta);
        params.put("updated_at", DateUtils.now());
        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("EXP 증가 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 레벨 업데이트 (EXP 연동 레벨업용)
     */
    public Future<User> updateLevel(SqlClient client, Long userId, int level) {
        String sql = QueryBuilder
            .update("users", "level", "updated_at")
            .whereById()
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("level", level);
        params.put("updated_at", DateUtils.now());
        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("레벨 업데이트 실패 - userId: {}", userId, throwable));
    }

    /**
     * 프로필 이미지 URL 업데이트
     */
    public Future<User> updateProfileImageUrl(SqlClient client, Long userId, String profileImageUrl) {
        String sql = QueryBuilder
            .update("users", "profile_image_url", "updated_at")
            .whereById()
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("profile_image_url", profileImageUrl);
        params.put("updated_at", DateUtils.now());
        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("프로필 이미지 URL 업데이트 실패 - userId: {}", userId, throwable));
    }

    /**
     * EXP 기준 계산 레벨이 현재 레벨보다 높은 사용자 목록을 ID cursor 순서로 조회
     */
    public Future<List<LevelSyncCandidate>> findUsersRequiringLevelSync(SqlClient client, Long afterUserId, int limit) {
        String sql = """
            SELECT
                u.id AS user_id,
                COALESCE(u.level, 1) AS current_level,
                CASE
                    WHEN COALESCE(u.exp, 0) >= 520 THEN 9
                    WHEN COALESCE(u.exp, 0) >= 350 THEN 8
                    WHEN COALESCE(u.exp, 0) >= 220 THEN 7
                    WHEN COALESCE(u.exp, 0) >= 130 THEN 6
                    WHEN COALESCE(u.exp, 0) >= 70 THEN 5
                    WHEN COALESCE(u.exp, 0) >= 35 THEN 4
                    WHEN COALESCE(u.exp, 0) >= 15 THEN 3
                    WHEN COALESCE(u.exp, 0) >= 5 THEN 2
                    ELSE 1
                END AS computed_level
            FROM users u
            WHERE u.deleted_at IS NULL
              AND u.id > #{after_user_id}
              AND (
                CASE
                    WHEN COALESCE(u.exp, 0) >= 520 THEN 9
                    WHEN COALESCE(u.exp, 0) >= 350 THEN 8
                    WHEN COALESCE(u.exp, 0) >= 220 THEN 7
                    WHEN COALESCE(u.exp, 0) >= 130 THEN 6
                    WHEN COALESCE(u.exp, 0) >= 70 THEN 5
                    WHEN COALESCE(u.exp, 0) >= 35 THEN 4
                    WHEN COALESCE(u.exp, 0) >= 15 THEN 3
                    WHEN COALESCE(u.exp, 0) >= 5 THEN 2
                    ELSE 1
                END
              ) > COALESCE(u.level, 1)
            ORDER BY u.id ASC
            LIMIT #{limit}
            """;
        String built = QueryBuilder.selectStringQuery(sql).build();
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("after_user_id", afterUserId != null ? afterUserId : 0L);
        params.put("limit", Math.max(1, Math.min(limit, 2000)));

        return query(client, built, params)
            .map(rows -> {
                List<LevelSyncCandidate> result = new ArrayList<>();
                for (var row : rows) {
                    result.add(LevelSyncCandidate.builder()
                        .userId(getLongColumnValue(row, "user_id"))
                        .currentLevel(getIntegerColumnValue(row, "current_level"))
                        .computedLevel(getIntegerColumnValue(row, "computed_level"))
                        .build());
                }
                return result;
            })
            .onFailure(throwable -> log.error("레벨 동기화 대상 조회 실패 - afterUserId: {}, limit: {}", afterUserId, limit, throwable));
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
     * 닉네임 존재 여부 (삭제 제외)
     */
    public Future<Boolean> existsByNickname(SqlClient client, String nickname) {
        if (nickname == null || nickname.isBlank()) return Future.succeededFuture(false);
        String sql = QueryBuilder
            .select("users", "COUNT(*) as count")
            .where("nickname", Op.Equal, "nickname")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        return query(client, sql, Collections.singletonMap("nickname", nickname))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long c = getLongColumnValue(rows.iterator().next(), "count");
                    return c != null && c > 0;
                }
                return false;
            });
    }

    /**
     * 다른 사용자가 해당 닉네임을 사용 중인지 (excludeUserId 제외)
     */
    public Future<Boolean> existsByNicknameExcludingUser(SqlClient client, String nickname, Long excludeUserId) {
        if (nickname == null || nickname.isBlank()) return Future.succeededFuture(false);
        String sql = QueryBuilder
            .select("users", "COUNT(*) as count")
            .where("nickname", Op.Equal, "nickname")
            .andWhere("deleted_at", Op.IsNull)
            .andWhere("id", Op.NotEqual, "exclude_id")
            .build();
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("nickname", nickname);
        params.put("exclude_id", excludeUserId);
        return query(client, sql, params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    Long c = getLongColumnValue(rows.iterator().next(), "count");
                    return c != null && c > 0;
                }
                return false;
            });
    }

    /**
     * 내 정보 수정 (name, nickname, phone 일부만 전달해도 됨)
     */
    public Future<Void> updateMeProfile(SqlClient client, Long userId, java.util.Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) return Future.succeededFuture();
        updates.put("id", userId);
        updates.put("updated_at", DateUtils.now());
        String setClause = updates.keySet().stream()
            .filter(k -> !"id".equals(k))
            .map(k -> k + " = #{" + k + "}")
            .reduce((a, b) -> a + ", " + b)
            .orElse("updated_at = #{updated_at}");
        String sql = "UPDATE users SET " + setClause + " WHERE id = #{id}";
        String q = QueryBuilder.selectStringQuery(sql).build();
        return query(client, q, updates).map(v -> null);
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
     * 추천인 등록 에어드랍 지급 완료 플래그 업데이트
     */
    public Future<Void> updateReferralAirdropRewarded(SqlClient client, Long userId, boolean rewarded) {
        String sql = QueryBuilder
            .update("users", "referral_airdrop_rewarded", "updated_at")
            .whereById()
            .andWhere("deleted_at", Op.IsNull)
            .build();

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("referral_airdrop_rewarded", rewarded);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .<Void>map(rows -> null)
            .onFailure(throwable -> log.error("추천인 에어드랍 지급 플래그 업데이트 실패 - userId: {}", userId, throwable));
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
     * 로그인 ID(이메일) 업데이트. 이메일 변경 완료 시 새 이메일로 로그인 가능하도록.
     */
    public Future<Void> updateLoginId(SqlClient client, Long userId, String newLoginId) {
        String sql = QueryBuilder
            .update("users", "login_id", "updated_at")
            .whereById()
            .build();

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("login_id", newLoginId);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> (Void) null)
            .onFailure(throwable -> log.error("로그인 ID 업데이트 실패 - userId: {}", userId));
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

    public Future<Long> countActiveUsers(SqlClient client) {
        String sql = """
            SELECT COUNT(*) AS total
            FROM users
            WHERE deleted_at IS NULL
            """;

        return query(client, QueryBuilder.selectStringQuery(sql).build())
            .map(rows -> {
                Row row = rows.iterator().next();
                Long total = getLongColumnValue(row, "total");
                return total == null ? 0L : total;
            })
            .onFailure(throwable -> log.error("활성 사용자 수 조회 실패", throwable));
    }

    public Future<List<Long>> getActiveUserIdsAfter(SqlClient client, Long afterUserId, int limit) {
        String sql = """
            SELECT id
            FROM users
            WHERE deleted_at IS NULL
              AND id > #{afterUserId}
            ORDER BY id ASC
            LIMIT #{limit}
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("afterUserId", afterUserId == null ? 0L : afterUserId);
        params.put("limit", Math.max(1, Math.min(limit, 5000)));

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> {
                List<Long> userIds = new ArrayList<>();
                for (Row row : rows) {
                    Long userId = getLongColumnValue(row, "id");
                    if (userId != null && userId > 0L) {
                        userIds.add(userId);
                    }
                }
                return userIds;
            })
            .onFailure(throwable -> log.error("활성 사용자 ID 조회 실패 - afterUserId: {}, limit: {}", afterUserId, limit, throwable));
    }
}
