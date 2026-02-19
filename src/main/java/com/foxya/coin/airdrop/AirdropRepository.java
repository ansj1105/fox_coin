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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class AirdropRepository extends BaseRepository {
    
    private final RowMapper<AirdropPhase> phaseMapper = row -> AirdropPhase.builder()
        .id(getLongColumnValue(row, "id"))
        .userId(getLongColumnValue(row, "user_id"))
        .phase(getIntegerColumnValue(row, "phase"))
        .status(getStringColumnValue(row, "status"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .transferredAmount(getBigDecimalColumnValue(row, "transferred_amount") != null ? getBigDecimalColumnValue(row, "transferred_amount") : java.math.BigDecimal.ZERO)
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
            .select("airdrop_phases", "id", "user_id", "phase", "status", "amount", "transferred_amount", "claimed", "unlock_date", "days_remaining", "created_at", "updated_at")
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
     * 추천인 등록 보상용 phase 생성 (동일 user+phase가 이미 있으면 생성하지 않음)
     */
    public Future<AirdropPhase> createPhaseIfAbsent(
        SqlClient client,
        Long userId,
        Integer phase,
        BigDecimal amount,
        LocalDateTime unlockDate,
        Integer daysRemaining
    ) {
        String sql = """
            INSERT INTO airdrop_phases (user_id, phase, status, amount, unlock_date, days_remaining, claimed, created_at, updated_at)
            SELECT #{user_id}, #{phase}, #{status}, #{amount}, #{unlock_date}, #{days_remaining}, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1
                FROM airdrop_phases
                WHERE user_id = #{user_id}
                  AND phase = #{phase}
                  AND deleted_at IS NULL
            )
            RETURNING *
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("phase", phase);
        params.put("status", AirdropPhase.STATUS_PROCESSING);
        params.put("amount", amount);
        params.put("unlock_date", unlockDate);
        params.put("days_remaining", daysRemaining);

        return query(client, QueryBuilder.selectStringQuery(sql).build(), params)
            .map(rows -> fetchOne(phaseMapper, rows))
            .onFailure(throwable -> log.error("추천인 등록 보상 Phase 생성 실패 - userId: {}, phase: {}", userId, phase, throwable));
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
     * 사용자별 에어드랍 전송 내역 조회 (거래내역 API 병합용, deleted_at 제외)
     */
    public Future<List<AirdropTransfer>> getTransfersByUserId(SqlClient client, Long userId, int limit, int offset) {
        String sql = QueryBuilder
            .select("airdrop_transfers")
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
            .map(rows -> fetchAll(transferMapper, rows))
            .onFailure(throwable -> log.error("에어드랍 전송 내역 조회 실패 - userId: {}", userId, throwable));
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
     * Phase의 transferred_amount에 전송량 추가 (전송 시 해당 Phase에 할당)
     */
    public Future<Void> addTransferredAmount(SqlClient client, Long phaseId, Long userId, BigDecimal addAmount) {
        if (addAmount == null || addAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.succeededFuture();
        }
        String sql = "UPDATE airdrop_phases SET transferred_amount = transferred_amount + #{add_amount}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{phase_id} AND user_id = #{user_id} AND deleted_at IS NULL AND claimed = TRUE";
        Map<String, Object> params = new HashMap<>();
        params.put("phase_id", phaseId);
        params.put("user_id", userId);
        params.put("add_amount", addAmount);
        return query(client, sql, params)
            .<Void>map(rows -> null)
            .onFailure(throwable -> log.error("transferred_amount 추가 실패 - phaseId: {}, userId: {}", phaseId, userId, throwable));
    }

    /**
     * 전송한 금액만큼 claimed Phase에 transferred_amount 할당 (id 순). 전송 후 잔량 = amount - transferred_amount 유지.
     */
    public Future<Void> allocateTransferredAmount(SqlClient client, Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.succeededFuture();
        }
        return getPhasesByUserId(client, userId)
            .compose(phases -> {
                List<AirdropPhase> claimed = phases.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getClaimed()))
                    .sorted(Comparator.comparing(AirdropPhase::getId))
                    .collect(Collectors.toList());
                BigDecimal remaining = amount;
                io.vertx.core.Future<Void> chain = Future.succeededFuture();
                for (AirdropPhase p : claimed) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal ta = p.getTransferredAmount() != null ? p.getTransferredAmount() : BigDecimal.ZERO;
                    BigDecimal available = p.getAmount().subtract(ta);
                    if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal add = remaining.min(available);
                    final BigDecimal addFinal = add;
                    chain = chain.compose(v -> addTransferredAmount(client, p.getId(), userId, addFinal));
                    remaining = remaining.subtract(add);
                }
                return chain;
            });
    }

    /**
     * 사용자의 모든 에어드랍 Phase Soft Delete (회원 탈퇴 등에서만 사용. 전송 시에는 사용하지 않음 — 데이터 정합성)
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
