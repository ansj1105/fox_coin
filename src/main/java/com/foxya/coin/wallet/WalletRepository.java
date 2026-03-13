package com.foxya.coin.wallet;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.deposit.dto.DepositWatchAddressDto;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.wallet.entities.Wallet;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WalletRepository extends BaseRepository {
    
    private final RowMapper<Wallet> walletMapper = row -> Wallet.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .currencyCode(getStringColumnValue(row, "currency_code"))
        .currencyName(getStringColumnValue(row, "currency_name"))
        .currencySymbol(getStringColumnValue(row, "currency_symbol"))
        .network(getStringColumnValue(row, "network"))
        .address(getStringColumnValue(row, "address"))
        .privateKey(getStringColumnValue(row, "private_key"))
        .balance(getBigDecimalColumnValue(row, "balance"))
        .lockedBalance(getBigDecimalColumnValue(row, "locked_balance"))
        .verified(getBooleanColumnValue(row, "verified"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    public Future<List<Wallet>> getWalletsByUserId(SqlClient client, Long userId) {
        String sql = """
            SELECT uw.*,
                   c.code  AS currency_code,
                   c.name  AS currency_name,
                   c.code  AS currency_symbol,
                   c.chain AS network
            FROM user_wallets uw
            LEFT JOIN currency c ON uw.currency_id = c.id
            WHERE uw.user_id = #{userId}
              AND uw.deleted_at IS NULL
            """;

        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> fetchAll(walletMapper, rows));
    }
    
    /**
     * 삭제되지 않은 지갑 조회 (not_deleted 전용)
     */
    public Future<List<Wallet>> getWalletsByUserIdNotDeleted(SqlClient client, Long userId) {
        return getWalletsByUserId(client, userId);
    }
    
    /**
     * 사용자의 모든 지갑 Soft Delete
     */
    public Future<Void> softDeleteWalletsByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("user_wallets", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "userId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("deleted_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("User wallets soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("지갑 Soft Delete 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 사용자와 통화로 지갑 존재 여부 확인
     */
    public Future<Boolean> existsByUserIdAndCurrencyId(SqlClient client, Long userId, Integer currencyId) {
        String sql = QueryBuilder
            .count("user_wallets")
            .where("user_id", Op.Equal, "userId")
            .andWhere("currency_id", Op.Equal, "currencyId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("currencyId", currencyId);
        
        return query(client, sql, params)
            .map(rows -> {
                Integer count = fetchOne(COUNT_MAPPER, rows);
                return count != null && count > 0;
            });
    }

    /**
     * 지갑 주소로 지갑 조회 (대소문자 무시)
     */
    public Future<Wallet> getWalletByAddressIgnoreCase(SqlClient client, String address) {
        String sql = """
            SELECT uw.*,
                   c.code  AS currency_code,
                   c.name  AS currency_name,
                   c.code  AS currency_symbol,
                   c.chain AS network
            FROM user_wallets uw
            LEFT JOIN currency c ON uw.currency_id = c.id
            WHERE LOWER(uw.address) = LOWER(#{address})
              AND uw.status = #{status}
              AND uw.deleted_at IS NULL
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("address", address);
        params.put("status", "ACTIVE");

        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows));
    }
    
    /**
     * 지갑 생성
     */
    public Future<Wallet> createWallet(SqlClient client, Long userId, Integer currencyId, String address, String privateKey) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("currency_id", currencyId);
        params.put("address", address);
        params.put("private_key", privateKey);
        params.put("balance", java.math.BigDecimal.ZERO);
        params.put("locked_balance", java.math.BigDecimal.ZERO);
        params.put("verified", false);
        params.put("status", "ACTIVE");
        
        String sql = QueryBuilder.insert("user_wallets", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .recover(throwable -> {
                // 중복 키 오류 처리
                if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                    log.warn("지갑이 이미 존재함 - userId: {}, currencyId: {}", userId, currencyId);
                    // 기존 지갑 조회하여 반환
                    return getWalletByUserIdAndCurrencyId(client, userId, currencyId)
                        .compose(existingWallet -> {
                            if (existingWallet != null) {
                                return Future.succeededFuture(existingWallet);
                            }
                            return Future.failedFuture(throwable);
                        });
                }
                log.error("지갑 생성 실패 - userId: {}, currencyId: {}", userId, currencyId, throwable);
                return Future.failedFuture(throwable);
            });
    }

    public Future<Wallet> createWallet(SqlClient client, Long userId, Integer currencyId, String address) {
        return createWallet(client, userId, currencyId, address, null);
    }

    /**
     * 지갑 개인키 업데이트
     */
    public Future<Void> updatePrivateKeyById(SqlClient client, Long walletId, String encryptedPrivateKey) {
        String sql = QueryBuilder
            .update("user_wallets", "private_key", "updated_at")
            .where("id", Op.Equal, "walletId")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("walletId", walletId);
        params.put("private_key", encryptedPrivateKey);
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());

        return query(client, sql, params)
            .<Void>map(rows -> null)
            .onFailure(throwable -> log.error("지갑 개인키 업데이트 실패 - walletId: {}", walletId, throwable));
    }

    public Future<Void> updateVerifiedById(SqlClient client, Long walletId, boolean verified) {
        String sql = QueryBuilder
            .update("user_wallets", "verified", "updated_at")
            .where("id", Op.Equal, "walletId")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("walletId", walletId);
        params.put("verified", verified);
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());

        return query(client, sql, params)
            .<Void>map(rows -> null)
            .onFailure(throwable -> log.error("지갑 verified 업데이트 실패 - walletId: {}", walletId, throwable));
    }

    public Future<Wallet> updateWalletAddressById(SqlClient client, Long walletId, String address) {
        String sql = QueryBuilder
            .update("user_wallets", "address", "updated_at")
            .where("id", Op.Equal, "walletId")
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("walletId", walletId);
        params.put("address", address);
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(throwable -> log.error("지갑 주소 업데이트 실패 - walletId: {}", walletId, throwable));
    }
    
    /**
     * 사용자와 통화로 지갑 조회
     */
    public Future<Wallet> getWalletByUserIdAndCurrencyId(SqlClient client, Long userId, Integer currencyId) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("user_id", Op.Equal, "userId")
            .andWhere("currency_id", Op.Equal, "currencyId")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("currencyId", currencyId);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(throwable -> log.error("지갑 조회 실패 - userId: {}, currencyId: {}", userId, currencyId, throwable));
    }
    
    /**
     * 삭제되지 않은 지갑 조회 (not_deleted 전용)
     */
    public Future<Wallet> getWalletByUserIdAndCurrencyIdNotDeleted(SqlClient client, Long userId, Integer currencyId) {
        return getWalletByUserIdAndCurrencyId(client, userId, currencyId);
    }

    /**
     * 입금 감시용 지갑 주소 목록 (외부 체인만, address가 있는 지갑).
     * 스캐너(coin_publish 등)가 주기적으로 이 목록을 조회해 온체인 입금 tx를 확인한다.
     */
    public Future<List<DepositWatchAddressDto>> getDepositWatchAddresses(SqlClient client) {
        String sql = """
            SELECT uw.user_id AS userId,
                   uw.currency_id AS currencyId,
                   c.code AS currencyCode,
                   uw.address AS address,
                   c.chain AS network
            FROM user_wallets uw
            JOIN currency c ON uw.currency_id = c.id
            WHERE uw.address IS NOT NULL AND uw.deleted_at IS NULL
              AND (c.chain IS NULL OR c.chain <> 'INTERNAL')
            """;
        return query(client, sql, Collections.emptyMap())
            .map(rows -> {
                List<DepositWatchAddressDto> list = new java.util.ArrayList<>();
                for (io.vertx.sqlclient.Row row : rows) {
                    list.add(DepositWatchAddressDto.builder()
                        .userId(getLongColumnValue(row, "userid"))
                        .currencyId(getIntegerColumnValue(row, "currencyid"))
                        .currencyCode(getStringColumnValue(row, "currencycode"))
                        .address(getStringColumnValue(row, "address"))
                        .network(getStringColumnValue(row, "network"))
                        .build());
                }
                return list;
            });
    }
}
