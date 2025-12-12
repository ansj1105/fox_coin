package com.foxya.coin.payment;

import com.foxya.coin.common.BaseRepository;
import com.foxya.coin.common.database.RowMapper;
import com.foxya.coin.payment.entities.PaymentDeposit;
import com.foxya.coin.utils.BaseQueryBuilder.Op;
import com.foxya.coin.utils.QueryBuilder;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PaymentDepositRepository extends BaseRepository {
    
    private final RowMapper<PaymentDeposit> paymentDepositMapper = row -> PaymentDeposit.builder()
        .id(getLongColumnValue(row, "id"))
        .depositId(getStringColumnValue(row, "deposit_id"))
        .userId(getLongColumnValue(row, "user_id"))
        .orderNumber(getStringColumnValue(row, "order_number"))
        .currencyId(getIntegerColumnValue(row, "currency_id"))
        .amount(getBigDecimalColumnValue(row, "amount"))
        .depositMethod(getStringColumnValue(row, "deposit_method"))
        .paymentAmount(getBigDecimalColumnValue(row, "payment_amount"))
        .status(getStringColumnValue(row, "status"))
        .createdAt(getLocalDateTimeColumnValue(row, "created_at"))
        .completedAt(getLocalDateTimeColumnValue(row, "completed_at"))
        .failedAt(getLocalDateTimeColumnValue(row, "failed_at"))
        .errorMessage(getStringColumnValue(row, "error_message"))
        .build();
    
    /**
     * 결제 입금 생성
     */
    public Future<PaymentDeposit> createPaymentDeposit(SqlClient client, PaymentDeposit deposit) {
        Map<String, Object> params = new HashMap<>();
        params.put("deposit_id", deposit.getDepositId());
        params.put("user_id", deposit.getUserId());
        params.put("order_number", deposit.getOrderNumber());
        params.put("currency_id", deposit.getCurrencyId());
        params.put("amount", deposit.getAmount());
        params.put("deposit_method", deposit.getDepositMethod());
        params.put("payment_amount", deposit.getPaymentAmount());
        params.put("status", deposit.getStatus());
        
        String sql = QueryBuilder.insert("payment_deposits", params, "*");
        
        return query(client, sql, params)
            .map(rows -> fetchOne(paymentDepositMapper, rows))
            .onFailure(e -> log.error("결제 입금 생성 실패: {}", e.getMessage()));
    }
    
    /**
     * 결제 입금 ID로 조회
     */
    public Future<PaymentDeposit> getPaymentDepositByDepositId(SqlClient client, String depositId) {
        String sql = QueryBuilder
            .select("payment_deposits")
            .where("deposit_id", Op.Equal, "deposit_id")
            .build();
        
        return query(client, sql, Collections.singletonMap("deposit_id", depositId))
            .map(rows -> fetchOne(paymentDepositMapper, rows))
            .onFailure(e -> log.error("결제 입금 조회 실패 - depositId: {}", depositId));
    }
    
    /**
     * 결제 입금 상태 업데이트 (완료)
     */
    public Future<PaymentDeposit> completePaymentDeposit(SqlClient client, String depositId) {
        String sql = QueryBuilder
            .update("payment_deposits", "status", "completed_at")
            .where("deposit_id", Op.Equal, "deposit_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("deposit_id", depositId);
        params.put("status", PaymentDeposit.STATUS_COMPLETED);
        params.put("completed_at", com.foxya.coin.common.utils.DateUtils.now());
        
        return query(client, sql, params)
            .map(rows -> fetchOne(paymentDepositMapper, rows))
            .onFailure(e -> log.error("결제 입금 완료 처리 실패 - depositId: {}", depositId));
    }
    
    /**
     * 결제 입금 상태 업데이트 (실패)
     */
    public Future<PaymentDeposit> failPaymentDeposit(SqlClient client, String depositId, String errorMessage) {
        String sql = QueryBuilder
            .update("payment_deposits", "status", "failed_at", "error_message")
            .where("deposit_id", Op.Equal, "deposit_id")
            .returning("*");
        
        Map<String, Object> params = new HashMap<>();
        params.put("deposit_id", depositId);
        params.put("status", PaymentDeposit.STATUS_FAILED);
        params.put("failed_at", com.foxya.coin.common.utils.DateUtils.now());
        params.put("error_message", errorMessage);
        
        return query(client, sql, params)
            .map(rows -> fetchOne(paymentDepositMapper, rows))
            .onFailure(e -> log.error("결제 입금 실패 처리 실패 - depositId: {}", depositId));
    }
}

