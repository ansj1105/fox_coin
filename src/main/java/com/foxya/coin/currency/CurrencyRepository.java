package com.foxya.coin.currency;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CurrencyRepository extends BaseRepository {
    
    private final RowMapper<Currency> currencyMapper = row -> Currency.builder()
        .id(getIntegerColumnValue(row, "id"))
        .code(getStringColumnValue(row, "code"))
        .name(getStringColumnValue(row, "name"))
        .chain(getStringColumnValue(row, "chain"))
        .isActive(getBooleanColumnValue(row, "is_active"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    /**
     * 모든 활성 통화 조회
     */
    public Future<List<Currency>> getAllActiveCurrencies(SqlClient client) {
        String sql = QueryBuilder
            .select("currency")
            .where("is_active", Op.Equal, "is_active")
            .build();
        
        return query(client, sql, Collections.singletonMap("is_active", true))
            .map(rows -> fetchAll(currencyMapper, rows));
    }
    
    /**
     * 통화 코드로 조회
     */
    public Future<Currency> getCurrencyByCode(SqlClient client, String code) {
        String sql = QueryBuilder
            .select("currency")
            .where("code", Op.Equal, "code")
            .andWhere("is_active", Op.Equal, "is_active")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("code", code);
        params.put("is_active", true);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(currencyMapper, rows))
            .onFailure(e -> log.error("통화 조회 실패 - code: {}", code));
    }
    
    /**
     * 통화 코드와 체인으로 조회
     */
    public Future<Currency> getCurrencyByCodeAndChain(SqlClient client, String code, String chain) {
        String sql = QueryBuilder
            .select("currency")
            .where("code", Op.Equal, "code")
            .andWhere("chain", Op.Equal, "chain")
            .andWhere("is_active", Op.Equal, "is_active")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("code", code);
        params.put("chain", chain);
        params.put("is_active", true);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(currencyMapper, rows))
            .onFailure(e -> log.error("통화 조회 실패 - code: {}, chain: {}", code, chain));
    }
    
    /**
     * 통화 ID로 조회
     */
    public Future<Currency> getCurrencyById(SqlClient client, Integer id) {
        String sql = QueryBuilder
            .select("currency")
            .whereById()
            .build();
        
        return query(client, sql, Collections.singletonMap("id", id))
            .map(rows -> fetchOne(currencyMapper, rows))
            .onFailure(e -> log.error("통화 조회 실패 - id: {}", id));
    }
}

