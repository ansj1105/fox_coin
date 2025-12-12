package com.foxya.coin.payment;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.payment.dto.PaymentDepositRequestDto;
import com.foxya.coin.payment.dto.PaymentDepositResponseDto;
import com.foxya.coin.payment.entities.PaymentDeposit;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
public class PaymentDepositService extends BaseService {
    
    private final PaymentDepositRepository paymentDepositRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    
    // 최소 입금 금액
    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("0.000001");
    
    public PaymentDepositService(PgPool pool, PaymentDepositRepository paymentDepositRepository,
                                CurrencyRepository currencyRepository,
                                TransferRepository transferRepository) {
        super(pool);
        this.paymentDepositRepository = paymentDepositRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
    }
    
    /**
     * 결제 입금 요청
     */
    public Future<PaymentDepositResponseDto> requestPaymentDeposit(Long userId, PaymentDepositRequestDto request, String requestIp) {
        log.info("결제 입금 요청 - userId: {}, currencyCode: {}, amount: {}, depositMethod: {}, paymentAmount: {}", 
            userId, request.getCurrencyCode(), request.getAmount(), request.getDepositMethod(), request.getPaymentAmount());
        
        // 1. 유효성 검사
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_DEPOSIT_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 입금 금액은 " + MIN_DEPOSIT_AMOUNT + " 입니다."));
        }
        
        if (request.getPaymentAmount() == null || request.getPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Future.failedFuture(new BadRequestException("결제 금액을 입력해주세요."));
        }
        
        // 2. 통화 조회
        return currencyRepository.getCurrencyByCode(pool, request.getCurrencyCode())
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + request.getCurrencyCode()));
                }
                
                // 3. 결제 입금 기록 생성 (PENDING 상태)
                String depositId = UUID.randomUUID().toString();
                String orderNumber = OrderNumberUtils.generateOrderNumber();
                
                PaymentDeposit deposit = PaymentDeposit.builder()
                    .depositId(depositId)
                    .userId(userId)
                    .orderNumber(orderNumber)
                    .currencyId(currency.getId())
                    .amount(request.getAmount())
                    .depositMethod(request.getDepositMethod())
                    .paymentAmount(request.getPaymentAmount())
                    .status(PaymentDeposit.STATUS_PENDING)
                    .build();
                
                return paymentDepositRepository.createPaymentDeposit(pool, deposit)
                    .map(createdDeposit -> PaymentDepositResponseDto.builder()
                        .depositId(createdDeposit.getDepositId())
                        .orderNumber(createdDeposit.getOrderNumber())
                        .currencyCode(currency.getCode())
                        .amount(createdDeposit.getAmount())
                        .depositMethod(createdDeposit.getDepositMethod())
                        .paymentAmount(createdDeposit.getPaymentAmount())
                        .status(createdDeposit.getStatus())
                        .createdAt(createdDeposit.getCreatedAt())
                        .build());
            });
    }
    
    /**
     * 결제 입금 완료 처리 (관리자용 또는 결제 시스템 콜백)
     */
    public Future<PaymentDepositResponseDto> completePaymentDeposit(String depositId) {
        return paymentDepositRepository.getPaymentDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("결제 입금을 찾을 수 없습니다."));
                }
                
                if (!PaymentDeposit.STATUS_PENDING.equals(deposit.getStatus())) {
                    return Future.failedFuture(new BadRequestException("이미 처리된 입금입니다."));
                }
                
                return pool.withTransaction(client -> {
                    // 1. 사용자 지갑 조회 또는 생성
                    return transferRepository.getWalletByUserIdAndCurrencyId(client, deposit.getUserId(), deposit.getCurrencyId())
                        .compose(wallet -> {
                            if (wallet == null) {
                                return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                            }
                            
                            // 2. 지갑 잔액 추가
                            return transferRepository.addBalance(client, wallet.getId(), deposit.getAmount())
                                .compose(updatedWallet -> {
                                    // 3. 입금 상태 업데이트
                                    return paymentDepositRepository.completePaymentDeposit(client, depositId)
                                        .compose(completedDeposit -> 
                                            currencyRepository.getCurrencyById(client, completedDeposit.getCurrencyId())
                                                .map(currency -> PaymentDepositResponseDto.builder()
                                                    .depositId(completedDeposit.getDepositId())
                                                    .orderNumber(completedDeposit.getOrderNumber())
                                                    .currencyCode(currency.getCode())
                                                    .amount(completedDeposit.getAmount())
                                                    .depositMethod(completedDeposit.getDepositMethod())
                                                    .paymentAmount(completedDeposit.getPaymentAmount())
                                                    .status(completedDeposit.getStatus())
                                                    .createdAt(completedDeposit.getCreatedAt())
                                                    .build()));
                                });
                        });
                });
            });
    }
    
    /**
     * 결제 입금 상세 조회
     */
    public Future<PaymentDepositResponseDto> getPaymentDeposit(Long userId, String depositId) {
        return paymentDepositRepository.getPaymentDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("결제 입금을 찾을 수 없습니다."));
                }
                
                if (!deposit.getUserId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("권한이 없습니다."));
                }
                
                return currencyRepository.getCurrencyById(pool, deposit.getCurrencyId())
                    .map(currency -> PaymentDepositResponseDto.builder()
                        .depositId(deposit.getDepositId())
                        .orderNumber(deposit.getOrderNumber())
                        .currencyCode(currency.getCode())
                        .amount(deposit.getAmount())
                        .depositMethod(deposit.getDepositMethod())
                        .paymentAmount(deposit.getPaymentAmount())
                        .status(deposit.getStatus())
                        .createdAt(deposit.getCreatedAt())
                        .build());
            });
    }
}

