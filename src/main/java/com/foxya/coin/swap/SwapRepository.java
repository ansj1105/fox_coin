package com.foxya.coin.swap;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.swap.entities.Swap;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SwapRepository extends BaseRepository {
    
    private final RowMapper<Swap> swapMapper = row -> Swap.builder()
        .id(getLongColumnValue(row, "id"))
        .swapId(getStringColumnValue(row, "swap_id"))
        .userId(getLongColumnValue(row, "user_id"))
        .orderNumber(getStringColumnValue(row, "order_number"))
        .fromCurrencyId(getIntegerColumnValue(row, "from_currency_id"))
        .toCurrencyId(getIntegerColumnValue(row, "to_currency_id"))
        .fromAmount(getBigDecimalColumnValue(row, "from_amount"))
        .toAmount(getBigDecimalColumnValue(row, "to_amount"))
        .network(getStringColumnValue(row, "network"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .completedAt(getLocalDateTimeColumnValue(row, "completed_at"))
        .failedAt(getLocalDateTimeColumnValue(row, "failed_at"))
        .errorMessage(getStringColumnValue(row, "error_message"))
        .build();
    
    /**
     * 스왑 생성
     */
    public Future<Swap> createSwap(SqlClient client, Swap swap) {
        Map<String, Object> params = new HashMap<>();
        params.put("swap_id", swap.getSwapId());
        params.put("user_id", swap.getUserId());
        params.put("order_number", swap.getOrderNumber());
        params.put("from_currency_id", swap.getFromCurrencyId());
        params.put("to_currency_id", swap.getToCurrencyId());
        params.put("from_amount", swap.getFromAmount());
        params.put("to_amount", swap.getToAmount());
        params.put("network", swap.getNetwork());
        params.put("status", swap.getStatus());
        
        String sql = QueryBuilder.insert("swaps", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(swapMapper, rows))
            .onFailure(e -> log.error("스왑 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 스왑 ID로 조회
     */
    public Future<Swap> getSwapBySwapId(SqlClient client, String swapId) {
        String sql = QueryBuilder
            .select("swaps")
            .where("swap_id", Op.Equal, "swap_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("swap_id", swapId))
            .map(rows -> fetchOne(swapMapper, rows))
            .onFailure(e -> log.error("스왑 조회 실패 - swapId: {}", swapId));
    }
    
    /**
     * 스왑 상태 업데이트 (완료)
     */
    public Future<Swap> completeSwap(SqlClient client, String swapId) {
        String sql = QueryBuilder
            .update("swaps", "status", "completed_at")
            .where("swap_id", Op.Equal, "swap_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("swap_id", swapId);
        params.put("status", Swap.STATUS_COMPLETED);
        params.put("completed_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(swapMapper, rows))
            .onFailure(e -> log.error("스왑 완료 처리 실패 - swapId: {}", swapId));
    }
    
    /**
     * 스왑 상태 업데이트 (실패)
     */
    public Future<Swap> failSwap(SqlClient client, String swapId, String errorMessage) {
        String sql = QueryBuilder
            .update("swaps", "status", "failed_at", "error_message")
            .where("swap_id", Op.Equal, "swap_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("swap_id", swapId);
        params.put("status", Swap.STATUS_FAILED);
        params.put("failed_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("error_message", errorMessage);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(swapMapper, rows))
            .onFailure(e -> log.error("스왑 실패 처리 실패 - swapId: {}", swapId));
    }
}

