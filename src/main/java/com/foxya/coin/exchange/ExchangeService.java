package com.foxya.coin.exchange;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.exchange.dto.ExchangeRequestDto;
import com.foxya.coin.exchange.dto.ExchangeResponseDto;
import com.foxya.coin.exchange.entities.Exchange;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
public class ExchangeService extends BaseService {
    
    private final ExchangeRepository exchangeRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    
    // 환전 비율 (KRWT 1.0 = BLUEDIA 0.8)
    private static final BigDecimal EXCHANGE_RATE = new BigDecimal("0.8");
    // 최소 환전 금액
    private static final BigDecimal MIN_EXCHANGE_AMOUNT = new BigDecimal("1.0");
    
    public ExchangeService(PgPool pool, ExchangeRepository exchangeRepository,
                          CurrencyRepository currencyRepository,
                          TransferRepository transferRepository) {
        super(pool);
        this.exchangeRepository = exchangeRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
    }
    
    /**
     * 환전 실행 (KRWT → BLUEDIA)
     */
    public Future<ExchangeResponseDto> executeExchange(Long userId, ExchangeRequestDto request, String requestIp) {
        log.info("환전 실행 요청 - userId: {}, fromAmount: {}", userId, request.getFromAmount());
        
        // 1. 유효성 검사
        if (request.getFromAmount() == null || request.getFromAmount().compareTo(MIN_EXCHANGE_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 환전 금액은 " + MIN_EXCHANGE_AMOUNT + " 입니다."));
        }
        
        // 2. 통화 조회 (KRWT, BLUEDIA)
        return currencyRepository.getCurrencyByCode(pool, "KRWT")
            .compose(krwtCurrency -> {
                if (krwtCurrency == null) {
                    return Future.failedFuture(new NotFoundException("KRWT 통화를 찾을 수 없습니다."));
                }
                
                return currencyRepository.getCurrencyByCode(pool, "BLUEDIA")
                    .compose(blueDiamondCurrency -> {
                        if (blueDiamondCurrency == null) {
                            return Future.failedFuture(new NotFoundException("BLUEDIA 통화를 찾을 수 없습니다."));
                        }
                        
                        // 3. 사용자 지갑 조회
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, krwtCurrency.getId())
                            .compose(krwtWallet -> {
                                if (krwtWallet == null) {
                                    return Future.failedFuture(new NotFoundException("KRWT 지갑을 찾을 수 없습니다."));
                                }
                                
                                // 4. TO 금액 계산
                                BigDecimal toAmount = request.getFromAmount().multiply(EXCHANGE_RATE)
                                    .setScale(18, RoundingMode.DOWN);
                                
                                // 5. 잔액 확인
                                if (krwtWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                                    return Future.failedFuture(new BadRequestException("잔액이 부족합니다."));
                                }
                                
                                // 6. 환전 실행
                                return executeExchangeTransaction(userId, krwtCurrency, blueDiamondCurrency, 
                                    krwtWallet, request.getFromAmount(), toAmount, requestIp);
                            });
                    });
            });
    }
    
    /**
     * 환전 트랜잭션 실행
     */
    private Future<ExchangeResponseDto> executeExchangeTransaction(Long userId, Currency fromCurrency, Currency toCurrency,
                                                                   Wallet fromWallet, BigDecimal fromAmount, BigDecimal toAmount,
                                                                   String requestIp) {
        String exchangeId = UUID.randomUUID().toString();
        String orderNumber = OrderNumberUtils.generateOrderNumber();
        
        return pool.withTransaction(client -> {
            // 1. FROM 지갑 잔액 차감
            return transferRepository.deductBalance(client, fromWallet.getId(), fromAmount)
                .compose(updatedFromWallet -> {
                    if (updatedFromWallet == null) {
                        return Future.failedFuture(new BadRequestException("잔액 차감 실패"));
                    }
                    
                    // 2. TO 지갑 조회 또는 생성
                    return transferRepository.getWalletByUserIdAndCurrencyId(client, userId, toCurrency.getId())
                        .compose(toWallet -> {
                            if (toWallet == null) {
                                return Future.failedFuture(new NotFoundException("BLUEDIA 지갑을 찾을 수 없습니다."));
                            }
                            
                            // 3. TO 지갑 잔액 추가
                            return transferRepository.addBalance(client, toWallet.getId(), toAmount)
                                .compose(updatedToWallet -> {
                                    // 4. 환전 기록 생성
                                    Exchange exchange = Exchange.builder()
                                        .exchangeId(exchangeId)
                                        .userId(userId)
                                        .orderNumber(orderNumber)
                                        .fromCurrencyId(fromCurrency.getId())
                                        .toCurrencyId(toCurrency.getId())
                                        .fromAmount(fromAmount)
                                        .toAmount(toAmount)
                                        .status(Exchange.STATUS_COMPLETED)
                                        .build();
                                    
                                    return exchangeRepository.createExchange(client, exchange)
                                        .map(createdExchange -> ExchangeResponseDto.builder()
                                            .exchangeId(createdExchange.getExchangeId())
                                            .orderNumber(createdExchange.getOrderNumber())
                                            .fromCurrencyCode(fromCurrency.getCode())
                                            .toCurrencyCode(toCurrency.getCode())
                                            .fromAmount(createdExchange.getFromAmount())
                                            .toAmount(createdExchange.getToAmount())
                                            .status(createdExchange.getStatus())
                                            .createdAt(createdExchange.getCreatedAt())
                                            .build());
                                });
                        });
                });
        });
    }
    
    /**
     * 환전 상세 조회
     */
    public Future<ExchangeResponseDto> getExchange(Long userId, String exchangeId) {
        return exchangeRepository.getExchangeByExchangeId(pool, exchangeId)
            .compose(exchange -> {
                if (exchange == null) {
                    return Future.failedFuture(new NotFoundException("환전을 찾을 수 없습니다."));
                }
                
                if (!exchange.getUserId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("권한이 없습니다."));
                }
                
                return currencyRepository.getCurrencyById(pool, exchange.getFromCurrencyId())
                    .compose(fromCurrency -> 
                        currencyRepository.getCurrencyById(pool, exchange.getToCurrencyId())
                            .map(toCurrency -> ExchangeResponseDto.builder()
                                .exchangeId(exchange.getExchangeId())
                                .orderNumber(exchange.getOrderNumber())
                                .fromCurrencyCode(fromCurrency.getCode())
                                .toCurrencyCode(toCurrency.getCode())
                                .fromAmount(exchange.getFromAmount())
                                .toAmount(exchange.getToAmount())
                                .status(exchange.getStatus())
                                .createdAt(exchange.getCreatedAt())
                                .build()));
            });
    }
}

