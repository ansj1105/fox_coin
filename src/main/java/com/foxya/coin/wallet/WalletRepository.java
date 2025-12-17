package com.foxya.coin.wallet;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
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
        .balance(getBigDecimalColumnValue(row, "balance"))
        .lockedBalance(getBigDecimalColumnValue(row, "locked_balance"))
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
            """;

        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> fetchAll(walletMapper, rows));
    }
    
    /**
     * 사용자와 통화로 지갑 존재 여부 확인
     */
    public Future<Boolean> existsByUserIdAndCurrencyId(SqlClient client, Long userId, Integer currencyId) {
        String sql = QueryBuilder
            .count("user_wallets")
            .where("user_id", Op.Equal, "userId")
            .andWhere("currency_id", Op.Equal, "currencyId")
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
     * 지갑 생성
     */
    public Future<Wallet> createWallet(SqlClient client, Long userId, Integer currencyId, String address) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("currency_id", currencyId);
        params.put("address", address);
        params.put("balance", java.math.BigDecimal.ZERO);
        params.put("locked_balance", java.math.BigDecimal.ZERO);
        params.put("status", "ACTIVE");
        
        String sql = QueryBuilder.insert("user_wallets", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(throwable -> log.error("지갑 생성 실패 - userId: {}, currencyId: {}", userId, currencyId, throwable));
    }
}
