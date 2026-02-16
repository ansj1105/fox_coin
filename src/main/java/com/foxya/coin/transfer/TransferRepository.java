package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.common.utils.DateUtils;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.BaseQueryBuilder.Sort;
import com.foxya.coin.utils.QueryBuilder;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        .orderNumber(getStringColumnValue(row, "order_number"))
        .transactionType(getStringColumnValue(row, "transaction_type"))
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
        .orderNumber(getStringColumnValue(row, "order_number"))
        .transactionType(getStringColumnValue(row, "transaction_type"))
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
    
    // Normalized comment.
    
    /**
      * Normalized comment.
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
        params.put("order_number", transfer.getOrderNumber());
        params.put("transaction_type", transfer.getTransactionType());
        params.put("memo", transfer.getMemo());
        params.put("request_ip", transfer.getRequestIp());
        
        String sql = QueryBuilder.insert("internal_transfers", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(internalTransferMapper, rows))
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
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
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
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
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
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
      * Normalized comment.
     */
    public Future<List<InternalTransfer>> getInternalTransfersByUserId(SqlClient client, Long userId, int limit, int offset) {
        String sql = QueryBuilder
            .select("internal_transfers")
            .whereOrEquals("sender_id", "receiver_id", "user_id")
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
            .map(rows -> fetchAll(internalTransferMapper, rows));
    }
    
    /**
      * Normalized comment.
     */
    public Future<List<InternalTransfer>> getReferralRewardTransfersByReceiver(SqlClient client, Long receiverId, LocalDate startDate, Integer limit, Integer offset) {
        QueryBuilder.SelectQueryBuilder q = QueryBuilder
            .select("internal_transfers")
            .where("receiver_id", Op.Equal, "receiver_id")
            .andWhere("transfer_type", Op.Equal, "transfer_type")
            .andWhere("status", Op.Equal, "status")
            .andWhere("deleted_at", Op.IsNull);
        if (startDate != null) {
            q = q.andWhere("created_at", Op.GreaterThanOrEqual, "start_datetime");
        }
        String sql = q.orderBy("created_at", Sort.DESC).limit(limit).offset(offset).build();
        Map<String, Object> params = new HashMap<>();
        params.put("receiver_id", receiverId);
        params.put("transfer_type", InternalTransfer.TYPE_REFERRAL_REWARD);
        params.put("status", InternalTransfer.STATUS_COMPLETED);
        if (startDate != null) {
            params.put("start_datetime", startDate.atStartOfDay());
        }
        params.put("limit", limit);
        params.put("offset", offset);
        return query(client, sql, params)
            .map(rows -> fetchAll(internalTransferMapper, rows))
            .onFailure(throwable -> log.error("Normalized log message", receiverId, throwable));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Long> getReferralRewardCountByReceiver(SqlClient client, Long receiverId, LocalDate startDate) {
        QueryBuilder.SelectQueryBuilder q = QueryBuilder
            .count("internal_transfers", "it", "total")
            .where("it.receiver_id", Op.Equal, "receiver_id")
            .andWhere("it.transfer_type", Op.Equal, "transfer_type")
            .andWhere("it.status", Op.Equal, "status")
            .andWhere("it.deleted_at", Op.IsNull);
        if (startDate != null) {
            q = q.andWhere("it.created_at", Op.GreaterThanOrEqual, "start_datetime");
        }
        String sql = q.build();
        Map<String, Object> params = new HashMap<>();
        params.put("receiver_id", receiverId);
        params.put("transfer_type", InternalTransfer.TYPE_REFERRAL_REWARD);
        params.put("status", InternalTransfer.STATUS_COMPLETED);
        if (startDate != null) {
            params.put("start_datetime", startDate.atStartOfDay());
        }
        return query(client, sql, params)
            .map(rows -> {
                var it = rows.iterator();
                if (!it.hasNext()) return 0L;
                Long v = it.next().getLong("total");
                return v != null ? v : 0L;
            })
            .onFailure(throwable -> log.error("Normalized log message", receiverId, throwable));
    }
    
    /**
      * Normalized comment.
     */
    public Future<BigDecimal> getReferralRewardTotalAmountByReceiver(SqlClient client, Long receiverId, LocalDate startDate) {
        QueryBuilder.SelectQueryBuilder q = QueryBuilder
            .selectAlias("internal_transfers", "it", "COALESCE(SUM(it.amount), 0) as total_amount")
            .where("it.receiver_id", Op.Equal, "receiver_id")
            .andWhere("it.transfer_type", Op.Equal, "transfer_type")
            .andWhere("it.status", Op.Equal, "status")
            .andWhere("it.deleted_at", Op.IsNull);
        if (startDate != null) {
            q = q.andWhere("it.created_at", Op.GreaterThanOrEqual, "start_datetime");
        }
        String sql = q.build();
        Map<String, Object> params = new HashMap<>();
        params.put("receiver_id", receiverId);
        params.put("transfer_type", InternalTransfer.TYPE_REFERRAL_REWARD);
        params.put("status", InternalTransfer.STATUS_COMPLETED);
        if (startDate != null) {
            params.put("start_datetime", startDate.atStartOfDay());
        }
        return query(client, sql, params)
            .map(rows -> {
                var it = rows.iterator();
                if (!it.hasNext()) return BigDecimal.ZERO;
                BigDecimal v = it.next().getBigDecimal("total_amount");
                return v != null ? v : BigDecimal.ZERO;
            })
            .onFailure(throwable -> log.error("Normalized log message", receiverId, throwable));
    }
    
    // Normalized comment.
    
    /**
      * Normalized comment.
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
        params.put("order_number", transfer.getOrderNumber());
        params.put("transaction_type", transfer.getTransactionType());
        params.put("chain", transfer.getChain());
        params.put("required_confirmations", transfer.getRequiredConfirmations() != null ? transfer.getRequiredConfirmations() : 1);
        params.put("retry_count", transfer.getRetryCount() != null ? transfer.getRetryCount() : 0);
        params.put("memo", transfer.getMemo());
        params.put("request_ip", transfer.getRequestIp());
        
        String sql = QueryBuilder.insert("external_transfers", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(externalTransferMapper, rows))
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
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
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
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
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
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
            .onFailure(e -> log.error("Normalized log message", e.getMessage()));
    }
    
    /**
      * Normalized comment.
     */
    public Future<List<ExternalTransfer>> getExternalTransfersByUserId(SqlClient client, Long userId, int limit, int offset) {
        String sql = QueryBuilder
            .select("external_transfers")
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
            .map(rows -> fetchAll(externalTransferMapper, rows))
            .onFailure(e -> log.error("Normalized log message", userId));
    }
    
    /**
      * Normalized comment.
     */
    public Future<ExternalTransfer> getExternalTransferById(SqlClient client, String transferId) {
        String sql = QueryBuilder
            .select("external_transfers")
            .where("transfer_id", Op.Equal, "transfer_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        return query(client, sql, Collections.singletonMap("transfer_id", transferId))
            .map(rows -> fetchOne(externalTransferMapper, rows));
    }
    
    /**
      * Normalized comment.
     */
    public Future<List<ExternalTransfer>> getExternalTransfersByStatus(SqlClient client, String status, int limit) {
        String sql = QueryBuilder
            .select("external_transfers")
            .where("status", Op.Equal, "status")
            .andWhere("deleted_at", Op.IsNull)
            .andWhere("tx_hash", Op.IsNotNull)
            .orderBy("created_at", Sort.ASC)
            .limitRefactoring()
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("status", status);
        params.put("limit", Math.min(limit, 500));
        return query(client, sql, params)
            .map(rows -> fetchAll(externalTransferMapper, rows))
            .onFailure(e -> log.error("Normalized log message", status));
    }

    /**
      * Normalized comment.
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
      * Normalized comment.
     */
    public Future<List<ExternalTransfer>> getPendingExternalTransfers(SqlClient client, int limit) {
        String sql = QueryBuilder
            .select("external_transfers")
            .where("status", Op.Equal, "status")
            .andWhere("deleted_at", Op.IsNull)
            .orderBy("created_at", Sort.ASC)
            .limit(limit)
            .build();
        
        return query(client, sql, Collections.singletonMap("status", ExternalTransfer.STATUS_PENDING))
            .map(rows -> fetchAll(externalTransferMapper, rows));
    }

    /**
     * Increase retry_count for a transfer. This is used by a periodic redispatch scheduler.
     */
    public Future<ExternalTransfer> incrementExternalTransferRetryCount(SqlClient client, String transferId) {
        String sql = QueryBuilder
            .update("external_transfers")
            .setCustom("retry_count = COALESCE(retry_count, 0) + 1")
            .where("transfer_id", Op.Equal, "transfer_id")
            .andWhere("deleted_at", Op.IsNull)
            .returning("*");

        Map<String, Object> params = Collections.singletonMap("transfer_id", transferId);

        return query(client, sql, params)
            .map(rows -> fetchOne(externalTransferMapper, rows))
            .onFailure(e -> log.error("Failed to increment retry_count - transferId: {}", transferId, e));
    }
    
    // Normalized comment.
    
    /**
      * Normalized comment.
     */
    public Future<Wallet> getWalletByAddress(SqlClient client, String address) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("address", Op.Equal, "address")
            .andWhere("status", Op.Equal, "status")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("address", address);
        params.put("status", "ACTIVE");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows));
    }

    /**
      * Normalized comment.
     */
    public Future<Wallet> getWalletByAddressIgnoreCase(SqlClient client, String address) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("LOWER(address) = LOWER(#{address})")
            .andWhere("status", Op.Equal, "status")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        Map<String, Object> params = new HashMap<>();
        params.put("address", address);
        params.put("status", "ACTIVE");
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Wallet> getWalletByUserIdAndCurrencyId(SqlClient client, Long userId, Integer currencyId) {
        String sql = QueryBuilder
            .select("user_wallets")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("currency_id", Op.Equal, "currency_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();
        
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("currency_id", currencyId);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Void> softDeleteTransfersByUserId(SqlClient client, Long userId) {
        // Internal transfer soft delete.
        String internalSql = QueryBuilder
            .update("internal_transfers", "deleted_at")
            .whereOrEquals("sender_id", "receiver_id", "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> internalParams = new HashMap<>();
        internalParams.put("user_id", userId);
        internalParams.put("deleted_at", DateUtils.now());

        Future<Void> internalDelete = query(client, internalSql, internalParams)
            .<Void>map(rows -> null);

        // External transfer soft delete.
        String externalSql = QueryBuilder
            .update("external_transfers", "deleted_at")
            .where("user_id", Op.Equal, "user_id")
            .andWhere("deleted_at", Op.IsNull)
            .build();

        Map<String, Object> externalParams = new HashMap<>();
        externalParams.put("user_id", userId);
        externalParams.put("deleted_at", DateUtils.now());

        Future<Void> externalDelete = query(client, externalSql, externalParams)
            .<Void>map(rows -> null);

        return Future.all(internalDelete, externalDelete)
            .<Void>map(v -> {
                log.info("User transfers soft deleted - userId: {}", userId);
                return null;
            })
            .onFailure(throwable -> log.error("Failed to soft delete transfers - userId: {}", userId, throwable));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Wallet> deductBalance(SqlClient client, Long walletId, BigDecimal amount) {
        String sql = QueryBuilder
            .update("user_wallets")
            .setCustom("balance = balance - #{amount}")
            .setCustom("updated_at = #{updated_at}")
            .where("id", Op.Equal, "id")
            .andWhere("balance", Op.GreaterThanOrEqual, "amount")
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("Failed to deduct balance - walletId: {}, amount: {}", walletId, amount));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Wallet> addBalance(SqlClient client, Long walletId, BigDecimal amount) {
        String sql = QueryBuilder
            .update("user_wallets")
            .setCustom("balance = balance + #{amount}")
            .setCustom("updated_at = #{updated_at}")
            .where("id", Op.Equal, "id")
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("Failed to add balance - walletId: {}, amount: {}", walletId, amount));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Wallet> lockBalance(SqlClient client, Long walletId, BigDecimal amount) {
        String sql = QueryBuilder
            .update("user_wallets")
            .setCustom("balance = balance - #{amount}")
            .setCustom("locked_balance = locked_balance + #{amount}")
            .setCustom("updated_at = #{updated_at}")
            .where("id", Op.Equal, "id")
            .andWhere("balance", Op.GreaterThanOrEqual, "amount")
            .returning("*");

        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("Failed to lock balance - walletId: {}, amount: {}", walletId, amount));
    }
    
    /**
      * Normalized comment.
     */
    public Future<Wallet> unlockBalance(SqlClient client, Long walletId, BigDecimal amount, boolean refund) {
        String sql;
        if (refund) {
            // Refund on external transfer failure.
            sql = QueryBuilder
                .update("user_wallets")
                .setCustom("balance = balance + #{amount}")
                .setCustom("locked_balance = locked_balance - #{amount}")
                .setCustom("updated_at = #{updated_at}")
                .where("id", Op.Equal, "id")
                .returning("*");
        } else {
            // Release lock only after successful external settlement.
            sql = QueryBuilder
                .update("user_wallets")
                .setCustom("locked_balance = locked_balance - #{amount}")
                .setCustom("updated_at = #{updated_at}")
                .where("id", Op.Equal, "id")
                .returning("*");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("id", walletId);
        params.put("amount", amount);
        params.put("updated_at", DateUtils.now());

        return query(client, sql, params)
            .map(rows -> fetchOne(walletMapper, rows))
            .onFailure(e -> log.error("Failed to unlock balance - walletId: {}, amount: {}, refund: {}", walletId, amount, refund));
    }
    
    /**
      * Normalized comment.
     */
    public Future<List<InternalTransfer>> getInternalTransfersByUserIdNotDeleted(SqlClient client, Long userId, int limit, int offset) {
        return getInternalTransfersByUserId(client, userId, limit, offset);
    }
    
    /**
      * Normalized comment.
     */
    public Future<List<ExternalTransfer>> getExternalTransfersByUserIdNotDeleted(SqlClient client, Long userId, int limit, int offset) {
        return getExternalTransfersByUserId(client, userId, limit, offset);
    }
}
