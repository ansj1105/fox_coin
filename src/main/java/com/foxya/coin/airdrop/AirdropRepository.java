package com.foxya.coin.airdrop;

import com.foxya.coin.airdrop.entities.AirdropPhase;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AirdropRepository extends BaseRepository {
    
    // ========== Row Mappers ==========
    
    private final RowMapper<AirdropPhase> airdropPhaseMapper = row -> AirdropPhase.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .phase(getIntegerColumnValue(row, "phase"))
        .status(getStringColumnValue(row, "status"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .unlockDate(getLocalDateTimeColumnValue(row, "unlock_date"))
        .daysRemaining(getIntegerColumnValue(row, "days_remaining"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    private final RowMapper<AirdropTransfer> airdropTransferMapper = row -> AirdropTransfer.builder()
        .id(getLongColumnValue(row, "id"))
        .transferId(getStringColumnValue(row, "transfer_id"))
        .userId(getLongColumnValue(row, "user_id"))
        .walletId(getLongColumnValue(row, "wallet_id"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .status(getStringColumnValue(row, "status"))
        .orderNumber(getStringColumnValue(row, "order_number"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    // ========== Airdrop Phase ==========
    
    /**
     * 사용자의 모든 에어드랍 Phase 조회
     */
    public Future<List<AirdropPhase>> getPhasesByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("airdrop_phases")
            .where("user_id", Op.Equal, "user_id")
            .orderBy("phase", com.foxya.coin.utils.BaseQueryBuilder.Sort.ASC)
            .build();
        
        return query(client, sql, Collections.singletonMap("user_id", userId))
            .map(rows -> fetchAll(airdropPhaseMapper, rows));
    }
    
    /**
     * 사용자의 특정 Phase 조회
     */
    public Future<AirdropPhase> getPhaseByUserIdAndPhase(SqlClient client, Long userId, Integer phase) {
        String sql = QueryBuilder
            .select("airdrop_phases")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("phase", Op.Equal, "phase")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("phase", phase);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(airdropPhaseMapper, rows));
    }
    
    /**
     * 에어드랍 Phase 생성
     */
    public Future<AirdropPhase> createPhase(SqlClient client, AirdropPhase phase) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", phase.getUserId());
        params.put("phase", phase.getPhase());
        params.put("status", phase.getStatus());
        params.put("amount", phase.getAmount());
        params.put("unlock_date", phase.getUnlockDate());
        params.put("days_remaining", phase.getDaysRemaining());
        
        String sql = QueryBuilder.insert("airdrop_phases", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(airdropPhaseMapper, rows))
            .onFailure(e -> log.error("에어드랍 Phase 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 에어드랍 Phase 상태 업데이트
     */
    public Future<AirdropPhase> updatePhaseStatus(SqlClient client, Long userId, Integer phase, String status, Integer daysRemaining) {
        String sql = QueryBuilder
            .update("airdrop_phases", "status", "days_remaining", "updated_at")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("phase", Op.Equal, "phase")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("phase", phase);
        params.put("status", status);
        params.put("days_remaining", daysRemaining);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(airdropPhaseMapper, rows))
            .onFailure(e -> log.error("에어드랍 Phase 상태 업데이트 실패: {}", e.getMessage()));
    }
    
    // ========== Airdrop Transfer ==========
    
    /**
     * 에어드랍 전송 생성
     */
    public Future<AirdropTransfer> createTransfer(SqlClient client, AirdropTransfer transfer) {
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transfer.getTransferId());
        params.put("user_id", transfer.getUserId());
        params.put("wallet_id", transfer.getWalletId());
        params.put("currency_id", transfer.getCurrencyId());
        params.put("amount", transfer.getAmount());
        params.put("status", transfer.getStatus());
        params.put("order_number", transfer.getOrderNumber());
        
        String sql = QueryBuilder.insert("airdrop_transfers", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(airdropTransferMapper, rows))
            .onFailure(e -> log.error("에어드랍 전송 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 에어드랍 전송 상태 업데이트
     */
    public Future<AirdropTransfer> updateTransferStatus(SqlClient client, String transferId, String status) {
        String sql = QueryBuilder
            .update("airdrop_transfers", "status", "updated_at")
            .where("transfer_id", Op.Equal, "transfer_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", status);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(airdropTransferMapper, rows))
            .onFailure(e -> log.error("에어드랍 전송 상태 업데이트 실패: {}", e.getMessage()));
    }
    
    /**
     * 에어드랍 전송 조회 by transferId
     */
    public Future<AirdropTransfer> getTransferById(SqlClient client, String transferId) {
        String sql = QueryBuilder
            .select("airdrop_transfers")
            .where("transfer_id", Op.Equal, "transfer_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("transfer_id", transferId))
            .map(rows -> fetchOne(airdropTransferMapper, rows));
    }
}

