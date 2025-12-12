package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.deposit.entities.TokenDeposit;
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
public class TokenDepositRepository extends BaseRepository {
    
    private final RowMapper<TokenDeposit> tokenDepositMapper = row -> TokenDeposit.builder()
        .id(getLongColumnValue(row, "id"))
        .depositId(getStringColumnValue(row, "deposit_id"))
        .userId(getLongColumnValue(row, "user_id"))
        .orderNumber(getStringColumnValue(row, "order_number"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .network(getStringColumnValue(row, "network"))
        .senderAddress(getStringColumnValue(row, "sender_address"))
        .txHash(getStringColumnValue(row, "tx_hash"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .confirmedAt(getLocalDateTimeColumnValue(row, "confirmed_at"))
        .failedAt(getLocalDateTimeColumnValue(row, "failed_at"))
        .errorMessage(getStringColumnValue(row, "error_message"))
        .build();
    
    /**
     * 토큰 입금 생성
     */
    public Future<TokenDeposit> createTokenDeposit(SqlClient client, TokenDeposit deposit) {
        Map<String, Object> params = new HashMap<>();
        params.put("deposit_id", deposit.getDepositId());
        params.put("user_id", deposit.getUserId());
        params.put("order_number", deposit.getOrderNumber());
        params.put("currency_id", deposit.getCurrencyId());
        params.put("amount", deposit.getAmount());
        params.put("network", deposit.getNetwork());
        params.put("sender_address", deposit.getSenderAddress());
        params.put("tx_hash", deposit.getTxHash());
        params.put("status", deposit.getStatus());
        
        String sql = QueryBuilder.insert("token_deposits", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(tokenDepositMapper, rows))
            .onFailure(e -> log.error("토큰 입금 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 사용자별 토큰 입금 목록 조회
     */
    public Future<List<TokenDeposit>> getTokenDepositsByUserId(SqlClient client, Long userId, Integer currencyId, int limit, int offset) {
        String sql;
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        
        if (currencyId != null) {
            sql = QueryBuilder
                .select("token_deposits")
                .where("user_id", Op.Equal, "user_id")
                .andWhere("currency_id", Op.Equal, "currency_id")
                .orderBy("created_at", Sort.DESC)
                .limitRefactoring()
                .offsetRefactoring()
                .build();
            params.put("currency_id", currencyId);
        } else {
            sql = QueryBuilder
                .select("token_deposits")
                .where("user_id", Op.Equal, "user_id")
                .orderBy("created_at", Sort.DESC)
                .limitRefactoring()
                .offsetRefactoring()
                .build();
        }
        
        params.put("limit", limit);
        params.put("offset", offset);
        
        return query(client, sql, params)
            .map(rows -> fetchAll(tokenDepositMapper, rows))
            .onFailure(e -> log.error("토큰 입금 목록 조회 실패 - userId: {}", userId));
    }
    
    /**
     * 토큰 입금 ID로 조회
     */
    public Future<TokenDeposit> getTokenDepositByDepositId(SqlClient client, String depositId) {
        String sql = QueryBuilder
            .select("token_deposits")
            .where("deposit_id", Op.Equal, "deposit_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("deposit_id", depositId))
            .map(rows -> fetchOne(tokenDepositMapper, rows))
            .onFailure(e -> log.error("토큰 입금 조회 실패 - depositId: {}", depositId));
    }
    
    /**
     * 토큰 입금 상태 업데이트 (완료)
     */
    public Future<TokenDeposit> completeTokenDeposit(SqlClient client, String depositId) {
        String sql = QueryBuilder
            .update("token_deposits", "status", "confirmed_at")
            .where("deposit_id", Op.Equal, "deposit_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("deposit_id", depositId);
        params.put("status", TokenDeposit.STATUS_COMPLETED);
        params.put("confirmed_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(tokenDepositMapper, rows))
            .onFailure(e -> log.error("토큰 입금 완료 처리 실패 - depositId: {}", depositId));
    }
    
    /**
     * 토큰 입금 상태 업데이트 (실패)
     */
    public Future<TokenDeposit> failTokenDeposit(SqlClient client, String depositId, String errorMessage) {
        String sql = QueryBuilder
            .update("token_deposits", "status", "failed_at", "error_message")
            .where("deposit_id", Op.Equal, "deposit_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("deposit_id", depositId);
        params.put("status", TokenDeposit.STATUS_FAILED);
        params.put("failed_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("error_message", errorMessage);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(tokenDepositMapper, rows))
            .onFailure(e -> log.error("토큰 입금 실패 처리 실패 - depositId: {}", depositId));
    }
}

