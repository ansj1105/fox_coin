package com.foxya.coin.exchange;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.exchange.dto.ExchangeInfoDto;
import com.foxya.coin.exchange.dto.ExchangeQuoteDto;
import com.foxya.coin.exchange.dto.ExchangeRequestDto;
import com.foxya.coin.exchange.dto.ExchangeResponseDto;
import com.foxya.coin.exchange.entities.Exchange;
import com.foxya.coin.exchange.entities.ExchangeSetting;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
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
    private final NotificationService notificationService;

    private static final String EXCHANGE_CHAIN = "INTERNAL";

    public ExchangeService(PgPool pool, ExchangeRepository exchangeRepository,
                          CurrencyRepository currencyRepository,
                          TransferRepository transferRepository,
                          UserRepository userRepository) {
        this(pool, exchangeRepository, currencyRepository, transferRepository, userRepository, null);
    }

    public ExchangeService(PgPool pool, ExchangeRepository exchangeRepository,
                          CurrencyRepository currencyRepository,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          NotificationService notificationService) {
        super(pool);
        this.exchangeRepository = exchangeRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * ?섏쟾 ?ㅽ뻾 (KORI -> F_COIN)
     */
    public Future<ExchangeResponseDto> executeExchange(Long userId, ExchangeRequestDto request, String requestIp) {
        log.info("?섏쟾 ?ㅽ뻾 ?붿껌 - userId: {}, fromAmount: {}", userId, request.getFromAmount());

        if (request.getTransactionPassword() == null || !request.getTransactionPassword().matches("^\\d{6}$")) {
            return Future.failedFuture(new BadRequestException("嫄곕옒 鍮꾨?踰덊샇???レ옄 6?먮━?ъ빞 ?⑸땲??"));
        }

        return loadActiveSetting()
            .compose(setting -> {
                if (request.getFromAmount() == null || request.getFromAmount().compareTo(setting.getMinExchangeAmount()) < 0) {
                    return Future.failedFuture(new BadRequestException("理쒖냼 ?섏쟾 湲덉븸? " + setting.getMinExchangeAmount() + " ?낅땲??"));
                }
                return userRepository.getUserById(pool, userId)
                    .compose(user -> {
                        if (user == null) {
                            return Future.failedFuture(new NotFoundException("?ъ슜?먮? 李얠쓣 ???놁뒿?덈떎."));
                        }
                        if (user.getTransactionPasswordHash() == null || user.getTransactionPasswordHash().isBlank()) {
                            return Future.failedFuture(new BadRequestException("嫄곕옒 鍮꾨?踰덊샇瑜??ㅼ젙?댁＜?몄슂."));
                        }
                        if (!matchesTransactionPassword(request.getTransactionPassword(), user.getTransactionPasswordHash())) {
                            return Future.failedFuture(new BadRequestException("嫄곕옒 鍮꾨?踰덊샇媛 ?щ컮瑜댁? ?딆뒿?덈떎."));
                        }

                        return currencyRepository.getCurrencyByCodeAndChain(pool, setting.getFromCurrencyCode(), EXCHANGE_CHAIN)
                            .compose(fromCurrency -> {
                                if (fromCurrency == null) {
                                    return Future.failedFuture(new NotFoundException(setting.getFromCurrencyCode() + " ?듯솕瑜?李얠쓣 ???놁뒿?덈떎."));
                                }

                                return currencyRepository.getCurrencyByCodeAndChain(pool, setting.getToCurrencyCode(), EXCHANGE_CHAIN)
                                    .compose(toCurrency -> {
                                        if (toCurrency == null) {
                                            return Future.failedFuture(new NotFoundException(setting.getToCurrencyCode() + " ?듯솕瑜?李얠쓣 ???놁뒿?덈떎."));
                                        }

                                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, fromCurrency.getId())
                                            .compose(fromWallet -> {
                                                if (fromWallet == null) {
                                                    return Future.failedFuture(new NotFoundException(setting.getFromCurrencyCode() + " 吏媛묒쓣 李얠쓣 ???놁뒿?덈떎."));
                                                }

                                                BigDecimal feeAmount = request.getFromAmount()
                                                    .multiply(setting.getFee())
                                                    .setScale(18, RoundingMode.DOWN);
                                                BigDecimal netFromAmount = request.getFromAmount().subtract(feeAmount);
                                                BigDecimal toAmount = netFromAmount
                                                    .multiply(setting.getExchangeRate())
                                                    .setScale(18, RoundingMode.DOWN);

                                                if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
                                                    return Future.failedFuture(new BadRequestException("?섏쟾 湲덉븸???좏슚?섏? ?딆뒿?덈떎."));
                                                }

                                                if (fromWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                                                    return Future.failedFuture(new BadRequestException("?붿븸??遺議깊빀?덈떎."));
                                                }

                                                return executeExchangeTransaction(userId, fromCurrency, toCurrency,
                                                    fromWallet, request.getFromAmount(), toAmount, requestIp)
                                                    .compose(this::createExchangeCompletedNotification);
                                            });
                                    });
                            });
                    });
            });
    }

    /**
     * ?섏쟾 ?몃옖??뀡 ?ㅽ뻾
     */
    private Future<ExchangeResponseDto> executeExchangeTransaction(Long userId, Currency fromCurrency, Currency toCurrency,
                                                                   Wallet fromWallet, BigDecimal fromAmount, BigDecimal toAmount,
                                                                   String requestIp) {
        String exchangeId = UUID.randomUUID().toString();
        String orderNumber = OrderNumberUtils.generateOrderNumber();

        return pool.withTransaction(client ->
            transferRepository.deductBalance(client, fromWallet.getId(), fromAmount)
                .compose(updatedFromWallet -> {
                    if (updatedFromWallet == null) {
                        return Future.failedFuture(new BadRequestException("?붿븸 李④컧 ?ㅽ뙣"));
                    }

                    return transferRepository.getWalletByUserIdAndCurrencyId(client, userId, toCurrency.getId())
                        .compose(toWallet -> {
                            if (toWallet == null) {
                                return Future.failedFuture(new NotFoundException("?섏쟾 諛쏆쓣 吏媛묒쓣 李얠쓣 ???놁뒿?덈떎."));
                            }

                            return transferRepository.addBalance(client, toWallet.getId(), toAmount)
                                .compose(updatedToWallet -> {
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
                })
        );
    }

    private Future<ExchangeResponseDto> createExchangeCompletedNotification(ExchangeResponseDto responseDto) {
        if (notificationService == null || responseDto == null || responseDto.getExchangeId() == null) {
            return Future.succeededFuture(responseDto);
        }

        return exchangeRepository.getExchangeByExchangeId(pool, responseDto.getExchangeId())
            .compose(exchange -> {
                if (exchange == null || exchange.getUserId() == null) {
                    return Future.succeededFuture();
                }

                String title = "\uD658\uC804 \uC644\uB8CC";
                String message = responseDto.getFromAmount() + " " + responseDto.getFromCurrencyCode() + " \uD658\uC804\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
                JsonObject metadata = new JsonObject()
                    .put("exchangeId", responseDto.getExchangeId())
                    .put("orderNumber", responseDto.getOrderNumber())
                    .put("fromCurrencyCode", responseDto.getFromCurrencyCode())
                    .put("toCurrencyCode", responseDto.getToCurrencyCode())
                    .put("fromAmount", responseDto.getFromAmount() != null ? responseDto.getFromAmount().toPlainString() : null)
                    .put("toAmount", responseDto.getToAmount() != null ? responseDto.getToAmount().toPlainString() : null)
                    .put("status", responseDto.getStatus());

                return notificationService.createNotificationIfAbsentByRelatedId(
                    exchange.getUserId(),
                    NotificationType.EXCHANGE_SUCCESS,
                    title,
                    message,
                    exchange.getId(),
                    metadata.encode()
                ).mapEmpty();
            })
            .map(v -> responseDto)
            .recover(err -> {
                log.warn("?섏쟾 ?꾨즺 ?뚮┝ ?앹꽦 ?ㅽ뙣(臾댁떆): exchangeId={}", responseDto.getExchangeId(), err);
                return Future.succeededFuture(responseDto);
            });
    }

    /**
     * ?섏쟾 ?곸꽭 議고쉶
     */
    public Future<ExchangeResponseDto> getExchange(Long userId, String exchangeId) {
        return exchangeRepository.getExchangeByExchangeId(pool, exchangeId)
            .compose(exchange -> {
                if (exchange == null) {
                    return Future.failedFuture(new NotFoundException("?섏쟾??李얠쓣 ???놁뒿?덈떎."));
                }

                if (!exchange.getUserId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("沅뚰븳???놁뒿?덈떎."));
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
     * ?섏쟾 ?뺣낫 議고쉶
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
     * ?섏쟾 ?덉긽 ?섎웾 議고쉶
     */
    public Future<ExchangeQuoteDto> getExchangeQuote(BigDecimal fromAmount) {
        return loadActiveSetting()
            .compose(setting -> {
                if (fromAmount == null || fromAmount.compareTo(setting.getMinExchangeAmount()) < 0) {
                    return Future.failedFuture(new BadRequestException("理쒖냼 ?섏쟾 湲덉븸? " + setting.getMinExchangeAmount() + " ?낅땲??"));
                }
                BigDecimal feeAmount = fromAmount
                    .multiply(setting.getFee())
                    .setScale(18, RoundingMode.DOWN);
                BigDecimal netFromAmount = fromAmount.subtract(feeAmount);
                BigDecimal toAmount = netFromAmount
                    .multiply(setting.getExchangeRate())
                    .setScale(18, RoundingMode.DOWN);
                if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    return Future.failedFuture(new BadRequestException("?섏쟾 湲덉븸???좏슚?섏? ?딆뒿?덈떎."));
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
                    return Future.failedFuture(new BadRequestException("?섏쟾 ?ㅼ젙???놁뒿?덈떎."));
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
