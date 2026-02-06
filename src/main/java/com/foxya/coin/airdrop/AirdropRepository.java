package com.foxya.coin.airdrop;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import com.foxya.coin.airdrop.entities.AirdropPhase;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AirdropRepository extends BaseRepository {
    
    private final RowMapper<AirdropPhase> phaseMapper = row -> AirdropPhase.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .phase(getIntegerColumnValue(row, "phase"))
        .status(getStringColumnValue(row, "status"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .claimed(getBooleanColumnValue(row, "claimed"))
        .unlockDate(getLocalDateTimeColumnValue(row, "unlock_date"))
        .daysRemaining(getIntegerColumnValue(row, "days_remaining"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .updatedAt(getLocalDateTimeColumnValue(row, "updated_at"))
        .build();
    
    private final RowMapper<AirdropTransfer> transferMapper = row -> AirdropTransfer.builder()
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
    
    /**
     * 사용자별 Phase 목록 조회 (삭제되지 않은 것만)
     */
    public Future<List<AirdropPhase>> getPhasesByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .select("airdrop_phases")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .orderBy("phase", Sort.ASC)
            .build();
        
        return query(client, sql, Collections.singletonMap("user_id", userId))
            .map(rows -> fetchAll(phaseMapper, rows))
            .onFailure(throwable -> log.error("Phase 조회 실패 - userId: {}", userId));
    }
    
    /**
     * 삭제되지 않은 Phase 목록 조회 (not_deleted 전용)
     */
    public Future<List<AirdropPhase>> getPhasesByUserIdNotDeleted(SqlClient client, Long userId) {
        return getPhasesByUserId(client, userId);
    }
    
    /**
     * phaseId + userId로 Phase 단건 조회 (삭제되지 않은 것만)
     */
    public Future<AirdropPhase> getPhaseByIdAndUserId(SqlClient client, Long phaseId, Long userId) {
        String sql = QueryBuilder
            .select("airdrop_phases")
            .where("id", Op.Equal, "phase_id")
            .andWhere("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("phase_id", phaseId);
        params.put("user_id", userId);
        return query(client, sql, params)
            .map(rows -> fetchOne(phaseMapper, rows))
            .onFailure(throwable -> log.error("Phase 조회 실패 - phaseId: {}, userId: {}", phaseId, userId, throwable));
    }
    
    /**
     * Phase의 claimed를 true로 갱신 (Release 완료 처리)
     */
    public Future<AirdropPhase> updatePhaseClaimed(SqlClient client, Long phaseId, Long userId) {
        String sql = "UPDATE airdrop_phases SET claimed = TRUE, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{phase_id} AND user_id = #{user_id} AND deleted_at IS NULL AND claimed = FALSE RETURNING *";
        Map<String, Object> params = new HashMap<>();
        params.put("phase_id", phaseId);
        params.put("user_id", userId);
        return query(client, sql, params)
            .map(rows -> fetchOne(phaseMapper, rows))
            .onFailure(throwable -> log.error("Phase claimed 갱신 실패 - phaseId: {}, userId: {}", phaseId, userId, throwable));
    }
    
    /**
     * 에어드랍 전송 생성
     */
    public Future<AirdropTransfer> createTransfer(SqlClient client, AirdropTransfer transfer) {
        String sql = QueryBuilder
            .insert("airdrop_transfers", 
                "transfer_id", "user_id", "wallet_id", "currency_id", "amount", "status", "order_number")
            .returningColumns("id, transfer_id, user_id, wallet_id, currency_id, amount, status, order_number, created_at, updated_at")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transfer.getTransferId());
        params.put("user_id", transfer.getUserId());
        params.put("wallet_id", transfer.getWalletId());
        params.put("currency_id", transfer.getCurrencyId());
        params.put("amount", transfer.getAmount());
        params.put("status", transfer.getStatus());
        params.put("order_number", transfer.getOrderNumber());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(transferMapper, rows))
            .onFailure(throwable -> log.error("에어드랍 전송 생성 실패: {}", throwable.getMessage()));
    }
    
    /**
     * 전송 상태 업데이트
     */
    public Future<AirdropTransfer> updateTransferStatus(SqlClient client, String transferId, String status) {
        String sql = "UPDATE airdrop_transfers SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE transfer_id = #{transfer_id} RETURNING *";
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", status);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(transferMapper, rows))
            .onFailure(throwable -> log.error("전송 상태 업데이트 실패 - transferId: {}", transferId));
    }
    
    /**
     * 지급 완료(claimed=true)된 Phase만 Soft Delete. transfer 성공 후 목록에서 제외할 때 사용.
     */
    public Future<Void> softDeleteClaimedPhasesByUserId(SqlClient client, Long userId) {
        String sql = "UPDATE airdrop_phases SET deleted_at = #{deleted_at}, updated_at = #{updated_at} " +
            "WHERE user_id = #{user_id} AND deleted_at IS NULL AND claimed = TRUE";
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.debug("Claimed airdrop phases soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("Claimed Phase Soft Delete 실패 - userId: {}", userId, throwable));
    }

    /**
     * 사용자의 모든 에어드랍 Phase Soft Delete
     */
    public Future<Void> softDeleteAirdropPhasesByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("airdrop_phases", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Airdrop phases soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("에어드랍 Phase Soft Delete 실패 - userId: {}", userId, throwable));
    }
    
    /**
     * 사용자의 모든 에어드랍 전송 Soft Delete
     */
    public Future<Void> softDeleteAirdropTransfersByUserId(SqlClient client, Long userId) {
        String sql = QueryBuilder
            .update("airdrop_transfers", "deleted_at", "updated_at")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("deleted_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("updated_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .<Void>map(rows -> {
                log.info("Airdrop transfers soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("에어드랍 전송 Soft Delete 실패 - userId: {}", userId, throwable));
    }
}

