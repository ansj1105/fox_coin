package com.foxya.coin.currency;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.utils.QueryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CurrencyRepository extends BaseRepository {
    
    private final RowMapper<Currency> currencyMapper = row -> Currency.builder()
        .id(getIntegerColumnValue(row, "id"))
        .name(getStringColumnValue(row, "name"))
        .symbol(getStringColumnValue(row, "symbol"))
        .chain(getStringColumnValue(row, "chain"))
        .contractAddress(getStringColumnValue(row, "contract_address"))
        .decimals(getIntegerColumnValue(row, "decimals"))
        .status(getStringColumnValue(row, "status"))
        .withdrawFee(getBigDecimalColumnValue(row, "withdraw_fee"))
        .minWithdraw(getBigDecimalColumnValue(row, "min_withdraw"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    public Future<List<Currency>> getAllActiveCurrencies(SqlClient client) {
        String sql = QueryBuilder
            .select("currencies")
            .where("status", com.foxya.coin.utils.BaseQueryBuilder.Op.Equal, "status")
            .build();
        
        return query(client, sql, java.util.Collections.singletonMap("status", "ACTIVE"))
            .map(rows -> fetchAll(currencyMapper, rows));
    }
}

