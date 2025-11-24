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
import java.util.List;

@Slf4j
public class WalletRepository extends BaseRepository {
    
    private final RowMapper<Wallet> walletMapper = row -> Wallet.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .address(getStringColumnValue(row, "address"))
        .balance(getBigDecimalColumnValue(row, "balance"))
        .lockedBalance(getBigDecimalColumnValue(row, "locked_balance"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    public Future<List<Wallet>> getWalletsByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("user_id", Op.Equal, "userId")
            .build();
        
        return query(client, sql, Collections.singletonMap("userId", userId))
            .map(rows -> fetchAll(walletMapper, rows));
    }
}

