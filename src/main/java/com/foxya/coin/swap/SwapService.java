package com.foxya.coin.swap;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.CurrencyService;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.swap.dto.SwapCurrenciesDto;
import com.foxya.coin.swap.dto.SwapInfoDto;
import com.foxya.coin.swap.dto.SwapQuoteDto;
import com.foxya.coin.swap.dto.SwapRequestDto;
import com.foxya.coin.swap.dto.SwapResponseDto;
import com.foxya.coin.swap.entities.Swap;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class SwapService extends BaseService {

    private final SwapRepository swapRepository;
    private final CurrencyRepository currencyRepository;
    private final CurrencyService currencyService;
    private final TransferRepository transferRepository;
    private final NotificationService notificationService;

    private static final BigDecimal SWAP_FEE_RATE = new BigDecimal("0.002");
    private static final BigDecimal SWAP_SPREAD_RATE = new BigDecimal("0.003");
    private static final BigDecimal MIN_SWAP_AMOUNT = new BigDecimal("0.000001");
    private static final BigDecimal MIN_SWAP_AMOUNT_KRWT = new BigDecimal("1000.0");

    private static final BigDecimal RATE_ETH = new BigDecimal("5000000.0");
    private static final BigDecimal RATE_USDT = new BigDecimal("1300.0");
    private static final BigDecimal RATE_KRWT = BigDecimal.ONE;

    private static final String INTERNAL_CHAIN = "INTERNAL";

    private record ResolvedSwapWallet(Currency currency, Wallet wallet) {}

    public SwapService(PgPool pool,
                       SwapRepository swapRepository,
                       CurrencyRepository currencyRepository,
                       CurrencyService currencyService,
                       TransferRepository transferRepository) {
        this(pool, swapRepository, currencyRepository, currencyService, transferRepository, null);
    }

    public SwapService(PgPool pool,
                       SwapRepository swapRepository,
                       CurrencyRepository currencyRepository,
                       CurrencyService currencyService,
                       TransferRepository transferRepository,
                       NotificationService notificationService) {
        super(pool);
        this.swapRepository = swapRepository;
        this.currencyRepository = currencyRepository;
        this.currencyService = currencyService;
        this.transferRepository = transferRepository;
        this.notificationService = notificationService;
    }

    /**
     * Execute swap with internal-wallet-first resolution.
     */
    public Future<SwapResponseDto> executeSwap(Long userId, SwapRequestDto request, String requestIp) {
        log.info("Swap request - userId: {}, fromCurrency: {}, toCurrency: {}, fromAmount: {}, network: {}",
            userId, request.getFromCurrencyCode(), request.getToCurrencyCode(), request.getFromAmount(), request.getNetwork());

        if (request.getFromAmount() == null || request.getFromAmount().compareTo(MIN_SWAP_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("Minimum swap amount is " + MIN_SWAP_AMOUNT));
        }

        if (request.getFromCurrencyCode() == null || request.getToCurrencyCode() == null) {
            return Future.failedFuture(new BadRequestException("Currency code is required."));
        }

        if (request.getFromCurrencyCode().equals(request.getToCurrencyCode())) {
            return Future.failedFuture(new BadRequestException("Cannot swap to the same currency."));
        }

        return resolvePreferredSwapWallet(userId, request.getFromCurrencyCode(), request.getNetwork(), "FROM")
            .compose(fromResolved ->
                resolvePreferredSwapWallet(userId, request.getToCurrencyCode(), request.getNetwork(), "TO")
                    .compose(toResolved -> {
                        Currency fromCurrency = fromResolved.currency();
                        Currency toCurrency = toResolved.currency();
                        Wallet fromWallet = fromResolved.wallet();

                        BigDecimal exchangeRate = currencyService.getExchangeRate(request.getFromCurrencyCode(), request.getToCurrencyCode());
                        BigDecimal feeAmount = request.getFromAmount().multiply(SWAP_FEE_RATE).setScale(18, RoundingMode.DOWN);
                        BigDecimal spreadAmount = request.getFromAmount().multiply(exchangeRate).multiply(SWAP_SPREAD_RATE).setScale(18, RoundingMode.DOWN);
                        BigDecimal toAmount = request.getFromAmount().multiply(exchangeRate)
                            .subtract(feeAmount)
                            .subtract(spreadAmount)
                            .setScale(18, RoundingMode.DOWN);

                        if (fromWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                            return Future.failedFuture(new BadRequestException("Insufficient balance."));
                        }

                        return executeSwapTransaction(
                            userId,
                            fromCurrency,
                            toCurrency,
                            fromWallet,
                            request.getFromAmount(),
                            toAmount,
                            request.getNetwork(),
                            requestIp
                        ).compose(this::createSwapCompletedNotification);
                    }));
    }

    private Future<ResolvedSwapWallet> resolvePreferredSwapWallet(Long userId, String currencyCode, String network, String direction) {
        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, network)
            .compose(externalCurrency -> {
                if (externalCurrency == null) {
                    return Future.failedFuture(new NotFoundException(direction + " currency not found: " + currencyCode));
                }

                return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, externalCurrency.getId())
                    .compose(externalWallet ->
                        currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, INTERNAL_CHAIN)
                            .compose(internalCurrency -> {
                                if (internalCurrency == null) {
                                    if (externalWallet == null) {
                                        return Future.failedFuture(new NotFoundException(direction + " wallet not found."));
                                    }
                                    return Future.succeededFuture(new ResolvedSwapWallet(externalCurrency, externalWallet));
                                }

                                return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, internalCurrency.getId())
                                    .compose(internalWallet -> {
                                        if (internalWallet != null) {
                                            return Future.succeededFuture(new ResolvedSwapWallet(internalCurrency, internalWallet));
                                        }
                                        if (externalWallet != null) {
                                            return Future.succeededFuture(new ResolvedSwapWallet(externalCurrency, externalWallet));
                                        }
                                        return Future.failedFuture(new NotFoundException(direction + " wallet not found."));
                                    });
                            }));
            });
    }

    private Future<SwapResponseDto> executeSwapTransaction(Long userId,
                                                           Currency fromCurrency,
                                                           Currency toCurrency,
                                                           Wallet fromWallet,
                                                           BigDecimal fromAmount,
                                                           BigDecimal toAmount,
                                                           String network,
                                                           String requestIp) {
        String swapId = UUID.randomUUID().toString();
        String orderNumber = OrderNumberUtils.generateOrderNumber();

        return pool.withTransaction(client ->
            transferRepository.deductBalance(client, fromWallet.getId(), fromAmount)
                .compose(updatedFromWallet -> {
                    if (updatedFromWallet == null) {
                        return Future.failedFuture(new BadRequestException("Failed to deduct balance."));
                    }

                    return transferRepository.getWalletByUserIdAndCurrencyId(client, userId, toCurrency.getId())
                        .compose(toWallet -> {
                            if (toWallet == null) {
                                return Future.failedFuture(new NotFoundException("Destination wallet not found."));
                            }

                            return transferRepository.addBalance(client, toWallet.getId(), toAmount)
                                .compose(updatedToWallet -> {
                                    Swap swap = Swap.builder()
                                        .swapId(swapId)
                                        .userId(userId)
                                        .orderNumber(orderNumber)
                                        .fromCurrencyId(fromCurrency.getId())
                                        .toCurrencyId(toCurrency.getId())
                                        .fromAmount(fromAmount)
                                        .toAmount(toAmount)
                                        .network(network)
                                        .status(Swap.STATUS_COMPLETED)
                                        .build();

                                    return swapRepository.createSwap(client, swap)
                                        .map(createdSwap -> SwapResponseDto.builder()
                                            .swapId(createdSwap.getSwapId())
                                            .orderNumber(createdSwap.getOrderNumber())
                                            .fromCurrencyCode(fromCurrency.getCode())
                                            .toCurrencyCode(toCurrency.getCode())
                                            .fromAmount(createdSwap.getFromAmount())
                                            .toAmount(createdSwap.getToAmount())
                                            .network(createdSwap.getNetwork())
                                            .status(createdSwap.getStatus())
                                            .createdAt(createdSwap.getCreatedAt())
                                            .build());
                                });
                        });
                })
        );
    }

    private Future<SwapResponseDto> createSwapCompletedNotification(SwapResponseDto responseDto) {
        if (notificationService == null || responseDto == null || responseDto.getSwapId() == null) {
            return Future.succeededFuture(responseDto);
        }

        return swapRepository.getSwapBySwapId(pool, responseDto.getSwapId())
            .compose(swap -> {
                if (swap == null || swap.getUserId() == null) {
                    return Future.succeededFuture();
                }

                String title = "\uC2A4\uC649 \uC644\uB8CC";
                String message = responseDto.getFromAmount() + " " + responseDto.getFromCurrencyCode() + " \uC2A4\uC649\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
                JsonObject metadata = new JsonObject()
                    .put("swapId", responseDto.getSwapId())
                    .put("orderNumber", responseDto.getOrderNumber())
                    .put("fromCurrencyCode", responseDto.getFromCurrencyCode())
                    .put("toCurrencyCode", responseDto.getToCurrencyCode())
                    .put("fromAmount", responseDto.getFromAmount() != null ? responseDto.getFromAmount().toPlainString() : null)
                    .put("toAmount", responseDto.getToAmount() != null ? responseDto.getToAmount().toPlainString() : null)
                    .put("network", responseDto.getNetwork())
                    .put("status", responseDto.getStatus());

                return notificationService.createNotificationIfAbsentByRelatedId(
                    swap.getUserId(),
                    NotificationType.SWAP_SUCCESS,
                    title,
                    message,
                    swap.getId(),
                    metadata.encode()
                ).mapEmpty();
            })
            .map(v -> responseDto)
            .recover(err -> {
                log.warn("?ㅼ솑 ?꾨즺 ?뚮┝ ?앹꽦 ?ㅽ뙣(臾댁떆): swapId={}", responseDto.getSwapId(), err);
                return Future.succeededFuture(responseDto);
            });
    }

    /**
     * Get swap details.
     */
    public Future<SwapResponseDto> getSwap(Long userId, String swapId) {
        return swapRepository.getSwapBySwapId(pool, swapId)
            .compose(swap -> {
                if (swap == null) {
                    return Future.failedFuture(new NotFoundException("Swap not found."));
                }

                if (!swap.getUserId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("No permission."));
                }

                return currencyRepository.getCurrencyById(pool, swap.getFromCurrencyId())
                    .compose(fromCurrency ->
                        currencyRepository.getCurrencyById(pool, swap.getToCurrencyId())
                            .map(toCurrency -> SwapResponseDto.builder()
                                .swapId(swap.getSwapId())
                                .orderNumber(swap.getOrderNumber())
                                .fromCurrencyCode(fromCurrency.getCode())
                                .toCurrencyCode(toCurrency.getCode())
                                .fromAmount(swap.getFromAmount())
                                .toAmount(swap.getToAmount())
                                .network(swap.getNetwork())
                                .status(swap.getStatus())
                                .createdAt(swap.getCreatedAt())
                                .build()));
            });
    }

    /**
     * Get swap quote.
     */
    public Future<SwapQuoteDto> getSwapQuote(String fromCurrencyCode, String toCurrencyCode,
                                             BigDecimal fromAmount, String network) {
        return currencyRepository.getCurrencyByCodeAndChain(pool, fromCurrencyCode, network)
            .compose(fromCurrency -> {
                if (fromCurrency == null) {
                    return Future.failedFuture(new NotFoundException("FROM currency not found: " + fromCurrencyCode));
                }

                return currencyRepository.getCurrencyByCodeAndChain(pool, toCurrencyCode, network)
                    .map(toCurrency -> {
                        if (toCurrency == null) {
                            throw new NotFoundException("TO currency not found: " + toCurrencyCode);
                        }

                        BigDecimal exchangeRate = currencyService.getExchangeRate(fromCurrencyCode, toCurrencyCode);
                        BigDecimal fee = SWAP_FEE_RATE;
                        BigDecimal feeAmount = fromAmount.multiply(fee).setScale(18, RoundingMode.DOWN);
                        BigDecimal spread = SWAP_SPREAD_RATE;
                        BigDecimal spreadAmount = fromAmount.multiply(exchangeRate).multiply(spread).setScale(18, RoundingMode.DOWN);
                        BigDecimal toAmount = fromAmount.multiply(exchangeRate)
                            .subtract(feeAmount)
                            .subtract(spreadAmount)
                            .setScale(18, RoundingMode.DOWN);

                        return SwapQuoteDto.builder()
                            .fromCurrencyCode(fromCurrencyCode)
                            .toCurrencyCode(toCurrencyCode)
                            .fromAmount(fromAmount)
                            .exchangeRate(exchangeRate)
                            .fee(fee)
                            .feeAmount(feeAmount)
                            .spread(spread)
                            .spreadAmount(spreadAmount)
                            .toAmount(toAmount)
                            .network(network)
                            .build();
                    });
            });
    }

    /**
     * Get list of swappable currencies.
     */
    public Future<SwapCurrenciesDto> getSwapCurrencies() {
        return currencyRepository.getAllActiveCurrencies(pool)
            .map(currencies -> {
                List<SwapCurrenciesDto.CurrencyInfo> currencyInfos = currencies.stream()
                    .filter(c -> !INTERNAL_CHAIN.equals(c.getChain()))
                    .map(c -> SwapCurrenciesDto.CurrencyInfo.builder()
                        .code(c.getCode())
                        .name(c.getName())
                        .symbol(c.getCode())
                        .network(c.getChain())
                        .decimals(18)
                        .minSwapAmount("KRWT".equals(c.getCode()) ? MIN_SWAP_AMOUNT_KRWT : MIN_SWAP_AMOUNT)
                        .build())
                    .collect(Collectors.toList());

                return SwapCurrenciesDto.builder()
                    .currencies(currencyInfos)
                    .build();
            });
    }

    /**
     * Get static swap configuration.
     */
    public Future<SwapInfoDto> getSwapInfo(String currencyCode) {
        Map<String, BigDecimal> minSwapAmount = new HashMap<>();
        if (currencyCode != null && "KRWT".equals(currencyCode)) {
            minSwapAmount.put("KRWT", MIN_SWAP_AMOUNT_KRWT);
        }

        return Future.succeededFuture(SwapInfoDto.builder()
            .fee(SWAP_FEE_RATE)
            .spread(SWAP_SPREAD_RATE)
            .minSwapAmount(minSwapAmount.isEmpty() ? null : minSwapAmount)
            .priceSource("Oracle")
            .note("Realtime price source: Oracle")
            .build());
    }
}
