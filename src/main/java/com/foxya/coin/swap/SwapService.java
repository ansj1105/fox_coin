package com.foxya.coin.swap;

import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.CurrencyService;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationI18nUtils;
import com.foxya.coin.swap.dto.SwapCurrenciesDto;
import com.foxya.coin.swap.dto.SwapInfoDto;
import com.foxya.coin.swap.dto.SwapQuoteDto;
import com.foxya.coin.swap.dto.SwapRequestDto;
import com.foxya.coin.swap.dto.SwapResponseDto;
import com.foxya.coin.swap.entities.Swap;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.CompositeFuture;
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
    private final AppConfigRepository appConfigRepository;

    private static final BigDecimal DEFAULT_SWAP_FEE_RATE = new BigDecimal("0.002");
    private static final BigDecimal DEFAULT_SWAP_SPREAD_RATE = new BigDecimal("0.003");
    private static final BigDecimal DEFAULT_MIN_SWAP_AMOUNT = new BigDecimal("0.000001");
    private static final BigDecimal DEFAULT_MIN_SWAP_AMOUNT_KORI = new BigDecimal("1000.0");
    private static final BigDecimal DEFAULT_MIN_SWAP_AMOUNT_KRWT = new BigDecimal("1000.0");
    private static final String DEFAULT_PRICE_SOURCE = "DB_MARKET";
    private static final String DEFAULT_PRICE_NOTE = "Realtime price source: DB market price";

    private static final String CFG_SWAP_FEE_BPS = "swap.fee_bps";
    private static final String CFG_SWAP_SPREAD_BPS = "swap.spread_bps";
    private static final String CFG_SWAP_MIN_AMOUNT_DEFAULT = "swap.min_amount.default";
    private static final String CFG_SWAP_MIN_AMOUNT_KORI = "swap.min_amount.KORI";
    private static final String CFG_SWAP_MIN_AMOUNT_KRWT = "swap.min_amount.KRWT";
    private static final String CFG_SWAP_PRICE_SOURCE = "swap.price_source";
    private static final String CFG_SWAP_PRICE_NOTE = "swap.price_note";

    private static final String INTERNAL_CHAIN = "INTERNAL";
    private static final String SWAP_COMPLETED_TITLE = "스왑 완료";
    private static final String SWAP_COMPLETED_MESSAGE = "스왑이 완료되었습니다.";
    private static final String SWAP_COMPLETED_TITLE_KEY = "notifications.swapCompleted.title";
    private static final String SWAP_COMPLETED_MESSAGE_KEY = "notifications.swapCompleted.message";

    private record ResolvedSwapWallet(Currency currency, Wallet wallet) {}
    private record SwapConfig(BigDecimal feeRate,
                              BigDecimal spreadRate,
                              BigDecimal defaultMinAmount,
                              BigDecimal koriMinAmount,
                              BigDecimal krwtMinAmount,
                              String priceSource,
                              String note) {}
    private record ComputedSwapQuote(BigDecimal exchangeRate,
                                     BigDecimal feeRate,
                                     BigDecimal feeAmount,
                                     BigDecimal spreadRate,
                                     BigDecimal spreadAmount,
                                     BigDecimal toAmount) {}

    public SwapService(PgPool pool,
                       SwapRepository swapRepository,
                       CurrencyRepository currencyRepository,
                       AppConfigRepository appConfigRepository,
                       CurrencyService currencyService,
                       TransferRepository transferRepository) {
        this(pool, swapRepository, currencyRepository, appConfigRepository, currencyService, transferRepository, null);
    }

    public SwapService(PgPool pool,
                       SwapRepository swapRepository,
                       CurrencyRepository currencyRepository,
                       AppConfigRepository appConfigRepository,
                       CurrencyService currencyService,
                       TransferRepository transferRepository,
                       NotificationService notificationService) {
        super(pool);
        this.swapRepository = swapRepository;
        this.currencyRepository = currencyRepository;
        this.appConfigRepository = appConfigRepository;
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

        if (request.getFromCurrencyCode() == null || request.getToCurrencyCode() == null) {
            return Future.failedFuture(new BadRequestException("Currency code is required."));
        }

        String fromCurrencyCode = normalizeCurrencyCode(request.getFromCurrencyCode());
        String toCurrencyCode = normalizeCurrencyCode(request.getToCurrencyCode());
        if (fromCurrencyCode.equals(toCurrencyCode)) {
            return Future.failedFuture(new BadRequestException("Cannot swap to the same currency."));
        }

        return loadSwapConfig()
            .compose(config -> validateMinimumAmount(request.getFromAmount(), fromCurrencyCode, config)
                .compose(v -> resolvePreferredSwapWallet(userId, fromCurrencyCode, request.getNetwork(), "FROM")
                    .compose(fromResolved ->
                        resolvePreferredSwapWallet(userId, toCurrencyCode, request.getNetwork(), "TO")
                            .compose(toResolved -> {
                                Currency fromCurrency = fromResolved.currency();
                                Currency toCurrency = toResolved.currency();
                                Wallet fromWallet = fromResolved.wallet();

                                if (fromWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                                    return Future.failedFuture(new BadRequestException("Insufficient balance."));
                                }

                                return currencyService.getSwapExchangeRate(fromCurrencyCode, toCurrencyCode)
                                    .map(exchangeRate -> computeSwapQuote(request.getFromAmount(), exchangeRate, config))
                                    .compose(quote -> executeSwapTransaction(
                                        userId,
                                        fromCurrency,
                                        toCurrency,
                                        fromWallet,
                                        request.getFromAmount(),
                                        quote.toAmount(),
                                        request.getNetwork(),
                                        requestIp
                                    ).compose(this::createSwapCompletedNotification));
                            }))));
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

                JsonObject metadata = new JsonObject()
                    .put("swapId", responseDto.getSwapId())
                    .put("orderNumber", responseDto.getOrderNumber())
                    .put("fromCurrencyCode", responseDto.getFromCurrencyCode())
                    .put("toCurrencyCode", responseDto.getToCurrencyCode())
                    .put("fromAmount", responseDto.getFromAmount() != null ? responseDto.getFromAmount().toPlainString() : null)
                    .put("toAmount", responseDto.getToAmount() != null ? responseDto.getToAmount().toPlainString() : null)
                    .put("amount", responseDto.getFromAmount() != null ? responseDto.getFromAmount().toPlainString() : null)
                    .put("currencyCode", responseDto.getFromCurrencyCode())
                    .put("network", responseDto.getNetwork())
                    .put("status", responseDto.getStatus());
                String encodedMetadata = NotificationI18nUtils.buildMetadata(
                    SWAP_COMPLETED_TITLE_KEY,
                    SWAP_COMPLETED_MESSAGE_KEY,
                    metadata
                );

                return notificationService.createNotificationIfAbsentByRelatedId(
                    swap.getUserId(),
                    NotificationType.SWAP_SUCCESS,
                    SWAP_COMPLETED_TITLE,
                    SWAP_COMPLETED_MESSAGE,
                    swap.getId(),
                    encodedMetadata
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
        String normalizedFromCurrency = normalizeCurrencyCode(fromCurrencyCode);
        String normalizedToCurrency = normalizeCurrencyCode(toCurrencyCode);
        return loadSwapConfig()
            .compose(config -> validateMinimumAmount(fromAmount, normalizedFromCurrency, config)
                .compose(v -> currencyRepository.getCurrencyByCodeAndChain(pool, normalizedFromCurrency, network)
                    .compose(fromCurrency -> {
                        if (fromCurrency == null) {
                            return Future.failedFuture(new NotFoundException("FROM currency not found: " + normalizedFromCurrency));
                        }

                        return currencyRepository.getCurrencyByCodeAndChain(pool, normalizedToCurrency, network)
                            .compose(toCurrency -> {
                                if (toCurrency == null) {
                                    return Future.failedFuture(new NotFoundException("TO currency not found: " + normalizedToCurrency));
                                }

                                return currencyService.getSwapExchangeRate(normalizedFromCurrency, normalizedToCurrency)
                                    .map(exchangeRate -> {
                                        ComputedSwapQuote quote = computeSwapQuote(fromAmount, exchangeRate, config);
                                        return SwapQuoteDto.builder()
                                            .fromCurrencyCode(normalizedFromCurrency)
                                            .toCurrencyCode(normalizedToCurrency)
                                            .fromAmount(fromAmount)
                                            .exchangeRate(quote.exchangeRate())
                                            .fee(quote.feeRate())
                                            .feeAmount(quote.feeAmount())
                                            .spread(quote.spreadRate())
                                            .spreadAmount(quote.spreadAmount())
                                            .toAmount(quote.toAmount())
                                            .network(network)
                                            .build();
                                    });
                            });
                    })));
    }

    /**
     * Get list of swappable currencies.
     */
    public Future<SwapCurrenciesDto> getSwapCurrencies() {
        return loadSwapConfig()
            .compose(config -> currencyRepository.getAllActiveCurrencies(pool)
                .map(currencies -> {
                    List<SwapCurrenciesDto.CurrencyInfo> currencyInfos = currencies.stream()
                        .filter(c -> !INTERNAL_CHAIN.equals(c.getChain()))
                        .map(c -> SwapCurrenciesDto.CurrencyInfo.builder()
                            .code(c.getCode())
                            .name(c.getName())
                            .symbol(c.getCode())
                            .network(c.getChain())
                            .decimals(18)
                            .minSwapAmount(resolveMinSwapAmount(c.getCode(), config))
                            .build())
                        .collect(Collectors.toList());

                    return SwapCurrenciesDto.builder()
                        .currencies(currencyInfos)
                        .build();
                }));
    }

    /**
     * Get static swap configuration.
     */
    public Future<SwapInfoDto> getSwapInfo(String currencyCode) {
        return loadSwapConfig()
            .map(config -> SwapInfoDto.builder()
                .fee(config.feeRate())
                .spread(config.spreadRate())
                .minSwapAmount(buildMinSwapAmountPayload(currencyCode, config))
                .priceSource(config.priceSource())
                .note(config.note())
                .build());
    }

    private Future<Void> validateMinimumAmount(BigDecimal fromAmount, String fromCurrencyCode, SwapConfig config) {
        BigDecimal minimumAmount = resolveMinSwapAmount(fromCurrencyCode, config);
        if (fromAmount == null || fromAmount.compareTo(minimumAmount) < 0) {
            return Future.failedFuture(new BadRequestException("Minimum swap amount is " + minimumAmount.stripTrailingZeros().toPlainString() + " " + fromCurrencyCode));
        }
        return Future.succeededFuture();
    }

    private ComputedSwapQuote computeSwapQuote(BigDecimal fromAmount, BigDecimal exchangeRate, SwapConfig config) {
        BigDecimal baseToAmount = fromAmount.multiply(exchangeRate);
        BigDecimal feeAmount = baseToAmount.multiply(config.feeRate()).setScale(18, RoundingMode.DOWN);
        BigDecimal spreadAmount = baseToAmount.multiply(config.spreadRate()).setScale(18, RoundingMode.DOWN);
        BigDecimal toAmount = baseToAmount
            .subtract(feeAmount)
            .subtract(spreadAmount)
            .setScale(18, RoundingMode.DOWN);
        if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Calculated swap amount must be greater than zero.");
        }
        return new ComputedSwapQuote(
            exchangeRate,
            config.feeRate(),
            feeAmount,
            config.spreadRate(),
            spreadAmount,
            toAmount
        );
    }

    private Future<SwapConfig> loadSwapConfig() {
        Future<String> feeBpsFuture = getConfigValue(CFG_SWAP_FEE_BPS);
        Future<String> spreadBpsFuture = getConfigValue(CFG_SWAP_SPREAD_BPS);
        Future<String> defaultMinAmountFuture = getConfigValue(CFG_SWAP_MIN_AMOUNT_DEFAULT);
        Future<String> koriMinAmountFuture = getConfigValue(CFG_SWAP_MIN_AMOUNT_KORI);
        Future<String> krwtMinAmountFuture = getConfigValue(CFG_SWAP_MIN_AMOUNT_KRWT);
        Future<String> priceSourceFuture = getConfigValue(CFG_SWAP_PRICE_SOURCE);
        Future<String> noteFuture = getConfigValue(CFG_SWAP_PRICE_NOTE);

        return CompositeFuture.all(List.of(
            feeBpsFuture,
            spreadBpsFuture,
            defaultMinAmountFuture,
            koriMinAmountFuture,
            krwtMinAmountFuture,
            priceSourceFuture,
            noteFuture
        )).map(v -> new SwapConfig(
            parseRateFromBps(feeBpsFuture.result(), DEFAULT_SWAP_FEE_RATE),
            parseRateFromBps(spreadBpsFuture.result(), DEFAULT_SWAP_SPREAD_RATE),
            parsePositiveDecimal(defaultMinAmountFuture.result(), DEFAULT_MIN_SWAP_AMOUNT),
            parsePositiveDecimal(koriMinAmountFuture.result(), DEFAULT_MIN_SWAP_AMOUNT_KORI),
            parsePositiveDecimal(krwtMinAmountFuture.result(), DEFAULT_MIN_SWAP_AMOUNT_KRWT),
            defaultIfBlank(priceSourceFuture.result(), DEFAULT_PRICE_SOURCE),
            defaultIfBlank(noteFuture.result(), DEFAULT_PRICE_NOTE)
        ));
    }

    private Future<String> getConfigValue(String configKey) {
        if (appConfigRepository == null || configKey == null || configKey.isBlank()) {
            return Future.succeededFuture(null);
        }
        return appConfigRepository.getByKey(pool, configKey)
            .recover(err -> {
                log.warn("Failed to read swap config. key={}, cause={}", configKey, err.getMessage());
                return Future.succeededFuture(null);
            });
    }

    private BigDecimal resolveMinSwapAmount(String currencyCode, SwapConfig config) {
        String normalizedCode = normalizeCurrencyCode(currencyCode);
        if ("KORI".equals(normalizedCode) || "KORION".equals(normalizedCode)) {
            return config.koriMinAmount();
        }
        if ("KRWT".equals(normalizedCode)) {
            return config.krwtMinAmount();
        }
        return config.defaultMinAmount();
    }

    private Map<String, BigDecimal> buildMinSwapAmountPayload(String currencyCode, SwapConfig config) {
        Map<String, BigDecimal> minSwapAmount = new HashMap<>();
        String normalizedCode = normalizeCurrencyCode(currencyCode);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            minSwapAmount.put("KORI", config.koriMinAmount());
            minSwapAmount.put("KRWT", config.krwtMinAmount());
            return minSwapAmount;
        }
        minSwapAmount.put(normalizedCode, resolveMinSwapAmount(normalizedCode, config));
        return minSwapAmount;
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return "";
        }
        return currencyCode.trim().toUpperCase();
    }

    private BigDecimal parseRateFromBps(String rawValue, BigDecimal fallback) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return fallback;
            }
            BigDecimal parsedBps = new BigDecimal(rawValue.trim());
            if (parsedBps.compareTo(BigDecimal.ZERO) < 0) {
                return fallback;
            }
            return parsedBps.divide(new BigDecimal("10000"), 6, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private BigDecimal parsePositiveDecimal(String rawValue, BigDecimal fallback) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                return fallback;
            }
            BigDecimal parsed = new BigDecimal(rawValue.trim());
            return parsed.compareTo(BigDecimal.ZERO) > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
