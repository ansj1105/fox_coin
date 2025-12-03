package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TransferRepository extends BaseRepository {
    
    // ========== Row Mappers ==========
    
    private final RowMapper<InternalTransfer> internalTransferMapper = row -> InternalTransfer.builder()
        .id(getLongColumnValue(row, "id"))
        .transferId(getStringColumnValue(row, "transfer_id"))
        .senderId(getLongColumnValue(row, "sender_id"))
        .senderWalletId(getLongColumnValue(row, "sender_wallet_id"))
        .receiverId(getLongColumnValue(row, "receiver_id"))
        .receiverWalletId(getLongColumnValue(row, "receiver_wallet_id"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .fee(getBigDecimalColumnValue(row, "fee"))
        .status(getStringColumnValue(row, "status"))
        .transferType(getStringColumnValue(row, "transfer_type"))
        .memo(getStringColumnValue(row, "memo"))
        .requestIp(getStringColumnValue(row, "request_ip"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .completedAt(getLocalDateTimeColumnValue(row, "completed_at"))
        .failedAt(getLocalDateTimeColumnValue(row, "failed_at"))
        .errorMessage(getStringColumnValue(row, "error_message"))
        .build();
    
    private final RowMapper<ExternalTransfer> externalTransferMapper = row -> ExternalTransfer.builder()
        .id(getLongColumnValue(row, "id"))
        .transferId(getStringColumnValue(row, "transfer_id"))
        .userId(getLongColumnValue(row, "user_id"))
        .walletId(getLongColumnValue(row, "wallet_id"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .toAddress(getStringColumnValue(row, "to_address"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .fee(getBigDecimalColumnValue(row, "fee"))
        .networkFee(getBigDecimalColumnValue(row, "network_fee"))
        .status(getStringColumnValue(row, "status"))
        .txHash(getStringColumnValue(row, "tx_hash"))
        .chain(getStringColumnValue(row, "chain"))
        .confirmations(getIntegerColumnValue(row, "confirmations"))
        .requiredConfirmations(getIntegerColumnValue(row, "required_confirmations"))
        .memo(getStringColumnValue(row, "memo"))
        .requestIp(getStringColumnValue(row, "request_ip"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .submittedAt(getLocalDateTimeColumnValue(row, "submitted_at"))
        .confirmedAt(getLocalDateTimeColumnValue(row, "confirmed_at"))
        .failedAt(getLocalDateTimeColumnValue(row, "failed_at"))
        .errorCode(getStringColumnValue(row, "error_code"))
        .errorMessage(getStringColumnValue(row, "error_message"))
        .retryCount(getIntegerColumnValue(row, "retry_count"))
        .build();
    
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
    
    // ========== 내부 전송 ==========
    
    /**
     * 내부 전송 생성
     */
    public Future<InternalTransfer> createInternalTransfer(SqlClient client, InternalTransfer transfer) {
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transfer.getTransferId());
        params.put("sender_id", transfer.getSenderId());
        params.put("sender_wallet_id", transfer.getSenderWalletId());
        params.put("receiver_id", transfer.getReceiverId());
        params.put("receiver_wallet_id", transfer.getReceiverWalletId());
        params.put("currency_id", transfer.getCurrencyId());
        params.put("amount", transfer.getAmount());
        params.put("fee", transfer.getFee() != null ? transfer.getFee() : BigDecimal.ZERO);
        params.put("status", transfer.getStatus());
        params.put("transfer_type", transfer.getTransferType());
        params.put("memo", transfer.getMemo());
        params.put("request_ip", transfer.getRequestIp());
        
        String sql = QueryBuilder.insert("internal_transfers", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(internalTransferMapper, rows))
            .onFailure(e -> log.error("내부 전송 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 내부 전송 상태 업데이트 (완료)
     */
    public Future<InternalTransfer> completeInternalTransfer(SqlClient client, String transferId) {
        String sql = QueryBuilder
            .update("internal_transfers", "status", "completed_at")
            .where("transfer_id", Op.Equal, "transfer_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", InternalTransfer.STATUS_COMPLETED);
        params.put("completed_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(internalTransferMapper, rows))
            .onFailure(e -> log.error("내부 전송 완료 처리 실패: {}", e.getMessage()));
    }
    
    /**
     * 내부 전송 상태 업데이트 (실패)
     */
    public Future<InternalTransfer> failInternalTransfer(SqlClient client, String transferId, String errorMessage) {
        String sql = QueryBuilder
            .update("internal_transfers", "status", "failed_at", "error_message")
            .where("transfer_id", Op.Equal, "transfer_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", InternalTransfer.STATUS_FAILED);
        params.put("failed_at", DateUtils.now());
        params.put("error_message", errorMessage);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(internalTransferMapper, rows))
            .onFailure(e -> log.error("내부 전송 실패 처리 실패: {}", e.getMessage()));
    }
    
    /**
     * 내부 전송 조회 by transferId
     */
    public Future<InternalTransfer> getInternalTransferById(SqlClient client, String transferId) {
        String sql = QueryBuilder
            .select("internal_transfers")
            .where("transfer_id", Op.Equal, "transfer_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("transfer_id", transferId))
            .map(rows -> fetchOne(internalTransferMapper, rows));
    }
    
    /**
     * 사용자의 내부 전송 내역 조회
     */
    public Future<List<InternalTransfer>> getInternalTransfersByUserId(SqlClient client, Long userId, int limit, int offset) {
        String sql = "SELECT * FROM internal_transfers WHERE sender_id = #{user_id} OR receiver_id = #{user_id} " +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}";
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("limit", limit);
        params.put("offset", offset);
        
        return query(client, sql, params)
            .map(rows -> fetchAll(internalTransferMapper, rows));
    }
    
    // ========== 외부 전송 ==========
    
    /**
     * 외부 전송 생성
     */
    public Future<ExternalTransfer> createExternalTransfer(SqlClient client, ExternalTransfer transfer) {
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transfer.getTransferId());
        params.put("user_id", transfer.getUserId());
        params.put("wallet_id", transfer.getWalletId());
        params.put("currency_id", transfer.getCurrencyId());
        params.put("to_address", transfer.getToAddress());
        params.put("amount", transfer.getAmount());
        params.put("fee", transfer.getFee() != null ? transfer.getFee() : BigDecimal.ZERO);
        params.put("network_fee", transfer.getNetworkFee() != null ? transfer.getNetworkFee() : BigDecimal.ZERO);
        params.put("status", transfer.getStatus());
        params.put("chain", transfer.getChain());
        params.put("required_confirmations", transfer.getRequiredConfirmations() != null ? transfer.getRequiredConfirmations() : 1);
        params.put("memo", transfer.getMemo());
        params.put("request_ip", transfer.getRequestIp());
        
        String sql = QueryBuilder.insert("external_transfers", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(externalTransferMapper, rows))
            .onFailure(e -> log.error("외부 전송 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 외부 전송 상태 업데이트 (제출됨)
     */
    public Future<ExternalTransfer> submitExternalTransfer(SqlClient client, String transferId, String txHash) {
        String sql = QueryBuilder
            .update("external_transfers", "status", "tx_hash", "submitted_at")
            .where("transfer_id", Op.Equal, "transfer_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", ExternalTransfer.STATUS_SUBMITTED);
        params.put("tx_hash", txHash);
        params.put("submitted_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(externalTransferMapper, rows))
            .onFailure(e -> log.error("외부 전송 제출 처리 실패: {}", e.getMessage()));
    }
    
    /**
     * 외부 전송 상태 업데이트 (컨펌)
     */
    public Future<ExternalTransfer> confirmExternalTransfer(SqlClient client, String transferId, int confirmations) {
        String sql = QueryBuilder
            .update("external_transfers", "status", "confirmations", "confirmed_at")
            .where("transfer_id", Op.Equal, "transfer_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", ExternalTransfer.STATUS_CONFIRMED);
        params.put("confirmations", confirmations);
        params.put("confirmed_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(externalTransferMapper, rows))
            .onFailure(e -> log.error("외부 전송 컨펌 처리 실패: {}", e.getMessage()));
    }
    
    /**
     * 외부 전송 상태 업데이트 (실패)
     */
    public Future<ExternalTransfer> failExternalTransfer(SqlClient client, String transferId, String errorCode, String errorMessage) {
        String sql = QueryBuilder
            .update("external_transfers", "status", "failed_at", "error_code", "error_message")
            .where("transfer_id", Op.Equal, "transfer_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("transfer_id", transferId);
        params.put("status", ExternalTransfer.STATUS_FAILED);
        params.put("failed_at", DateUtils.now());
        params.put("error_code", errorCode);
        params.put("error_message", errorMessage);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(externalTransferMapper, rows))
            .onFailure(e -> log.error("외부 전송 실패 처리 실패: {}", e.getMessage()));
    }
    
    /**
     * 외부 전송 조회 by transferId
     */
    public Future<ExternalTransfer> getExternalTransferById(SqlClient client, String transferId) {
        String sql = QueryBuilder
            .select("external_transfers")
            .where("transfer_id", Op.Equal, "transfer_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("transfer_id", transferId))
            .map(rows -> fetchOne(externalTransferMapper, rows));
    }
    
    /**
     * 외부 전송 조회 by txHash
     */
    public Future<ExternalTransfer> getExternalTransferByTxHash(SqlClient client, String txHash) {
        String sql = QueryBuilder
            .select("external_transfers")
            .where("tx_hash", Op.Equal, "tx_hash")
            .build();
        
        return query(client, sql, Collections.singletonMap("tx_hash", txHash))
            .map(rows -> fetchOne(externalTransferMapper, rows));
    }
    
    /**
     * 처리 대기중인 외부 전송 조회 (Node.js 서비스에서 처리)
     */
    public Future<List<ExternalTransfer>> getPendingExternalTransfers(SqlClient client, int limit) {
        String sql = QueryBuilder
            .select("external_transfers")
            .where("status", Op.Equal, "status")
            .orderBy("created_at", true)
            .limit(limit)
            .build();
        
        return query(client, sql, Collections.singletonMap("status", ExternalTransfer.STATUS_PENDING))
            .map(rows -> fetchAll(externalTransferMapper, rows));
    }
    
    // ========== 지갑 관련 ==========
    
    /**
     * 지갑 주소로 지갑 조회
     */
    public Future<Wallet> getWalletByAddress(SqlClient client, String address) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("address", Op.Equal, "address")
            .andWhere("status", Op.Equal, "status")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("address", address);
        params.put("status", "ACTIVE");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows));
    }
    
    /**
     * 사용자의 특정 통화 지갑 조회
     */
    public Future<Wallet> getWalletByUserIdAndCurrencyId(SqlClient client, Long userId, Integer currencyId) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("currency_id", Op.Equal, "currency_id")
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("currency_id", currencyId);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows));
    }
    
    /**
     * 지갑 잔액 차감 (송신자)
     */
    public Future<Wallet> deductBalance(SqlClient client, Long walletId, BigDecimal amount) {
        String sql = "UPDATE user_wallets SET balance = balance - #{amount}, updated_at = #{updated_at} " +
            "WHERE id = #{id} AND balance >= #{amount} RETURNING *";
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("잔액 차감 실패 - walletId: {}, amount: {}", walletId, amount));
    }
    
    /**
     * 지갑 잔액 추가 (수신자)
     */
    public Future<Wallet> addBalance(SqlClient client, Long walletId, BigDecimal amount) {
        String sql = "UPDATE user_wallets SET balance = balance + #{amount}, updated_at = #{updated_at} " +
            "WHERE id = #{id} RETURNING *";
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("잔액 추가 실패 - walletId: {}, amount: {}", walletId, amount));
    }
    
    /**
     * 지갑 잔액 잠금 (외부 전송 시)
     */
    public Future<Wallet> lockBalance(SqlClient client, Long walletId, BigDecimal amount) {
        String sql = "UPDATE user_wallets SET balance = balance - #{amount}, locked_balance = locked_balance + #{amount}, " +
            "updated_at = #{updated_at} WHERE id = #{id} AND balance >= #{amount} RETURNING *";
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("잔액 잠금 실패 - walletId: {}, amount: {}", walletId, amount));
    }
    
    /**
     * 지갑 잔액 잠금 해제 (외부 전송 완료/실패 시)
     */
    public Future<Wallet> unlockBalance(SqlClient client, Long walletId, BigDecimal amount, boolean refund) {
        String sql;
        if (refund) {
            // 실패 시 잔액 복구
            sql = "UPDATE user_wallets SET balance = balance + #{amount}, locked_balance = locked_balance - #{amount}, " +
                "updated_at = #{updated_at} WHERE id = #{id} RETURNING *";
        } else {
            // 성공 시 잠금 잔액만 차감
            sql = "UPDATE user_wallets SET locked_balance = locked_balance - #{amount}, " +
                "updated_at = #{updated_at} WHERE id = #{id} RETURNING *";
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("잔액 잠금 해제 실패 - walletId: {}, amount: {}, refund: {}", walletId, amount, refund));
    }
}

