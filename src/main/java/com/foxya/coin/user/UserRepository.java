package com.foxya.coin.user;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Row;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.security.dto.OfflinePaySettingsDto;
import com.foxya.coin.security.dto.OfflinePayActivityLogDto;
import com.foxya.coin.security.dto.OfflinePayNotificationCenterDto;
import com.foxya.coin.security.dto.OfflinePaySettlementCenterDto;
import com.foxya.coin.security.dto.OfflinePaySharedDetailPublicDto;
import com.foxya.coin.security.dto.OfflinePayTrustCenterDto;
import com.foxya.coin.security.dto.OfflinePayTrustCenterLogDto;
import com.foxya.coin.user.dto.CreateUserDto;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
        .reviewPromptDismissed(getBooleanColumnValue(row, "review_prompt_dismissed"))
        .reviewPromptLastShownAt(getLocalDateTimeColumnValue(row, "review_prompt_last_shown_at"))
        .isWarning(getIntegerColumnValue(row, "is_warning"))
        .isMiningSuspended(getIntegerColumnValue(row, "is_mining_suspended"))
        .isAccountBlocked(getIntegerColumnValue(row, "is_account_blocked"))
        .offlinePayPinFailedAttempts(getIntegerColumnValue(row, "offline_pay_pin_failed_attempts"))
        .offlinePayPinLockedAt(getLocalDateTimeColumnValue(row, "offline_pay_pin_locked_at"))
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

    public Future<User> getUserByIdIncludingDeleted(SqlClient client, Long id) {
        String sql = QueryBuilder
            .select("users")
            .whereById()
            .build();

        return query(client, sql, Collections.singletonMap("id", id))
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 조회 실패(삭제 포함) - id: {}", id));
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

    public Future<User> restoreDeletedUser(SqlClient client, Long userId) {
        String sql = QueryBuilder.update("users")
            .setNull("deleted_at")
            .set("status", "status")
            .set("updated_at", "updated_at")
            .where("id", Op.Equal, "id")
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("id", userId);
        params.put("status", "ACTIVE");
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("사용자 복구 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 경험치(EXP) 증가 (채굴량, 친구 초대 1명당 +1 EXP 등)
     */
    public Future<User> addExp(SqlClient client, Long userId, java.math.BigDecimal delta) {
        if (delta == null || delta.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return getUserById(client, userId);
        }
        String sql = QueryBuilder.update("users")
            .increaseByParam("exp", "delta", true)
            .set("updated_at", "updated_at")
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
                COALESCE(ml.computed_level, 1) AS computed_level
            FROM users u
            LEFT JOIN LATERAL (
                SELECT MAX(level) AS computed_level
                FROM mining_levels
                WHERE required_exp <= COALESCE(u.exp, 0)
            ) ml ON TRUE
            WHERE u.deleted_at IS NULL
              AND u.id > #{after_user_id}
              AND COALESCE(ml.computed_level, 1) > COALESCE(u.level, 1)
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

    public Future<User> updateReviewPromptState(
        SqlClient client,
        Long userId,
        Boolean reviewPromptDismissed,
        boolean markShown
    ) {
        Map<String, Object> updates = new HashMap<>();
        if (reviewPromptDismissed != null) {
            updates.put("review_prompt_dismissed", reviewPromptDismissed);
        }
        if (markShown) {
            updates.put("review_prompt_last_shown_at", DateUtils.now());
        }
        if (updates.isEmpty()) {
            return getUserById(client, userId);
        }

        updates.put("id", userId);
        updates.put("updated_at", DateUtils.now());

        String setClause = updates.keySet().stream()
            .filter(k -> !"id".equals(k))
            .map(k -> k + " = #{" + k + "}")
            .reduce((a, b) -> a + ", " + b)
            .orElse("updated_at = #{updated_at}");

        String sql = "UPDATE users SET " + setClause + " WHERE id = #{id} AND deleted_at IS NULL RETURNING *";
        String q = QueryBuilder.selectStringQuery(sql).build();
        return query(client, q, updates)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("리뷰 프롬프트 상태 업데이트 실패 - userId: {}", userId, throwable));
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
            .update("users", "transaction_password_hash", "offline_pay_pin_failed_attempts", "offline_pay_pin_locked_at", "updated_at")
            .whereById()
            .returning("*");

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", userId);
        params.put("transaction_password_hash", transactionPasswordHash);
        params.put("offline_pay_pin_failed_attempts", 0);
        params.put("offline_pay_pin_locked_at", null);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("거래 비밀번호 업데이트 실패 - userId: {}", userId));
    }

    public Future<User> updateOfflinePayPinState(SqlClient client, Long userId, int failedAttempts, LocalDateTime lockedAt) {
        String sql = QueryBuilder
            .update("users", "offline_pay_pin_failed_attempts", "offline_pay_pin_locked_at", "updated_at")
            .whereById()
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("id", userId);
        params.put("offline_pay_pin_failed_attempts", failedAttempts);
        params.put("offline_pay_pin_locked_at", lockedAt);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(userMapper, rows))
            .onFailure(throwable -> log.error("오프라인 페이 PIN 상태 업데이트 실패 - userId: {}", userId, throwable));
    }

    public Future<User> resetOfflinePayPinState(SqlClient client, Long userId) {
        return updateOfflinePayPinState(client, userId, 0, null);
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

    public Future<OfflinePaySettingsDto> getOfflinePaySettings(SqlClient client, Long userId) {
        String sql = """
            SELECT
                security_level_high_enabled,
                face_id_setting_enabled,
                fingerprint_setting_enabled,
                payment_offline_enabled,
                payment_ble_enabled,
                payment_nfc_enabled,
                payment_approval_mode,
                settlement_auto_enabled,
                settlement_cycle_minutes,
                store_offline_enabled,
                store_ble_enabled,
                store_nfc_enabled,
                store_merchant_label,
                payment_completed_alert_enabled,
                incoming_request_alert_enabled,
                failed_alert_enabled,
                settlement_completed_alert_enabled,
                updated_at
            FROM offline_pay_user_settings
            WHERE user_id = #{user_id}
            """;
        return query(client, QueryBuilder.selectStringQuery(sql).build(), Collections.singletonMap("user_id", userId))
            .map(rows -> fetchOne(row -> OfflinePaySettingsDto.builder()
                .securityLevelHighEnabled(getBooleanColumnValue(row, "security_level_high_enabled"))
                .faceIdSettingEnabled(getBooleanColumnValue(row, "face_id_setting_enabled"))
                .fingerprintSettingEnabled(getBooleanColumnValue(row, "fingerprint_setting_enabled"))
                .paymentOfflineEnabled(getBooleanColumnValue(row, "payment_offline_enabled"))
                .paymentBleEnabled(getBooleanColumnValue(row, "payment_ble_enabled"))
                .paymentNfcEnabled(getBooleanColumnValue(row, "payment_nfc_enabled"))
                .paymentApprovalMode(getStringColumnValue(row, "payment_approval_mode"))
                .settlementAutoEnabled(getBooleanColumnValue(row, "settlement_auto_enabled"))
                .settlementCycleMinutes(getIntegerColumnValue(row, "settlement_cycle_minutes"))
                .storeOfflineEnabled(getBooleanColumnValue(row, "store_offline_enabled"))
                .storeBleEnabled(getBooleanColumnValue(row, "store_ble_enabled"))
                .storeNfcEnabled(getBooleanColumnValue(row, "store_nfc_enabled"))
                .storeMerchantLabel(getStringColumnValue(row, "store_merchant_label"))
                .paymentCompletedAlertEnabled(getBooleanColumnValue(row, "payment_completed_alert_enabled"))
                .incomingRequestAlertEnabled(getBooleanColumnValue(row, "incoming_request_alert_enabled"))
                .failedAlertEnabled(getBooleanColumnValue(row, "failed_alert_enabled"))
                .settlementCompletedAlertEnabled(getBooleanColumnValue(row, "settlement_completed_alert_enabled"))
                .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
                .build(), rows))
            .onFailure(throwable -> log.error("오프라인 페이 설정 조회 실패 - userId: {}", userId, throwable));
    }

    public Future<OfflinePaySettingsDto> upsertOfflinePaySettings(SqlClient client, Long userId, OfflinePaySettingsDto settings) {
        String sql = """
            INSERT INTO offline_pay_user_settings (
                user_id,
                security_level_high_enabled,
                face_id_setting_enabled,
                fingerprint_setting_enabled,
                payment_offline_enabled,
                payment_ble_enabled,
                payment_nfc_enabled,
                payment_approval_mode,
                settlement_auto_enabled,
                settlement_cycle_minutes,
                store_offline_enabled,
                store_ble_enabled,
                store_nfc_enabled,
                store_merchant_label,
                payment_completed_alert_enabled,
                incoming_request_alert_enabled,
                failed_alert_enabled,
                settlement_completed_alert_enabled,
                created_at,
                updated_at
            ) VALUES (
                #{user_id},
                #{security_level_high_enabled},
                #{face_id_setting_enabled},
                #{fingerprint_setting_enabled},
                #{payment_offline_enabled},
                #{payment_ble_enabled},
                #{payment_nfc_enabled},
                #{payment_approval_mode},
                #{settlement_auto_enabled},
                #{settlement_cycle_minutes},
                #{store_offline_enabled},
                #{store_ble_enabled},
                #{store_nfc_enabled},
                #{store_merchant_label},
                #{payment_completed_alert_enabled},
                #{incoming_request_alert_enabled},
                #{failed_alert_enabled},
                #{settlement_completed_alert_enabled},
                #{created_at},
                #{updated_at}
            )
            ON CONFLICT (user_id) DO UPDATE SET
                security_level_high_enabled = EXCLUDED.security_level_high_enabled,
                face_id_setting_enabled = EXCLUDED.face_id_setting_enabled,
                fingerprint_setting_enabled = EXCLUDED.fingerprint_setting_enabled,
                payment_offline_enabled = EXCLUDED.payment_offline_enabled,
                payment_ble_enabled = EXCLUDED.payment_ble_enabled,
                payment_nfc_enabled = EXCLUDED.payment_nfc_enabled,
                payment_approval_mode = EXCLUDED.payment_approval_mode,
                settlement_auto_enabled = EXCLUDED.settlement_auto_enabled,
                settlement_cycle_minutes = EXCLUDED.settlement_cycle_minutes,
                store_offline_enabled = EXCLUDED.store_offline_enabled,
                store_ble_enabled = EXCLUDED.store_ble_enabled,
                store_nfc_enabled = EXCLUDED.store_nfc_enabled,
                store_merchant_label = EXCLUDED.store_merchant_label,
                payment_completed_alert_enabled = EXCLUDED.payment_completed_alert_enabled,
                incoming_request_alert_enabled = EXCLUDED.incoming_request_alert_enabled,
                failed_alert_enabled = EXCLUDED.failed_alert_enabled,
                settlement_completed_alert_enabled = EXCLUDED.settlement_completed_alert_enabled,
                updated_at = EXCLUDED.updated_at
            RETURNING
                security_level_high_enabled,
                face_id_setting_enabled,
                fingerprint_setting_enabled,
                payment_offline_enabled,
                payment_ble_enabled,
                payment_nfc_enabled,
                payment_approval_mode,
                settlement_auto_enabled,
                settlement_cycle_minutes,
                store_offline_enabled,
                store_ble_enabled,
                store_nfc_enabled,
                store_merchant_label,
                payment_completed_alert_enabled,
                incoming_request_alert_enabled,
                failed_alert_enabled,
                settlement_completed_alert_enabled,
                updated_at
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("security_level_high_enabled", settings.getSecurityLevelHighEnabled());
        params.put("face_id_setting_enabled", settings.getFaceIdSettingEnabled());
        params.put("fingerprint_setting_enabled", settings.getFingerprintSettingEnabled());
        params.put("payment_offline_enabled", settings.getPaymentOfflineEnabled());
        params.put("payment_ble_enabled", settings.getPaymentBleEnabled());
        params.put("payment_nfc_enabled", settings.getPaymentNfcEnabled());
        params.put("payment_approval_mode", settings.getPaymentApprovalMode());
        params.put("settlement_auto_enabled", settings.getSettlementAutoEnabled());
        params.put("settlement_cycle_minutes", settings.getSettlementCycleMinutes());
        params.put("store_offline_enabled", settings.getStoreOfflineEnabled());
        params.put("store_ble_enabled", settings.getStoreBleEnabled());
        params.put("store_nfc_enabled", settings.getStoreNfcEnabled());
        params.put("store_merchant_label", settings.getStoreMerchantLabel());
        params.put("payment_completed_alert_enabled", settings.getPaymentCompletedAlertEnabled());
        params.put("incoming_request_alert_enabled", settings.getIncomingRequestAlertEnabled());
        params.put("failed_alert_enabled", settings.getFailedAlertEnabled());
        params.put("settlement_completed_alert_enabled", settings.getSettlementCompletedAlertEnabled());
        params.put("created_at", DateUtils.now());
        params.put("updated_at", DateUtils.now());

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> fetchOne(row -> OfflinePaySettingsDto.builder()
                .securityLevelHighEnabled(getBooleanColumnValue(row, "security_level_high_enabled"))
                .faceIdSettingEnabled(getBooleanColumnValue(row, "face_id_setting_enabled"))
                .fingerprintSettingEnabled(getBooleanColumnValue(row, "fingerprint_setting_enabled"))
                .paymentOfflineEnabled(getBooleanColumnValue(row, "payment_offline_enabled"))
                .paymentBleEnabled(getBooleanColumnValue(row, "payment_ble_enabled"))
                .paymentNfcEnabled(getBooleanColumnValue(row, "payment_nfc_enabled"))
                .paymentApprovalMode(getStringColumnValue(row, "payment_approval_mode"))
                .settlementAutoEnabled(getBooleanColumnValue(row, "settlement_auto_enabled"))
                .settlementCycleMinutes(getIntegerColumnValue(row, "settlement_cycle_minutes"))
                .storeOfflineEnabled(getBooleanColumnValue(row, "store_offline_enabled"))
                .storeBleEnabled(getBooleanColumnValue(row, "store_ble_enabled"))
                .storeNfcEnabled(getBooleanColumnValue(row, "store_nfc_enabled"))
                .storeMerchantLabel(getStringColumnValue(row, "store_merchant_label"))
                .paymentCompletedAlertEnabled(getBooleanColumnValue(row, "payment_completed_alert_enabled"))
                .incomingRequestAlertEnabled(getBooleanColumnValue(row, "incoming_request_alert_enabled"))
                .failedAlertEnabled(getBooleanColumnValue(row, "failed_alert_enabled"))
                .settlementCompletedAlertEnabled(getBooleanColumnValue(row, "settlement_completed_alert_enabled"))
                .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
                .build(), rows))
            .onFailure(throwable -> log.error("오프라인 페이 설정 저장 실패 - userId: {}", userId, throwable));
    }

    public Future<Void> createOfflinePaySharedDetail(SqlClient client, String token, Long userId, String itemId, JsonObject payload, LocalDateTime expiresAt) {
        String sql = """
            INSERT INTO offline_pay_shared_details (
                token,
                user_id,
                item_id,
                payload_json,
                expires_at,
                created_at,
                updated_at
            ) VALUES (
                #{token},
                #{user_id},
                #{item_id},
                CAST(#{payload_json} AS jsonb),
                #{expires_at},
                #{created_at},
                #{updated_at}
            )
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("token", token);
        params.put("user_id", userId);
        params.put("item_id", itemId);
        params.put("payload_json", payload.encode());
        params.put("expires_at", expiresAt);
        params.put("created_at", DateUtils.now());
        params.put("updated_at", DateUtils.now());

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> (Void) null)
            .onFailure(throwable -> log.error("오프라인 페이 공유 토큰 저장 실패 - userId: {}, itemId: {}", userId, itemId, throwable));
    }

    public Future<OfflinePaySharedDetailPublicDto> getOfflinePaySharedDetail(SqlClient client, String token) {
        String sql = """
            SELECT token, item_id, payload_json, expires_at
            FROM offline_pay_shared_details
            WHERE token = #{token}
              AND expires_at > NOW()
            """;
        return query(client, QueryBuilder.selectStringQuery(sql).build(), Collections.singletonMap("token", token))
            .map(rows -> fetchOne(row -> OfflinePaySharedDetailPublicDto.builder()
                .token(getStringColumnValue(row, "token"))
                .itemId(getStringColumnValue(row, "item_id"))
                .payload(getJsonObjectColumnValue(row, "payload_json"))
                .expiresAt(getLocalDateTimeColumnValue(row, "expires_at"))
                .build(), rows))
            .onFailure(throwable -> log.error("오프라인 페이 공유 토큰 조회 실패 - token: {}", token, throwable));
    }

    public Future<OfflinePayTrustCenterDto> getOfflinePayTrustCenter(SqlClient client, Long userId, int limit) {
        String snapshotSql = """
            SELECT
                platform,
                device_name,
                tee_available,
                key_signing_active,
                key_provider,
                hardware_backed_key,
                user_presence_protected,
                secure_hardware_level,
                attestation_class,
                attestation_verdict,
                server_verified_trust_level,
                device_registration_id,
                source_device_id,
                device_binding_key,
                app_version,
                collected_at,
                face_available,
                fingerprint_available,
                auth_binding_key,
                last_verified_auth_method,
                last_verified_at,
                last_synced_at,
                sync_status,
                updated_at
            FROM offline_pay_trust_center_snapshots
            WHERE user_id = #{user_id}
            """;
        String logsSql = """
            SELECT
                log_id,
                event_type,
                event_status,
                message,
                reason_code,
                metadata_json,
                created_at
            FROM offline_pay_proof_logs
            WHERE user_id = #{user_id}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """;
        String statusLogsSql = """
            SELECT
                log_id,
                event_type,
                event_status,
                message,
                reason_code,
                metadata_json,
                created_at
            FROM offline_pay_trust_center_status_logs
            WHERE user_id = #{user_id}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("limit", limit);

        Future<OfflinePayTrustCenterDto> snapshotFuture = query(client, QueryBuilder.selectStringQuery(snapshotSql).build(), Collections.singletonMap("user_id", userId))
            .map(rows -> fetchOne(row -> OfflinePayTrustCenterDto.builder()
                .platform(getStringColumnValue(row, "platform"))
                .deviceName(getStringColumnValue(row, "device_name"))
                .teeAvailable(getBooleanColumnValue(row, "tee_available"))
                .keySigningActive(getBooleanColumnValue(row, "key_signing_active"))
                .keyProvider(getStringColumnValue(row, "key_provider"))
                .hardwareBackedKey(getBooleanColumnValue(row, "hardware_backed_key"))
                .userPresenceProtected(getBooleanColumnValue(row, "user_presence_protected"))
                .secureHardwareLevel(getStringColumnValue(row, "secure_hardware_level"))
                .attestationClass(getStringColumnValue(row, "attestation_class"))
                .attestationVerdict(getStringColumnValue(row, "attestation_verdict"))
                .serverVerifiedTrustLevel(getStringColumnValue(row, "server_verified_trust_level"))
                .deviceRegistrationId(getStringColumnValue(row, "device_registration_id"))
                .sourceDeviceId(getStringColumnValue(row, "source_device_id"))
                .deviceBindingKey(getStringColumnValue(row, "device_binding_key"))
                .appVersion(getStringColumnValue(row, "app_version"))
                .collectedAt(getLocalDateTimeColumnValue(row, "collected_at"))
                .faceAvailable(getBooleanColumnValue(row, "face_available"))
                .fingerprintAvailable(getBooleanColumnValue(row, "fingerprint_available"))
                .authBindingKey(getStringColumnValue(row, "auth_binding_key"))
                .lastVerifiedAuthMethod(getStringColumnValue(row, "last_verified_auth_method"))
                .lastVerifiedAt(getLocalDateTimeColumnValue(row, "last_verified_at"))
                .lastSyncedAt(getLocalDateTimeColumnValue(row, "last_synced_at"))
                .syncStatus(getStringColumnValue(row, "sync_status"))
                .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
                .build(), rows));

        Future<List<OfflinePayTrustCenterLogDto>> logsFuture = query(client, QueryBuilder.selectStringQuery(logsSql).build(), params)
            .map(rows -> fetchAll(row -> OfflinePayTrustCenterLogDto.builder()
                .id(getStringColumnValue(row, "log_id"))
                .eventType(getStringColumnValue(row, "event_type"))
                .eventStatus(getStringColumnValue(row, "event_status"))
                .message(getStringColumnValue(row, "message"))
                .reasonCode(getStringColumnValue(row, "reason_code"))
                .metadata(getJsonObjectColumnValue(row, "metadata_json"))
                .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
                .build(), rows));
        Future<List<OfflinePayTrustCenterLogDto>> statusLogsFuture = query(client, QueryBuilder.selectStringQuery(statusLogsSql).build(), params)
            .map(rows -> fetchAll(row -> OfflinePayTrustCenterLogDto.builder()
                .id(getStringColumnValue(row, "log_id"))
                .eventType(getStringColumnValue(row, "event_type"))
                .eventStatus(getStringColumnValue(row, "event_status"))
                .message(getStringColumnValue(row, "message"))
                .reasonCode(getStringColumnValue(row, "reason_code"))
                .metadata(getJsonObjectColumnValue(row, "metadata_json"))
                .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
                .build(), rows));

        return snapshotFuture.compose(snapshot ->
            Future.all(logsFuture, statusLogsFuture).map(results -> {
                List<OfflinePayTrustCenterLogDto> logs = results.resultAt(0);
                List<OfflinePayTrustCenterLogDto> statusLogs = results.resultAt(1);
                return OfflinePayTrustCenterDto.builder()
                    .platform(snapshot != null ? snapshot.getPlatform() : null)
                    .deviceName(snapshot != null ? snapshot.getDeviceName() : null)
                    .teeAvailable(snapshot != null ? snapshot.getTeeAvailable() : null)
                    .keySigningActive(snapshot != null ? snapshot.getKeySigningActive() : null)
                    .keyProvider(snapshot != null ? snapshot.getKeyProvider() : null)
                    .hardwareBackedKey(snapshot != null ? snapshot.getHardwareBackedKey() : null)
                    .userPresenceProtected(snapshot != null ? snapshot.getUserPresenceProtected() : null)
                    .secureHardwareLevel(snapshot != null ? snapshot.getSecureHardwareLevel() : null)
                    .attestationClass(snapshot != null ? snapshot.getAttestationClass() : null)
                    .attestationVerdict(snapshot != null ? snapshot.getAttestationVerdict() : null)
                    .serverVerifiedTrustLevel(snapshot != null ? snapshot.getServerVerifiedTrustLevel() : null)
                    .deviceRegistrationId(snapshot != null ? snapshot.getDeviceRegistrationId() : null)
                    .sourceDeviceId(snapshot != null ? snapshot.getSourceDeviceId() : null)
                    .deviceBindingKey(snapshot != null ? snapshot.getDeviceBindingKey() : null)
                    .appVersion(snapshot != null ? snapshot.getAppVersion() : null)
                    .collectedAt(snapshot != null ? snapshot.getCollectedAt() : null)
                    .faceAvailable(snapshot != null ? snapshot.getFaceAvailable() : null)
                    .fingerprintAvailable(snapshot != null ? snapshot.getFingerprintAvailable() : null)
                    .authBindingKey(snapshot != null ? snapshot.getAuthBindingKey() : null)
                    .lastVerifiedAuthMethod(snapshot != null ? snapshot.getLastVerifiedAuthMethod() : null)
                    .lastVerifiedAt(snapshot != null ? snapshot.getLastVerifiedAt() : null)
                    .lastSyncedAt(snapshot != null ? snapshot.getLastSyncedAt() : null)
                    .syncStatus(snapshot != null ? snapshot.getSyncStatus() : null)
                    .updatedAt(snapshot != null ? snapshot.getUpdatedAt() : null)
                    .proofLogs(logs)
                    .statusLogs(statusLogs)
                    .trustContractMet(snapshot != null && "SERVER_VERIFIED".equals(snapshot.getServerVerifiedTrustLevel()))
                    .contractRequirements("HARDWARE_BACKED_VERIFIED")
                    .build();
            })
        ).onFailure(throwable -> log.error("오프라인 페이 보안 센터 조회 실패 - userId: {}", userId, throwable));
    }

    public Future<OfflinePayTrustCenterDto> upsertOfflinePayTrustCenter(
        SqlClient client,
        Long userId,
        OfflinePayTrustCenterDto trustCenter
    ) {
        String snapshotSql = """
            INSERT INTO offline_pay_trust_center_snapshots (
                user_id,
                platform,
                device_name,
                tee_available,
                key_signing_active,
                key_provider,
                hardware_backed_key,
                user_presence_protected,
                secure_hardware_level,
                attestation_class,
                attestation_verdict,
                server_verified_trust_level,
                device_registration_id,
                source_device_id,
                device_binding_key,
                app_version,
                collected_at,
                face_available,
                fingerprint_available,
                auth_binding_key,
                last_verified_auth_method,
                last_verified_at,
                last_synced_at,
                sync_status,
                created_at,
                updated_at
            ) VALUES (
                #{user_id},
                #{platform},
                #{device_name},
                #{tee_available},
                #{key_signing_active},
                #{key_provider},
                #{hardware_backed_key},
                #{user_presence_protected},
                #{secure_hardware_level},
                #{attestation_class},
                #{attestation_verdict},
                #{server_verified_trust_level},
                #{device_registration_id},
                #{source_device_id},
                #{device_binding_key},
                #{app_version},
                #{collected_at},
                #{face_available},
                #{fingerprint_available},
                #{auth_binding_key},
                #{last_verified_auth_method},
                #{last_verified_at},
                #{last_synced_at},
                #{sync_status},
                #{created_at},
                #{updated_at}
            )
            ON CONFLICT (user_id) DO UPDATE SET
                platform = EXCLUDED.platform,
                device_name = EXCLUDED.device_name,
                tee_available = EXCLUDED.tee_available,
                key_signing_active = EXCLUDED.key_signing_active,
                key_provider = EXCLUDED.key_provider,
                hardware_backed_key = EXCLUDED.hardware_backed_key,
                user_presence_protected = EXCLUDED.user_presence_protected,
                secure_hardware_level = EXCLUDED.secure_hardware_level,
                attestation_class = EXCLUDED.attestation_class,
                attestation_verdict = EXCLUDED.attestation_verdict,
                server_verified_trust_level = EXCLUDED.server_verified_trust_level,
                device_registration_id = EXCLUDED.device_registration_id,
                source_device_id = EXCLUDED.source_device_id,
                device_binding_key = EXCLUDED.device_binding_key,
                app_version = EXCLUDED.app_version,
                collected_at = EXCLUDED.collected_at,
                face_available = EXCLUDED.face_available,
                fingerprint_available = EXCLUDED.fingerprint_available,
                auth_binding_key = EXCLUDED.auth_binding_key,
                last_verified_auth_method = EXCLUDED.last_verified_auth_method,
                last_verified_at = EXCLUDED.last_verified_at,
                last_synced_at = EXCLUDED.last_synced_at,
                sync_status = EXCLUDED.sync_status,
                updated_at = EXCLUDED.updated_at
            """;

        Future<OfflinePayTrustCenterDto> currentFuture = getOfflinePayTrustCenter(client, userId, 10)
            .recover(throwable -> Future.succeededFuture(null));

        Map<String, Object> snapshotParams = new HashMap<>();
        snapshotParams.put("user_id", userId);
        snapshotParams.put("platform", trustCenter.getPlatform());
        snapshotParams.put("device_name", trustCenter.getDeviceName());
        snapshotParams.put("tee_available", trustCenter.getTeeAvailable());
        snapshotParams.put("key_signing_active", trustCenter.getKeySigningActive());
        snapshotParams.put("key_provider", trustCenter.getKeyProvider());
        snapshotParams.put("hardware_backed_key", trustCenter.getHardwareBackedKey());
        snapshotParams.put("user_presence_protected", trustCenter.getUserPresenceProtected());
        snapshotParams.put("secure_hardware_level", trustCenter.getSecureHardwareLevel());
        snapshotParams.put("attestation_class", trustCenter.getAttestationClass());
        snapshotParams.put("attestation_verdict", trustCenter.getAttestationVerdict());
        snapshotParams.put("server_verified_trust_level", trustCenter.getServerVerifiedTrustLevel());
        snapshotParams.put("device_registration_id", trustCenter.getDeviceRegistrationId());
        snapshotParams.put("source_device_id", trustCenter.getSourceDeviceId());
        snapshotParams.put("device_binding_key", trustCenter.getDeviceBindingKey());
        snapshotParams.put("app_version", trustCenter.getAppVersion());
        snapshotParams.put("collected_at", trustCenter.getCollectedAt());
        snapshotParams.put("face_available", trustCenter.getFaceAvailable());
        snapshotParams.put("fingerprint_available", trustCenter.getFingerprintAvailable());
        snapshotParams.put("auth_binding_key", trustCenter.getAuthBindingKey());
        snapshotParams.put("last_verified_auth_method", trustCenter.getLastVerifiedAuthMethod());
        snapshotParams.put("last_verified_at", trustCenter.getLastVerifiedAt());
        snapshotParams.put("last_synced_at", DateUtils.now());
        snapshotParams.put("sync_status", "SYNCED");
        snapshotParams.put("created_at", DateUtils.now());
        snapshotParams.put("updated_at", DateUtils.now());

        return currentFuture.compose(currentSnapshot -> {
            Future<Void> snapshotUpsert = query(client, QueryBuilder.selectStringQuery(snapshotSql).build(), snapshotParams)
                .map(rows -> (Void) null);

            Future<Void> proofLogsUpsert = Future.succeededFuture();
            if (trustCenter.getProofLogs() != null && !trustCenter.getProofLogs().isEmpty()) {
                for (OfflinePayTrustCenterLogDto logItem : trustCenter.getProofLogs()) {
                    proofLogsUpsert = proofLogsUpsert.compose(v -> upsertOfflinePayProofLog(client, userId, logItem));
                }
            }

            List<OfflinePayTrustCenterLogDto> generatedStatusLogs = buildTrustCenterStatusLogs(currentSnapshot, trustCenter);
            Future<Void> statusLogsUpsert = Future.succeededFuture();
            for (OfflinePayTrustCenterLogDto logItem : generatedStatusLogs) {
                statusLogsUpsert = statusLogsUpsert.compose(v -> upsertOfflinePayTrustCenterStatusLog(client, userId, logItem));
            }
            if (trustCenter.getStatusLogs() != null && !trustCenter.getStatusLogs().isEmpty()) {
                for (OfflinePayTrustCenterLogDto logItem : trustCenter.getStatusLogs()) {
                    statusLogsUpsert = statusLogsUpsert.compose(v -> upsertOfflinePayTrustCenterStatusLog(client, userId, logItem));
                }
            }
            final Future<Void> proofLogsUpsertFuture = proofLogsUpsert;
            final Future<Void> statusLogsUpsertFuture = statusLogsUpsert;

            return snapshotUpsert
                .compose(v -> proofLogsUpsertFuture)
                .compose(v -> statusLogsUpsertFuture)
                .compose(v -> getOfflinePayTrustCenter(client, userId, 10));
        })
            .onFailure(throwable -> log.error("오프라인 페이 보안 센터 저장 실패 - userId: {}", userId, throwable));
    }

    private List<OfflinePayTrustCenterLogDto> buildTrustCenterStatusLogs(
        OfflinePayTrustCenterDto currentSnapshot,
        OfflinePayTrustCenterDto nextSnapshot
    ) {
        List<OfflinePayTrustCenterLogDto> logs = new ArrayList<>();
        LocalDateTime now = DateUtils.now();
        logs.add(OfflinePayTrustCenterLogDto.builder()
            .id(UUID.randomUUID().toString())
            .eventType("TRUST_CENTER_SYNC")
            .eventStatus("SYNCED")
            .message("보안 센터 상태가 서버와 동기화되었습니다.")
            .reasonCode("SYNC_COMPLETED")
            .metadata(new JsonObject()
                .put("platform", nextSnapshot.getPlatform())
                .put("keyProvider", nextSnapshot.getKeyProvider())
                .put("hardwareBackedKey", nextSnapshot.getHardwareBackedKey())
                .put("userPresenceProtected", nextSnapshot.getUserPresenceProtected())
                .put("secureHardwareLevel", nextSnapshot.getSecureHardwareLevel())
                .put("attestationClass", nextSnapshot.getAttestationClass())
                .put("attestationVerdict", nextSnapshot.getAttestationVerdict())
                .put("serverVerifiedTrustLevel", nextSnapshot.getServerVerifiedTrustLevel())
                .put("deviceRegistrationId", nextSnapshot.getDeviceRegistrationId())
                .put("sourceDeviceId", nextSnapshot.getSourceDeviceId())
                .put("deviceBindingKey", nextSnapshot.getDeviceBindingKey())
                .put("appVersion", nextSnapshot.getAppVersion())
                .put("collectedAt", nextSnapshot.getCollectedAt() != null ? nextSnapshot.getCollectedAt().toString() : null))
            .createdAt(now)
            .build());

        appendTrustCenterChangeLog(logs, "TEE_STATUS", "teeAvailable",
            currentSnapshot != null ? currentSnapshot.getTeeAvailable() : null, nextSnapshot.getTeeAvailable(), now);
        appendTrustCenterChangeLog(logs, "KEY_SIGNING_STATUS", "keySigningActive",
            currentSnapshot != null ? currentSnapshot.getKeySigningActive() : null, nextSnapshot.getKeySigningActive(), now);
        appendTrustCenterChangeLog(logs, "FACE_CAPABILITY", "faceAvailable",
            currentSnapshot != null ? currentSnapshot.getFaceAvailable() : null, nextSnapshot.getFaceAvailable(), now);
        appendTrustCenterChangeLog(logs, "FINGERPRINT_CAPABILITY", "fingerprintAvailable",
            currentSnapshot != null ? currentSnapshot.getFingerprintAvailable() : null, nextSnapshot.getFingerprintAvailable(), now);
        appendTrustCenterChangeLog(logs, "AUTH_METHOD", "lastVerifiedAuthMethod",
            currentSnapshot != null ? currentSnapshot.getLastVerifiedAuthMethod() : null, nextSnapshot.getLastVerifiedAuthMethod(), now);
        appendTrustCenterChangeLog(logs, "DEVICE_BINDING_KEY", "deviceBindingKey",
            currentSnapshot != null ? currentSnapshot.getDeviceBindingKey() : null, nextSnapshot.getDeviceBindingKey(), now);
        appendTrustCenterChangeLog(logs, "APP_VERSION", "appVersion",
            currentSnapshot != null ? currentSnapshot.getAppVersion() : null, nextSnapshot.getAppVersion(), now);
        appendTrustCenterChangeLog(logs, "ATTESTATION_VERDICT", "attestationVerdict",
            currentSnapshot != null ? currentSnapshot.getAttestationVerdict() : null, nextSnapshot.getAttestationVerdict(), now);
        appendTrustCenterChangeLog(logs, "TRUST_LEVEL", "serverVerifiedTrustLevel",
            currentSnapshot != null ? currentSnapshot.getServerVerifiedTrustLevel() : null, nextSnapshot.getServerVerifiedTrustLevel(), now);

        if (!Objects.equals(currentSnapshot != null ? currentSnapshot.getDeviceRegistrationId() : null, nextSnapshot.getDeviceRegistrationId())) {
            logs.add(OfflinePayTrustCenterLogDto.builder()
                .id(UUID.randomUUID().toString())
                .eventType("DEVICE_REGISTRATION")
                .eventStatus(nextSnapshot.getDeviceRegistrationId() == null || nextSnapshot.getDeviceRegistrationId().isBlank() ? "DETACHED" : "ACTIVE")
                .message(nextSnapshot.getDeviceRegistrationId() == null || nextSnapshot.getDeviceRegistrationId().isBlank()
                    ? "보안 디바이스 등록 연결이 해제되었습니다."
                    : "보안 디바이스 등록 상태가 갱신되었습니다.")
                .reasonCode("DEVICE_REGISTRATION_CHANGED")
                .metadata(new JsonObject()
                    .put("before", currentSnapshot != null ? currentSnapshot.getDeviceRegistrationId() : null)
                    .put("after", nextSnapshot.getDeviceRegistrationId()))
                .createdAt(now)
                .build());
        }

        if (!Objects.equals(currentSnapshot != null ? currentSnapshot.getSourceDeviceId() : null, nextSnapshot.getSourceDeviceId())) {
            logs.add(OfflinePayTrustCenterLogDto.builder()
                .id(UUID.randomUUID().toString())
                .eventType("SOURCE_DEVICE")
                .eventStatus("UPDATED")
                .message("보안 센터 상태를 수집한 기기 식별값이 갱신되었습니다.")
                .reasonCode("SOURCE_DEVICE_CHANGED")
                .metadata(new JsonObject()
                    .put("before", currentSnapshot != null ? currentSnapshot.getSourceDeviceId() : null)
                    .put("after", nextSnapshot.getSourceDeviceId()))
                .createdAt(now)
                .build());
        }

        return logs;
    }

    private void appendTrustCenterChangeLog(
        List<OfflinePayTrustCenterLogDto> logs,
        String eventType,
        String field,
        Object before,
        Object after,
        LocalDateTime createdAt
    ) {
        if (Objects.equals(before, after)) {
            return;
        }
        logs.add(OfflinePayTrustCenterLogDto.builder()
            .id(UUID.randomUUID().toString())
            .eventType(eventType)
            .eventStatus("UPDATED")
            .message(field + " 상태가 변경되었습니다.")
            .reasonCode("STATE_CHANGED")
            .metadata(new JsonObject()
                .put("field", field)
                .put("before", before)
                .put("after", after))
            .createdAt(createdAt)
            .build());
    }

    private Future<Void> upsertOfflinePayProofLog(SqlClient client, Long userId, OfflinePayTrustCenterLogDto logItem) {
        String sql = """
            INSERT INTO offline_pay_proof_logs (
                user_id,
                log_id,
                event_type,
                event_status,
                message,
                reason_code,
                metadata_json,
                created_at,
                updated_at
            ) VALUES (
                #{user_id},
                #{log_id},
                #{event_type},
                #{event_status},
                #{message},
                #{reason_code},
                CAST(#{metadata_json} AS jsonb),
                #{created_at},
                #{updated_at}
            )
            ON CONFLICT (user_id, log_id) DO UPDATE SET
                event_type = EXCLUDED.event_type,
                event_status = EXCLUDED.event_status,
                message = EXCLUDED.message,
                reason_code = EXCLUDED.reason_code,
                metadata_json = EXCLUDED.metadata_json,
                updated_at = EXCLUDED.updated_at
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("log_id", logItem.getId());
        params.put("event_type", logItem.getEventType());
        params.put("event_status", logItem.getEventStatus());
        params.put("message", logItem.getMessage());
        params.put("reason_code", logItem.getReasonCode());
        params.put("metadata_json", logItem.getMetadata() != null ? logItem.getMetadata().encode() : "{}");
        params.put("created_at", logItem.getCreatedAt() != null ? logItem.getCreatedAt() : DateUtils.now());
        params.put("updated_at", DateUtils.now());

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> (Void) null);
    }

    private Future<Void> upsertOfflinePayTrustCenterStatusLog(SqlClient client, Long userId, OfflinePayTrustCenterLogDto logItem) {
        String sql = """
            INSERT INTO offline_pay_trust_center_status_logs (
                user_id,
                log_id,
                event_type,
                event_status,
                message,
                reason_code,
                metadata_json,
                created_at,
                updated_at
            ) VALUES (
                #{user_id},
                #{log_id},
                #{event_type},
                #{event_status},
                #{message},
                #{reason_code},
                CAST(#{metadata_json} AS jsonb),
                #{created_at},
                #{updated_at}
            )
            ON CONFLICT (user_id, log_id) DO UPDATE SET
                event_type = EXCLUDED.event_type,
                event_status = EXCLUDED.event_status,
                message = EXCLUDED.message,
                reason_code = EXCLUDED.reason_code,
                metadata_json = EXCLUDED.metadata_json,
                created_at = EXCLUDED.created_at,
                updated_at = EXCLUDED.updated_at
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("log_id", logItem.getId());
        params.put("event_type", logItem.getEventType());
        params.put("event_status", logItem.getEventStatus());
        params.put("message", logItem.getMessage());
        params.put("reason_code", logItem.getReasonCode());
        params.put("metadata_json", (logItem.getMetadata() != null ? logItem.getMetadata() : new JsonObject()).encode());
        params.put("created_at", logItem.getCreatedAt() != null ? logItem.getCreatedAt() : DateUtils.now());
        params.put("updated_at", DateUtils.now());

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> (Void) null);
    }

    public Future<OfflinePayNotificationCenterDto> getOfflinePayNotificationCenter(SqlClient client, Long userId, int limit) {
        String sql = """
            SELECT
                log_id,
                notification_type,
                delivery_status,
                title,
                message,
                reason_code,
                metadata_json,
                created_at
            FROM offline_pay_notification_logs
            WHERE user_id = #{user_id}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("limit", limit);

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> {
                List<OfflinePayActivityLogDto> logs = fetchAll(row -> {
                    String notifType = getStringColumnValue(row, "notification_type");
                    return OfflinePayActivityLogDto.builder()
                        .id(getStringColumnValue(row, "log_id"))
                        .category(notifType)
                        .eventStatus(getStringColumnValue(row, "delivery_status"))
                        .title(getStringColumnValue(row, "title"))
                        .message(getStringColumnValue(row, "message"))
                        .reasonCode(getStringColumnValue(row, "reason_code"))
                        .metadata(getJsonObjectColumnValue(row, "metadata_json"))
                        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
                        .audience("USER")
                        .logSource(resolveNotificationLogSource(notifType))
                        .build();
                }, rows);
                LocalDateTime updatedAt = logs.isEmpty() ? LocalDateTime.now() : logs.get(0).getCreatedAt();
                return OfflinePayNotificationCenterDto.builder()
                    .updatedAt(updatedAt)
                    .logs(logs)
                    .build();
            })
            .onFailure(throwable -> log.error("오프라인 페이 알림 센터 조회 실패 - userId: {}", userId, throwable));
    }

    public Future<OfflinePayNotificationCenterDto> upsertOfflinePayNotificationCenter(SqlClient client, Long userId, List<OfflinePayActivityLogDto> logs) {
        Future<Void> chain = Future.succeededFuture();
        for (OfflinePayActivityLogDto item : logs) {
            chain = chain.compose(v -> upsertOfflinePayNotificationLog(client, userId, item));
        }
        return chain.compose(v -> getOfflinePayNotificationCenter(client, userId, 20))
            .onFailure(throwable -> log.error("오프라인 페이 알림 센터 저장 실패 - userId: {}", userId, throwable));
    }

    private Future<Void> upsertOfflinePayNotificationLog(SqlClient client, Long userId, OfflinePayActivityLogDto item) {
        String sql = """
            INSERT INTO offline_pay_notification_logs (
                user_id,
                log_id,
                notification_type,
                delivery_status,
                title,
                message,
                reason_code,
                metadata_json,
                created_at,
                updated_at
            ) VALUES (
                #{user_id},
                #{log_id},
                #{notification_type},
                #{delivery_status},
                #{title},
                #{message},
                #{reason_code},
                CAST(#{metadata_json} AS jsonb),
                #{created_at},
                #{updated_at}
            )
            ON CONFLICT (user_id, log_id) DO UPDATE SET
                notification_type = EXCLUDED.notification_type,
                delivery_status = EXCLUDED.delivery_status,
                title = EXCLUDED.title,
                message = EXCLUDED.message,
                reason_code = EXCLUDED.reason_code,
                metadata_json = EXCLUDED.metadata_json,
                updated_at = EXCLUDED.updated_at
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("log_id", item.getId());
        params.put("notification_type", item.getCategory());
        params.put("delivery_status", item.getEventStatus());
        params.put("title", item.getTitle());
        params.put("message", item.getMessage());
        params.put("reason_code", item.getReasonCode());
        params.put("metadata_json", item.getMetadata() != null ? item.getMetadata().encode() : "{}");
        params.put("created_at", item.getCreatedAt() != null ? item.getCreatedAt() : DateUtils.now());
        params.put("updated_at", DateUtils.now());
        return query(client, QueryBuilder.selectStringQuery(sql).build(), params).map(rows -> (Void) null);
    }

    public Future<OfflinePaySettlementCenterDto> getOfflinePaySettlementCenter(SqlClient client, Long userId, int limit) {
        String sql = """
            SELECT
                log_id,
                settlement_status,
                title,
                message,
                reason_code,
                request_id,
                settlement_id,
                metadata_json,
                created_at
            FROM offline_pay_settlement_logs
            WHERE user_id = #{user_id}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("limit", limit);

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> {
                List<OfflinePayActivityLogDto> logs = fetchAll(row -> {
                    String status = getStringColumnValue(row, "settlement_status");
                    String audience = resolveSettlementLogAudience(status);
                    return OfflinePayActivityLogDto.builder()
                        .id(getStringColumnValue(row, "log_id"))
                        .category("SETTLEMENT")
                        .eventStatus(status)
                        .title(getStringColumnValue(row, "title"))
                        .message(getStringColumnValue(row, "message"))
                        .reasonCode(getStringColumnValue(row, "reason_code"))
                        .requestId(getStringColumnValue(row, "request_id"))
                        .settlementId(getStringColumnValue(row, "settlement_id"))
                        .metadata(getJsonObjectColumnValue(row, "metadata_json"))
                        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
                        .audience(audience)
                        .logSource("SETTLEMENT_FLOW")
                        .build();
                }, rows);
                LocalDateTime updatedAt = logs.isEmpty() ? LocalDateTime.now() : logs.get(0).getCreatedAt();
                return OfflinePaySettlementCenterDto.builder()
                    .updatedAt(updatedAt)
                    .logs(logs)
                    .build();
            })
            .onFailure(throwable -> log.error("오프라인 페이 정산 센터 조회 실패 - userId: {}", userId, throwable));
    }

    public Future<OfflinePaySettlementCenterDto> upsertOfflinePaySettlementCenter(SqlClient client, Long userId, List<OfflinePayActivityLogDto> logs) {
        Future<Void> chain = Future.succeededFuture();
        for (OfflinePayActivityLogDto item : logs) {
            chain = chain.compose(v -> upsertOfflinePaySettlementLog(client, userId, item));
        }
        return chain.compose(v -> getOfflinePaySettlementCenter(client, userId, 20))
            .onFailure(throwable -> log.error("오프라인 페이 정산 센터 저장 실패 - userId: {}", userId, throwable));
    }

    private Future<Void> upsertOfflinePaySettlementLog(SqlClient client, Long userId, OfflinePayActivityLogDto item) {
        String sql = """
            INSERT INTO offline_pay_settlement_logs (
                user_id,
                log_id,
                settlement_status,
                title,
                message,
                reason_code,
                request_id,
                settlement_id,
                metadata_json,
                created_at,
                updated_at
            ) VALUES (
                #{user_id},
                #{log_id},
                #{settlement_status},
                #{title},
                #{message},
                #{reason_code},
                #{request_id},
                #{settlement_id},
                CAST(#{metadata_json} AS jsonb),
                #{created_at},
                #{updated_at}
            )
            ON CONFLICT (user_id, log_id) DO UPDATE SET
                settlement_status = EXCLUDED.settlement_status,
                title = EXCLUDED.title,
                message = EXCLUDED.message,
                reason_code = EXCLUDED.reason_code,
                request_id = EXCLUDED.request_id,
                settlement_id = EXCLUDED.settlement_id,
                metadata_json = EXCLUDED.metadata_json,
                updated_at = EXCLUDED.updated_at
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("log_id", item.getId());
        params.put("settlement_status", item.getEventStatus());
        params.put("title", item.getTitle());
        params.put("message", item.getMessage());
        params.put("reason_code", item.getReasonCode());
        params.put("request_id", item.getRequestId());
        params.put("settlement_id", item.getSettlementId());
        params.put("metadata_json", item.getMetadata() != null ? item.getMetadata().encode() : "{}");
        params.put("created_at", item.getCreatedAt() != null ? item.getCreatedAt() : DateUtils.now());
        params.put("updated_at", DateUtils.now());
        return query(client, QueryBuilder.selectStringQuery(sql).build(), params).map(rows -> (Void) null);
    }

    private static String resolveSettlementLogAudience(String eventStatus) {
        if (eventStatus == null) {
            return "USER";
        }
        return switch (eventStatus.toUpperCase()) {
            case "COMPLETED" -> "BOTH";
            case "FAILED" -> "BOTH";
            case "PENDING" -> "USER";
            default -> "USER";
        };
    }

    private static String resolveNotificationLogSource(String notificationType) {
        if (notificationType == null) {
            return "SYSTEM";
        }
        String upper = notificationType.toUpperCase();
        if (upper.contains("SETTLEMENT") || upper.contains("SAGA")) {
            return "SETTLEMENT_FLOW";
        }
        if (upper.contains("COLLATERAL")) {
            return "COLLATERAL_FLOW";
        }
        if (upper.contains("TRUST") || upper.contains("SECURITY")) {
            return "TRUST_FLOW";
        }
        if (upper.contains("RECONCILIATION")) {
            return "RECONCILIATION_FLOW";
        }
        return "SYSTEM";
    }
}
