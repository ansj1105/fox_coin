package com.foxya.coin.exchange;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.exchange.entities.Exchange;
import com.foxya.coin.exchange.entities.ExchangeSetting;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private final RowMapper<ExchangeSetting> exchangeSettingMapper = row -> ExchangeSetting.builder()
        .id(getLongColumnValue(row, "id"))
        .fromCurrencyCode(getStringColumnValue(row, "from_currency_code"))
        .toCurrencyCode(getStringColumnValue(row, "to_currency_code"))
        .exchangeRate(getBigDecimalColumnValue(row, "exchange_rate"))
        .fee(getBigDecimalColumnValue(row, "fee"))
        .minExchangeAmount(getBigDecimalColumnValue(row, "min_exchange_amount"))
        .note(getStringColumnValue(row, "note"))
        .isActive(getBooleanColumnValue(row, "is_active"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
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
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        return query(client, sql, Collections.singletonMap("exchange_id", exchangeId))
            .map(rows -> fetchOne(exchangeMapper, rows))
            .onFailure(e -> log.error("환전 조회 실패 - exchangeId: {}", exchangeId));
    }

    public Future<List<Exchange>> getExchangesByUserId(SqlClient client, Long userId, int limit, int offset) {
        String sql = QueryBuilder
            .select("exchanges")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .orderBy("created_at", Sort.DESC)
            .limitRefactoring()
            .offsetRefactoring()
            .build();

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("limit", limit);
        params.put("offset", offset);

        return query(client, sql, params)
            .map(rows -> fetchAll(exchangeMapper, rows))
            .onFailure(e -> log.error("환전 목록 조회 실패 - userId: {}", userId, e));
    }

    public Future<ExchangeSetting> getActiveExchangeSetting(SqlClient client) {
        String sql = QueryBuilder
            .select("exchange_settings")
            .where("is_active", Op.Equal, "is_active")
            .build() + " ORDER BY id DESC LIMIT 1";

        return query(client, sql, Collections.singletonMap("is_active", true))
            .map(rows -> fetchOne(exchangeSettingMapper, rows));
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
    
    /**
     * 사용자의 모든 환전 Soft Delete
     */
    public Future<Void> softDeleteExchangesByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("exchanges", "deleted_at")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Exchanges soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("환전 Soft Delete 실패 - userId: {}", userId, throwable));
    }
}
