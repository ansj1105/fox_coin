package com.foxya.coin.exchange;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.exchange.entities.Exchange;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ExchangeRepository extends BaseRepository {
    
    private final RowMapper<Exchange> exchangeMapper = row -> Exchange.builder()
        .id(getLongColumnValue(row, "id"))
        .exchangeId(getStringColumnValue(row, "exchange_id"))
        .userId(getLongColumnValue(row, "user_id"))
        .orderNumber(getStringColumnValue(row, "order_number"))
        .fromCurrencyId(getIntegerColumnValue(row, "from_currency_id"))
        .toCurrencyId(getIntegerColumnValue(row, "to_currency_id"))
        .fromAmount(getBigDecimalColumnValue(row, "from_amount"))
        .toAmount(getBigDecimalColumnValue(row, "to_amount"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .completedAt(getLocalDateTimeColumnValue(row, "completed_at"))
        .failedAt(getLocalDateTimeColumnValue(row, "failed_at"))
        .errorMessage(getStringColumnValue(row, "error_message"))
        .build();
    
    /**
     * 환전 생성
     */
    public Future<Exchange> createExchange(SqlClient client, Exchange exchange) {
        Map<String, Object> params = new HashMap<>();
        params.put("exchange_id", exchange.getExchangeId());
        params.put("user_id", exchange.getUserId());
        params.put("order_number", exchange.getOrderNumber());
        params.put("from_currency_id", exchange.getFromCurrencyId());
        params.put("to_currency_id", exchange.getToCurrencyId());
        params.put("from_amount", exchange.getFromAmount());
        params.put("to_amount", exchange.getToAmount());
        params.put("status", exchange.getStatus());
        
        String sql = QueryBuilder.insert("exchanges", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(exchangeMapper, rows))
            .onFailure(e -> log.error("환전 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 환전 ID로 조회
     */
    public Future<Exchange> getExchangeByExchangeId(SqlClient client, String exchangeId) {
        String sql = QueryBuilder
            .select("exchanges")
            .where("exchange_id", Op.Equal, "exchange_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("exchange_id", exchangeId))
            .map(rows -> fetchOne(exchangeMapper, rows))
            .onFailure(e -> log.error("환전 조회 실패 - exchangeId: {}", exchangeId));
    }
    
    /**
     * 환전 상태 업데이트 (완료)
     */
    public Future<Exchange> completeExchange(SqlClient client, String exchangeId) {
        String sql = QueryBuilder
            .update("exchanges", "status", "completed_at")
            .where("exchange_id", Op.Equal, "exchange_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("exchange_id", exchangeId);
        params.put("status", Exchange.STATUS_COMPLETED);
        params.put("completed_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(exchangeMapper, rows))
            .onFailure(e -> log.error("환전 완료 처리 실패 - exchangeId: {}", exchangeId));
    }
    
    /**
     * 환전 상태 업데이트 (실패)
     */
    public Future<Exchange> failExchange(SqlClient client, String exchangeId, String errorMessage) {
        String sql = QueryBuilder
            .update("exchanges", "status", "failed_at", "error_message")
            .where("exchange_id", Op.Equal, "exchange_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("exchange_id", exchangeId);
        params.put("status", Exchange.STATUS_FAILED);
        params.put("failed_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("error_message", errorMessage);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(exchangeMapper, rows))
            .onFailure(e -> log.error("환전 실패 처리 실패 - exchangeId: {}", exchangeId));
    }
}

