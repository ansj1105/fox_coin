package com.foxya.coin.exchange;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.exchange.dto.ExchangeRequestDto;
import com.foxya.coin.exchange.dto.ExchangeResponseDto;
import com.foxya.coin.exchange.dto.ExchangeInfoDto;
import com.foxya.coin.exchange.dto.ExchangeQuoteDto;
import com.foxya.coin.exchange.entities.Exchange;
import com.foxya.coin.exchange.entities.ExchangeSetting;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
public class ExchangeService extends BaseService {
    
    private final ExchangeRepository exchangeRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    
    private static final String EXCHANGE_CHAIN = "INTERNAL";
    
    public ExchangeService(PgPool pool, ExchangeRepository exchangeRepository,
                          CurrencyRepository currencyRepository,
                          TransferRepository transferRepository,
                          UserRepository userRepository) {
        super(pool);
        this.exchangeRepository = exchangeRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * 환전 실행 (KORI → F_COIN)
     */
    public Future<ExchangeResponseDto> executeExchange(Long userId, ExchangeRequestDto request, String requestIp) {
        log.info("환전 실행 요청 - userId: {}, fromAmount: {}", userId, request.getFromAmount());

        if (request.getTransactionPassword() == null || !request.getTransactionPassword().matches("^\\d{6}$")) {
            return Future.failedFuture(new BadRequestException("거래 비밀번호는 숫자 6자리여야 합니다."));
        }

        return loadActiveSetting()
            .compose(setting -> {
                if (request.getFromAmount() == null || request.getFromAmount().compareTo(setting.getMinExchangeAmount()) < 0) {
                    return Future.failedFuture(new BadRequestException("최소 환전 금액은 " + setting.getMinExchangeAmount() + " 입니다."));
                }
                return userRepository.getUserById(pool, userId)
                    .compose(user -> {
                        if (user == null) {
                            return Future.failedFuture(new NotFoundException("사용자를 찾을 수 없습니다."));
                        }
                        if (user.getTransactionPasswordHash() == null || user.getTransactionPasswordHash().isBlank()) {
                            return Future.failedFuture(new BadRequestException("거래 비밀번호를 설정해주세요."));
                        }
                        if (!matchesTransactionPassword(request.getTransactionPassword(), user.getTransactionPasswordHash())) {
                            return Future.failedFuture(new BadRequestException("거래 비밀번호가 올바르지 않습니다."));
                        }
                        // 2. 통화 조회 (설정 기준) - 환전은 항상 INTERNAL 체인 사용
                        return currencyRepository.getCurrencyByCodeAndChain(pool, setting.getFromCurrencyCode(), EXCHANGE_CHAIN)
                            .compose(fromCurrency -> {
                                if (fromCurrency == null) {
                                    return Future.failedFuture(new NotFoundException(setting.getFromCurrencyCode() + " 통화를 찾을 수 없습니다."));
                                }

                                return currencyRepository.getCurrencyByCodeAndChain(pool, setting.getToCurrencyCode(), EXCHANGE_CHAIN)
                                    .compose(toCurrency -> {
                                        if (toCurrency == null) {
                                            return Future.failedFuture(new NotFoundException(setting.getToCurrencyCode() + " 통화를 찾을 수 없습니다."));
                                        }

                                        // 3. 사용자 지갑 조회
                                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, fromCurrency.getId())
                                            .compose(fromWallet -> {
                                                if (fromWallet == null) {
                                                    return Future.failedFuture(new NotFoundException(setting.getFromCurrencyCode() + " 지갑을 찾을 수 없습니다."));
                                                }

                                                // 4. TO 금액 계산
                                                BigDecimal feeAmount = request.getFromAmount()
                                                    .multiply(setting.getFee())
                                                    .setScale(18, RoundingMode.DOWN);
                                                BigDecimal netFromAmount = request.getFromAmount().subtract(feeAmount);
                                                BigDecimal toAmount = netFromAmount
                                                    .multiply(setting.getExchangeRate())
                                                    .setScale(18, RoundingMode.DOWN);

                                                if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
                                                    return Future.failedFuture(new BadRequestException("환전 금액이 유효하지 않습니다."));
                                                }

                                                // 5. 잔액 확인
                                                if (fromWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                                                    return Future.failedFuture(new BadRequestException("잔액이 부족합니다."));
                                                }

                                                // 6. 환전 실행
                                                return executeExchangeTransaction(userId, fromCurrency, toCurrency,
                                                    fromWallet, request.getFromAmount(), toAmount, requestIp);
                                            });
                                    });
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
                                return Future.failedFuture(new NotFoundException("환전 받을 지갑을 찾을 수 없습니다."));
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
    
    /**
     * 환전 정보 조회
     */
    public Future<ExchangeInfoDto> getExchangeInfo(String currencyCode) {
        return loadActiveSetting()
            .map(setting -> ExchangeInfoDto.builder()
                .exchangeRate(setting.getExchangeRate())
                .feeRate(setting.getFee())
                .minExchangeAmount(setting.getMinExchangeAmount())
                .fromCurrencyCode(setting.getFromCurrencyCode())
                .toCurrencyCode(setting.getToCurrencyCode())
                .note(setting.getNote())
                .build());
    }

    /**
     * 환전 예상 수량 조회
     */
    public Future<ExchangeQuoteDto> getExchangeQuote(BigDecimal fromAmount) {
        return loadActiveSetting()
            .compose(setting -> {
                if (fromAmount == null || fromAmount.compareTo(setting.getMinExchangeAmount()) < 0) {
                    return Future.failedFuture(new BadRequestException("최소 환전 금액은 " + setting.getMinExchangeAmount() + " 입니다."));
                }
                BigDecimal feeAmount = fromAmount
                    .multiply(setting.getFee())
                    .setScale(18, RoundingMode.DOWN);
                BigDecimal netFromAmount = fromAmount.subtract(feeAmount);
                BigDecimal toAmount = netFromAmount
                    .multiply(setting.getExchangeRate())
                    .setScale(18, RoundingMode.DOWN);
                if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    return Future.failedFuture(new BadRequestException("환전 금액이 유효하지 않습니다."));
                }
                return Future.succeededFuture(ExchangeQuoteDto.builder()
                    .fromCurrencyCode(setting.getFromCurrencyCode())
                    .toCurrencyCode(setting.getToCurrencyCode())
                    .fromAmount(fromAmount)
                    .exchangeRate(setting.getExchangeRate())
                    .feeRate(setting.getFee())
                    .feeAmount(feeAmount)
                    .toAmount(toAmount)
                    .build());
            });
    }

    private Future<ExchangeSetting> loadActiveSetting() {
        return exchangeRepository.getActiveExchangeSetting(pool)
            .compose(setting -> {
                if (setting == null) {
                    return Future.failedFuture(new BadRequestException("환전 설정이 없습니다."));
                }
                return Future.succeededFuture(setting);
            });
    }

    private boolean matchesTransactionPassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        String normalizedHash = hashedPassword;
        if (normalizedHash.startsWith("$2y$")) {
            normalizedHash = "$2a$" + normalizedHash.substring(4);
        }
        try {
            return BCrypt.checkpw(rawPassword, normalizedHash);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transaction password hash format.");
            return false;
        }
    }
}
