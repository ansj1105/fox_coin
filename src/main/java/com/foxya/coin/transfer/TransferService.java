package com.foxya.coin.transfer;

import com.foxya.coin.airdrop.AirdropRepository;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.ChainType;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.enums.TransactionType;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.deposit.TokenDepositRepository;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.exchange.ExchangeRepository;
import com.foxya.coin.exchange.entities.Exchange;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventType;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationI18nUtils;
import com.foxya.coin.payment.PaymentDepositRepository;
import com.foxya.coin.payment.entities.PaymentDeposit;
import com.foxya.coin.swap.SwapRepository;
import com.foxya.coin.swap.entities.Swap;
import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import com.foxya.coin.transfer.dto.InternalTransferRequestDto;
import com.foxya.coin.transfer.dto.TransferHistoryResponseDto;
import com.foxya.coin.transfer.dto.TransferResponseDto;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class TransferService extends BaseService {

    /** Normalized comment. */
    private static final int CONFIRMED_IDEMPOTENCY_TTL_SECONDS = 7 * 24 * 3600;
    private static final String REDIS_KEY_CONFIRMED = "transfer:confirmed:";
    private static final String REDIS_KEY_FAILED = "transfer:failed:";
    private static final String INTERNAL_CHAIN = "INTERNAL";
    private static final int WITHDRAWAL_REDISPATCH_MAX_RETRY = 50;

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final AppConfigRepository appConfigRepository;
    private final WalletRepository walletRepository;
    private final EventPublisher eventPublisher;
    private final RedisAPI redisApi;
    private final NotificationService notificationService;
    private final AirdropRepository airdropRepository;
    private final TokenDepositRepository tokenDepositRepository;
    private final PaymentDepositRepository paymentDepositRepository;
    private final SwapRepository swapRepository;
    private final ExchangeRepository exchangeRepository;

    // Normalized comment.
    private static final BigDecimal INTERNAL_FEE_RATE = new BigDecimal("0.001");
    // Normalized comment.
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.000001");
    private static final String HOT_WALLET_MIN_PREFIX = "hot_wallet_min.";
    private static final String HOT_WALLET_USER_ID_KEY = "hot_wallet_user_id";
    private static final String WITHDRAW_COMPLETED_TITLE = "출금 완료";
    private static final String WITHDRAW_COMPLETED_MESSAGE = "출금이 완료되었습니다.";
    private static final String WITHDRAW_COMPLETED_TITLE_KEY = "notifications.withdrawCompleted.title";
    private static final String WITHDRAW_COMPLETED_MESSAGE_KEY = "notifications.withdrawCompleted.message";
    private static final Pattern TRON_ADDRESS_PATTERN = Pattern.compile("^T[1-9A-HJ-NP-Za-km-z]{33}$");
    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final boolean LEGACY_WITHDRAWAL_STREAM_ENABLED =
        Boolean.parseBoolean(System.getenv().getOrDefault("LEGACY_WITHDRAWAL_STREAM_ENABLED", "false"));

    private final WithdrawalBridgeClient withdrawalBridgeClient;

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, null, null, null, null, null, null, null, null, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, null, null, null, null, null, null, null, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi,
                          NotificationService notificationService) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, notificationService, null, null, null, null, null, null, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi,
                          NotificationService notificationService,
                          AirdropRepository airdropRepository) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, notificationService, airdropRepository, null, null, null, null, null, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi,
                          NotificationService notificationService,
                          AirdropRepository airdropRepository,
                          AppConfigRepository appConfigRepository,
                          TokenDepositRepository tokenDepositRepository,
                          PaymentDepositRepository paymentDepositRepository,
                          SwapRepository swapRepository,
                          ExchangeRepository exchangeRepository,
                          WithdrawalBridgeClient withdrawalBridgeClient) {
        super(pool);
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.currencyRepository = currencyRepository;
        this.appConfigRepository = appConfigRepository;
        this.walletRepository = walletRepository;
        this.eventPublisher = eventPublisher;
        this.redisApi = redisApi;
        this.notificationService = notificationService;
        this.airdropRepository = airdropRepository;
        this.tokenDepositRepository = tokenDepositRepository;
        this.paymentDepositRepository = paymentDepositRepository;
        this.swapRepository = swapRepository;
        this.exchangeRepository = exchangeRepository;
        this.withdrawalBridgeClient = withdrawalBridgeClient;
    }
    
    /**
      * Normalized comment.
      * Normalized comment.
     */
    public Future<TransferResponseDto> executeInternalTransfer(Long senderId, InternalTransferRequestDto request, String requestIp) {
        log.info("Normalized log message", 
            senderId, request.getReceiverType(), request.getReceiverValue(), request.getAmount());
        
        // Normalized comment.
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("Invalid request." + MIN_TRANSFER_AMOUNT + "Invalid request."));
        }
        
        // Normalized comment.
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getCurrencyCode(), "INTERNAL")
            .compose(externalCurrency -> {
                if (externalCurrency == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found." + request.getCurrencyCode() + " on INTERNAL"));
                }
                
                // Normalized comment.
                return findReceiver(request.getReceiverType(), request.getReceiverValue())
                    .compose(receiver -> {
                        if (receiver == null) {
                            return Future.failedFuture(new NotFoundException("Resource not found."));
                        }
                        
                        if (receiver.getId().equals(senderId)) {
                            return Future.failedFuture(new BadRequestException("Invalid request."));
                        }
                        
                        // Normalized comment.
                        return getOrCreateInternalWallet(senderId, externalCurrency, false)
                            .compose(senderWallet ->
                                getOrCreateInternalWallet(receiver.getId(), externalCurrency, true)
                                    .compose(receiverWallet -> {
                                        // Normalized comment.
                                        BigDecimal fee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                                        BigDecimal totalDeduct = request.getAmount().add(fee);

                                        // Normalized comment.
                                        if (senderWallet.getBalance().compareTo(totalDeduct) < 0) {
                                            return Future.failedFuture(new BadRequestException("Invalid request." + totalDeduct + "Invalid request." + senderWallet.getBalance()));
                                        }

                                        // Normalized comment.
                                        return executeInternalTransferTransaction(
                                            senderId, receiver.getId(),
                                            senderWallet, receiverWallet,
                                            externalCurrency, request.getAmount(), fee,
                                            request.getMemo(), requestIp
                                        );
                                    })
                            );
                    });
            });
    }
    
    /**
      * Normalized comment.
     */
    private Future<TransferResponseDto> executeInternalTransferTransaction(
            Long senderId, Long receiverId,
            Wallet senderWallet, Wallet receiverWallet,
            Currency currency, BigDecimal amount, BigDecimal fee,
            String memo, String requestIp) {
        
        String transferId = UUID.randomUUID().toString();
        BigDecimal totalDeduct = amount.add(fee);
        
        // Normalized comment.
        return pool.withTransaction(client -> {
            // Normalized comment.
            return transferRepository.deductBalance(client, senderWallet.getId(), totalDeduct)
                .compose(updatedSenderWallet -> {
                    if (updatedSenderWallet == null) {
                        return Future.failedFuture(new BadRequestException("Invalid request."));
                    }
                    
                    // Normalized comment.
                    return transferRepository.addBalance(client, receiverWallet.getId(), amount);
                })
                .compose(updatedReceiverWallet -> {
                    if (updatedReceiverWallet == null) {
                        return Future.failedFuture(new BadRequestException("Invalid request."));
                    }
                    
                    // Normalized comment.
                    String orderNumber = OrderNumberUtils.generateOrderNumber();
                    InternalTransfer transfer = InternalTransfer.builder()
                        .transferId(transferId)
                        .senderId(senderId)
                        .senderWalletId(senderWallet.getId())
                        .receiverId(receiverId)
                        .receiverWalletId(receiverWallet.getId())
                        .currencyId(currency.getId())
                        .amount(amount)
                        .fee(fee)
                        .status(InternalTransfer.STATUS_COMPLETED)
                        .transferType(InternalTransfer.TYPE_INTERNAL)
                        .orderNumber(orderNumber)
                        .transactionType(TransactionType.WITHDRAW.getValue())
                        .memo(memo)
                        .requestIp(requestIp)
                        .build();
                    
                    return transferRepository.createInternalTransfer(client, transfer);
                })
                .compose(createdTransfer -> {
                    // Normalized comment.
                    return transferRepository.completeInternalTransfer(client, transferId);
                });
        }).map(completedTransfer -> {
            log.info("Normalized log message", 
                transferId, senderId, receiverId, amount);
            
            return TransferResponseDto.builder()
                .transferId(transferId)
                .transferType("INTERNAL")
                .senderId(senderId)
                .receiverId(receiverId)
                .currencyCode(currency.getCode())
                .amount(amount)
                .fee(fee)
                .status(InternalTransfer.STATUS_COMPLETED)
                .memo(memo)
                .createdAt(completedTransfer.getCreatedAt())
                .completedAt(completedTransfer.getCompletedAt())
                .build();
        });
    }

    /**
     * Use external-chain wallet first; fallback to INTERNAL wallet when external wallet is missing.
     */
    private Future<Wallet> resolvePreferredWalletForWithdrawal(Long userId, Currency externalCurrency) {
        if ("KORI".equalsIgnoreCase(externalCurrency.getCode())) {
            return currencyRepository.getCurrencyByCodeAndChain(pool, externalCurrency.getCode(), INTERNAL_CHAIN)
                .compose(internalCurrency -> {
                    if (internalCurrency == null) {
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, externalCurrency.getId());
                    }
                    return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, internalCurrency.getId())
                        .compose(internalWallet -> {
                            if (internalWallet != null) {
                                return Future.succeededFuture(internalWallet);
                            }
                            return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, externalCurrency.getId());
                        });
                });
        }

        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, externalCurrency.getId())
            .compose(externalWallet -> {
                if (externalWallet != null) {
                    return Future.succeededFuture(externalWallet);
                }
                return currencyRepository.getCurrencyByCodeAndChain(pool, externalCurrency.getCode(), INTERNAL_CHAIN)
                    .compose(internalCurrency -> {
                        if (internalCurrency == null) {
                            return Future.failedFuture(new NotFoundException("Resource not found."));
                        }
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, internalCurrency.getId())
                            .compose(internalWallet -> {
                                if (internalWallet != null) {
                                    return Future.succeededFuture(internalWallet);
                                }
                                return Future.failedFuture(new NotFoundException("Resource not found."));
                            });
                    });
            });
    }
    
    /**
      * Normalized comment.
     */
    public Future<InternalTransfer> createReferralRewardTransfer(Long referrerId, BigDecimal amount, String memo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.failedFuture(new BadRequestException("Invalid request."));
        }
        return currencyRepository.getCurrencyByCodeAndChainAllowInactive(pool, "KORI", "INTERNAL")
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found."));
                }
                return getOrCreateInternalWallet(referrerId, currency, true)
                    .compose(receiverWallet -> {
                        if (receiverWallet == null) {
                            return Future.failedFuture(new NotFoundException("Resource not found."));
                        }
                        String transferId = UUID.randomUUID().toString();
                        InternalTransfer transfer = InternalTransfer.builder()
                            .transferId(transferId)
                            .senderId(null)
                            .senderWalletId(null)
                            .receiverId(referrerId)
                            .receiverWalletId(receiverWallet.getId())
                            .currencyId(currency.getId())
                            .amount(amount)
                            .fee(BigDecimal.ZERO)
                            .status(InternalTransfer.STATUS_COMPLETED)
                            .transferType(InternalTransfer.TYPE_REFERRAL_REWARD)
                            .orderNumber(OrderNumberUtils.generateOrderNumber())
                            .transactionType(TransactionType.REFERRAL_REWARD.getValue())
                            .memo(memo != null ? memo : "REFERRAL_REWARD")
                            .requestIp(null)
                            .build();
                        return pool.withTransaction(client -> transferRepository.addBalance(client, receiverWallet.getId(), amount)
                            .compose(updated -> transferRepository.createInternalTransfer(client, transfer)));
                    });
            });
    }
    
    /**
      * Normalized comment.
     */
    private Future<User> findReceiver(String receiverType, String receiverValue) {
        return switch (receiverType) {
            case InternalTransferRequestDto.RECEIVER_TYPE_ADDRESS -> 
                // Normalized comment.
                transferRepository.getWalletByAddress(pool, receiverValue)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.succeededFuture(null);
                        }
                        return userRepository.getUserById(pool, wallet.getUserId());
                    });
            
            case InternalTransferRequestDto.RECEIVER_TYPE_REFERRAL_CODE -> 
                // Normalized comment.
                userRepository.getUserByReferralCode(pool, receiverValue);
            
            case InternalTransferRequestDto.RECEIVER_TYPE_USER_ID -> 
                // Normalized comment.
                userRepository.getUserById(pool, Long.parseLong(receiverValue));
            
            default -> Future.failedFuture(new BadRequestException("Invalid request." + receiverType));
        };
    }

    private Future<Wallet> getOrCreateInternalWallet(Long userId, Currency currency, boolean createIfMissing) {
        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
            .compose(existing -> {
                if (existing != null) {
                    return Future.succeededFuture(existing);
                }
                if (!createIfMissing) {
                    return Future.failedFuture(new NotFoundException("Resource not found."));
                }
                return createInternalWalletIfNeeded(userId, currency)
                    .compose(created -> {
                        if (created == null) {
                            return Future.failedFuture(new NotFoundException("Resource not found."));
                        }
                        return Future.succeededFuture(created);
                    });
            });
    }

    private Future<Wallet> createInternalWalletIfNeeded(Long userId, Currency currency) {
        if (!"INTERNAL".equalsIgnoreCase(currency.getChain())) {
            return Future.succeededFuture(null);
        }
        String address = currency.getCode() + "_INTERNAL_" + userId;
        return walletRepository.createWallet(pool, userId, currency.getId(), address)
            .recover(throwable -> {
                if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                    return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId());
                }
                return Future.failedFuture(throwable);
            });
    }
    
    /**
      * Normalized comment.
      * Normalized comment.
      * Normalized comment.
     */
    private Future<Long> getHotWalletUserId() {
        if (appConfigRepository == null) {
            return Future.succeededFuture(null);
        }
        return appConfigRepository.getByKey(pool, HOT_WALLET_USER_ID_KEY)
            .map(value -> {
                if (value == null || value.isBlank()) return null;
                try {
                    return Long.parseLong(value.trim());
                } catch (Exception e) {
                    return null;
                }
            });
    }

    private Future<java.math.BigDecimal> getHotWalletMin(String chain, String currencyCode) {
        if (appConfigRepository == null) {
            return Future.succeededFuture(null);
        }
        String key = HOT_WALLET_MIN_PREFIX + chain + "." + currencyCode;
        return appConfigRepository.getByKey(pool, key)
            .map(value -> {
                if (value == null || value.isBlank()) return null;
                try {
                    return new java.math.BigDecimal(value.trim());
                } catch (Exception e) {
                    return null;
                }
            });
    }

    private Future<Boolean> isHotWalletLiquiditySufficient(String chain, String currencyCode, java.math.BigDecimal amount) {
        return getHotWalletUserId()
            .compose(hotUserId -> {
                if (hotUserId == null) {
                    return Future.succeededFuture(true);
                }
                return getHotWalletMin(chain, currencyCode)
                    .compose(min -> {
                        if (min == null) {
                            return Future.succeededFuture(true);
                        }
                        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, chain)
                            .compose(currency -> {
                                if (currency == null) {
                                    return Future.succeededFuture(true);
                                }
                                return walletRepository.getWalletByUserIdAndCurrencyId(pool, hotUserId, currency.getId())
                                    .map(wallet -> {
                                        if (wallet == null || wallet.getBalance() == null) {
                                            return false;
                                        }
                                        java.math.BigDecimal needed = min.add(amount);
                                        return wallet.getBalance().compareTo(needed) >= 0;
                                    });
                            });
                    });
            });
    }

    public Future<TransferResponseDto> requestExternalTransfer(Long userId, ExternalTransferRequestDto request, String requestIp) {
        log.info("Normalized log message",
            userId, request.getToAddress(), request.getAmount(), request.getChain());

        if (request == null) {
            return Future.failedFuture(new BadRequestException("Invalid request."));
        }

        String normalizedChain = normalizeChain(request.getChain());
        String normalizedCurrencyCode = normalizeCurrencyCode(request.getCurrencyCode());
        String normalizedToAddress = request.getToAddress() != null ? request.getToAddress().trim() : null;

        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("Invalid request." + MIN_TRANSFER_AMOUNT + "Invalid request."));
        }

        if (normalizedToAddress == null || normalizedToAddress.isBlank()) {
            return Future.failedFuture(new BadRequestException("Invalid request."));
        }

        if (normalizedCurrencyCode == null || normalizedCurrencyCode.isBlank()) {
            return Future.failedFuture(new BadRequestException("Invalid request."));
        }

        if (!isSupportedWithdrawalAddress(normalizedChain, normalizedToAddress)) {
            return Future.failedFuture(new BadRequestException("Invalid request." + normalizedChain + " address"));
        }

        request.setChain(normalizedChain);
        request.setCurrencyCode(normalizedCurrencyCode);
        request.setToAddress(normalizedToAddress);

        ChainType chainType = ChainType.fromValue(normalizedChain);
        if (chainType == null) {
            return Future.failedFuture(new BadRequestException("Invalid request." + normalizedChain));
        }

        return currencyRepository.getCurrencyByCodeAndChain(pool, normalizedCurrencyCode, normalizedChain)
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found." + normalizedCurrencyCode + " on " + normalizedChain));
                }

                // Normalized comment.
                return resolvePreferredWalletForWithdrawal(userId, currency)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("Resource not found."));
                        }

                        // Normalized comment.
                        BigDecimal serviceFee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                        BigDecimal totalDeduct = request.getAmount().add(serviceFee);

                        // Normalized comment.
                        if (wallet.getBalance().compareTo(totalDeduct) < 0) {
                            return Future.failedFuture(new BadRequestException("Invalid request." + totalDeduct + "Invalid request." + wallet.getBalance()));
                        }

                        if (supportsKorionWithdrawalBridge(currency.getCode(), normalizedChain)) {
                            return createExternalTransferRequest(
                                userId,
                                wallet,
                                currency,
                                request,
                                serviceFee,
                                requestIp,
                                ExternalTransfer.STATUS_PENDING,
                                false,
                                true
                            );
                        }

                        return isHotWalletLiquiditySufficient(normalizedChain, currency.getCode(), request.getAmount())
                            .compose(hasLiquidity -> {
                                String status = hasLiquidity ? ExternalTransfer.STATUS_PENDING : ExternalTransfer.STATUS_WAITING_LIQUIDITY;
                                boolean dispatch = hasLiquidity;
                                return createExternalTransferRequest(userId, wallet, currency, request, serviceFee, requestIp, status, dispatch, false);
                            });
                    });
            });
    }
    
    /**
      * Normalized comment.
      * Normalized comment.
     */
    private Future<TransferResponseDto> createExternalTransferRequest(
            Long userId, Wallet wallet, Currency currency,
            ExternalTransferRequestDto request, BigDecimal serviceFee, String requestIp,
            String status, boolean dispatchEvent, boolean bridgeToKorion) {
        
        String transferId = UUID.randomUUID().toString();
        BigDecimal totalDeduct = request.getAmount().add(serviceFee);
        
        return pool.withTransaction(client -> {
            // Normalized comment.
            return transferRepository.lockBalance(client, wallet.getId(), totalDeduct)
                .compose(updatedWallet -> {
                    if (updatedWallet == null) {
                        return Future.failedFuture(new BadRequestException("Invalid request."));
                    }
                    
                    // Normalized comment.
                    String orderNumber = OrderNumberUtils.generateOrderNumber();
                    ExternalTransfer transfer = ExternalTransfer.builder()
                        .transferId(transferId)
                        .userId(userId)
                        .walletId(wallet.getId())
                        .currencyId(currency.getId())
                        .toAddress(request.getToAddress())
                        .amount(request.getAmount())
                        .fee(serviceFee)
                        .networkFee(BigDecimal.ZERO) // Network fee is filled by Node.js at submission
                        .status(status)
                        .orderNumber(orderNumber)
                        .transactionType(TransactionType.WITHDRAW.getValue())
                        .chain(request.getChain())
                        .requiredConfirmations(getRequiredConfirmations(request.getChain()))
                        .retryCount(0)
                        .memo(request.getMemo())
                        .requestIp(requestIp)
                        .build();
                    
                    return transferRepository.createExternalTransfer(client, transfer);
                });
        }).compose(createdTransfer -> {
            log.info("Normalized log message", transferId);
            
            // Normalized comment.
            if (dispatchEvent && eventPublisher != null && LEGACY_WITHDRAWAL_STREAM_ENABLED) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("transferId", transferId);
                payload.put("userId", userId);
                payload.put("toAddress", request.getToAddress());
                payload.put("amount", request.getAmount().toPlainString());
                payload.put("currencyCode", currency.getCode());
                payload.put("chain", request.getChain());
                
                eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                    .onFailure(e -> log.error("Normalized log message", e.getMessage()));
            } else if (dispatchEvent && !LEGACY_WITHDRAWAL_STREAM_ENABLED) {
                log.info("Legacy withdrawal stream dispatch disabled for transferId={}", transferId);
            }
            
            Future<ExternalTransfer> finalizedTransfer = bridgeToKorion
                ? bridgeExternalTransferToKorion(createdTransfer, request, requestIp)
                : Future.succeededFuture(createdTransfer);

            return finalizedTransfer.map(transfer -> TransferResponseDto.builder()
                .transferId(transferId)
                .transferType("EXTERNAL")
                .senderId(userId)
                .toAddress(request.getToAddress())
                .currencyCode(currency.getCode())
                .amount(request.getAmount())
                .fee(serviceFee)
                .status(transfer.getStatus())
                .memo(request.getMemo())
                .createdAt(transfer.getCreatedAt())
                .build());
        });
    }

    private Future<ExternalTransfer> bridgeExternalTransferToKorion(
            ExternalTransfer createdTransfer,
            ExternalTransferRequestDto request,
            String requestIp) {
        if (withdrawalBridgeClient == null) {
            return Future.succeededFuture(createdTransfer);
        }

        return withdrawalBridgeClient.requestWithdrawal(
                createdTransfer.getUserId(),
                createdTransfer.getTransferId(),
                request,
                requestIp
            )
            .compose(withdrawalId -> transferRepository.attachCoinManageWithdrawalId(pool, createdTransfer.getTransferId(), withdrawalId))
            .compose(updatedTransfer -> updatedTransfer != null
                ? Future.succeededFuture(updatedTransfer)
                : Future.failedFuture("coin_manage withdrawal id link failed"))
            .recover(error -> failExternalTransferAndRefund(
                    createdTransfer.getTransferId(),
                    "KORION_WITHDRAW_REQUEST_FAILED",
                    truncateErrorMessage(error.getMessage())
                )
                .compose(ignored -> Future.failedFuture(new RuntimeException("coin_manage withdrawal bridge failed", error))));
    }

    private boolean supportsKorionWithdrawalBridge(String currencyCode, String chain) {
        return withdrawalBridgeClient != null && withdrawalBridgeClient.supports(currencyCode, chain);
    }

    private String normalizeChain(String chain) {
        return chain == null ? null : chain.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrencyCode(String currencyCode) {
        return currencyCode == null ? null : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSupportedWithdrawalAddress(String chain, String toAddress) {
        if (chain == null || toAddress == null) {
            return false;
        }
        return switch (chain) {
            case ExternalTransfer.CHAIN_TRON -> TRON_ADDRESS_PATTERN.matcher(toAddress).matches();
            case ExternalTransfer.CHAIN_ETH -> ETH_ADDRESS_PATTERN.matcher(toAddress).matches();
            default -> true;
        };
    }

    private String truncateErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "coin_manage withdrawal bridge failed";
        }
        return message.length() > 250 ? message.substring(0, 250) : message;
    }
    
    /**
      * Normalized comment.
     */
    private int getRequiredConfirmations(String chain) {
        return switch (chain) {
            case ExternalTransfer.CHAIN_TRON -> 20;
            case ExternalTransfer.CHAIN_ETH -> 12;
            default -> 1;
        };
    }

    /**
      * Normalized comment.
     */
    public Future<List<ExternalTransfer>> listExternalTransfersByStatus(String status, int limit) {
        return transferRepository.getExternalTransfersByStatus(pool, status, limit);
    }

    /**
     * Periodically republishes pending withdrawals so external settlement is eventually executed.
     */
    /**
     * Promote waiting withdrawals when hot wallet liquidity is sufficient.
     */
    public Future<Integer> promoteWaitingWithdrawals(int limit) {
        if (eventPublisher == null || !LEGACY_WITHDRAWAL_STREAM_ENABLED) {
            return Future.succeededFuture(0);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return transferRepository.getWaitingExternalTransfers(pool, safeLimit)
            .compose(waitingTransfers -> {
                if (waitingTransfers == null || waitingTransfers.isEmpty()) {
                    return Future.succeededFuture(0);
                }
                java.util.List<io.vertx.core.Future<Integer>> tasks = waitingTransfers.stream()
                    .map(transfer -> currencyRepository.getCurrencyById(pool, transfer.getCurrencyId())
                        .compose(currency -> {
                            if (currency == null) return Future.succeededFuture(0);
                            return isHotWalletLiquiditySufficient(transfer.getChain(), currency.getCode(), transfer.getAmount())
                                .compose(ok -> {
                                    if (!ok) return Future.succeededFuture(0);
                                    return transferRepository.updateExternalTransferStatus(pool, transfer.getTransferId(), ExternalTransfer.STATUS_PENDING)
                                        .compose(updated -> {
                                            if (updated == null) return Future.succeededFuture(0);
                                            java.util.Map<String, Object> payload = new java.util.HashMap<>();
                                            payload.put("transferId", transfer.getTransferId());
                                            payload.put("userId", transfer.getUserId());
                                            payload.put("toAddress", transfer.getToAddress());
                                            payload.put("amount", transfer.getAmount() != null ? transfer.getAmount().toPlainString() : null);
                                            payload.put("currencyCode", currency.getCode());
                                            payload.put("chain", transfer.getChain());
                                            return eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                                                .map(v -> 1)
                                                .recover(err -> {
                                                    log.warn("Failed to promote waiting withdrawal - transferId: {}", transfer.getTransferId(), err);
                                                    return Future.succeededFuture(0);
                                                });
                                        });
                                });
                        }))
                    .collect(java.util.stream.Collectors.toList());

                return io.vertx.core.Future.all(tasks)
                    .map(result -> result.list().stream()
                        .mapToInt(item -> item instanceof Integer ? (Integer) item : 0)
                        .sum());
            });
    }

    public Future<Integer> redispatchPendingWithdrawals(int limit) {
        if (eventPublisher == null || !LEGACY_WITHDRAWAL_STREAM_ENABLED) {
            return Future.succeededFuture(0);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return transferRepository.getPendingExternalTransfers(pool, safeLimit)
            .compose(pendingTransfers -> {
                if (pendingTransfers == null || pendingTransfers.isEmpty()) {
                    return Future.succeededFuture(0);
                }

                List<Future<Integer>> tasks = pendingTransfers.stream()
                    .map(transfer -> {
                        int retryCount = transfer.getRetryCount() != null ? transfer.getRetryCount() : 0;
                        if (retryCount >= WITHDRAWAL_REDISPATCH_MAX_RETRY) {
                            return Future.succeededFuture(0);
                        }
                        return currencyRepository.getCurrencyById(pool, transfer.getCurrencyId())
                            .compose(currency -> {
                                if (currency == null) {
                                    return Future.succeededFuture(0);
                                }
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("transferId", transfer.getTransferId());
                                payload.put("userId", transfer.getUserId());
                                payload.put("toAddress", transfer.getToAddress());
                                payload.put("amount", transfer.getAmount() != null ? transfer.getAmount().toPlainString() : null);
                                payload.put("currencyCode", currency.getCode());
                                payload.put("chain", transfer.getChain());
                                payload.put("retryCount", retryCount + 1);
                                return eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                                    .compose(v -> transferRepository.incrementExternalTransferRetryCount(pool, transfer.getTransferId()))
                                    .map(updated -> updated != null ? 1 : 0)
                                    .recover(err -> {
                                        log.warn("Failed to redispatch withdrawal - transferId: {}", transfer.getTransferId(), err);
                                        return Future.succeededFuture(0);
                                    });
                            });
                    })
                    .collect(Collectors.toList());

                return Future.all(tasks)
                    .map(result -> result.list().stream()
                        .mapToInt(item -> item instanceof Integer ? (Integer) item : 0)
                        .sum());
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
     */
    public Future<ExternalTransfer> submitExternalTransfer(String transferId, String txHash) {
        return transferRepository.getExternalTransferById(pool, transferId)
            .compose(et -> {
                if (et == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found." + transferId));
                }
                if (!ExternalTransfer.STATUS_PENDING.equals(et.getStatus()) && !ExternalTransfer.STATUS_PROCESSING.equals(et.getStatus())) {
                    return Future.failedFuture(new BadRequestException("Invalid request." + et.getStatus()));
                }
                return transferRepository.submitExternalTransfer(pool, transferId, txHash);
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
      * Normalized comment.
      * Normalized comment.
     */
    public Future<ExternalTransfer> confirmExternalTransfer(String transferId, int confirmations) {
        if (redisApi != null) {
            String key = REDIS_KEY_CONFIRMED + transferId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("Normalized log message", transferId);
                        return transferRepository.getExternalTransferById(pool, transferId);
                    }
                    return doConfirmExternalTransfer(transferId, confirmations)
                        .compose(et -> redisApi.setex(key, String.valueOf(CONFIRMED_IDEMPOTENCY_TTL_SECONDS), "1")
                            .map(v -> et))
                        .compose(this::createWithdrawalCompletedNotification);
                });
        }
        return doConfirmExternalTransfer(transferId, confirmations)
            .compose(this::createWithdrawalCompletedNotification);
    }

    /** Normalized comment. */
    private Future<ExternalTransfer> createWithdrawalCompletedNotification(ExternalTransfer confirmed) {
        if (notificationService == null || confirmed == null || confirmed.getUserId() == null) {
            return Future.succeededFuture(confirmed);
        }
        return currencyRepository.getCurrencyById(pool, confirmed.getCurrencyId())
            .compose(currency -> {
                String currencyCode = currency != null ? currency.getCode() : "";
                String amountStr = confirmed.getAmount() != null ? confirmed.getAmount().toPlainString() : "";
                JsonObject meta = new JsonObject()
                    .put("transferId", confirmed.getTransferId())
                    .put("amount", amountStr)
                    .put("currencyCode", currencyCode)
                    .put("txHash", confirmed.getTxHash())
                    .put("toAddress", confirmed.getToAddress());
                String encodedMetadata = NotificationI18nUtils.buildMetadata(
                    WITHDRAW_COMPLETED_TITLE_KEY,
                    WITHDRAW_COMPLETED_MESSAGE_KEY,
                    meta
                );
                return notificationService.createNotificationIfAbsentByRelatedId(
                    confirmed.getUserId(),
                    NotificationType.WITHDRAW_SUCCESS,
                    WITHDRAW_COMPLETED_TITLE,
                    WITHDRAW_COMPLETED_MESSAGE,
                    confirmed.getId(),
                    encodedMetadata
                );
            })
            .map(v -> confirmed)
            .recover(err -> {
                log.warn("Normalized log message", confirmed.getTransferId(), err);
                return Future.succeededFuture(confirmed);
            });
    }

    private Future<ExternalTransfer> doConfirmExternalTransfer(String transferId, int confirmations) {
        return pool.withTransaction(client ->
            transferRepository.getExternalTransferById(client, transferId)
                .compose(et -> {
                    if (et == null) {
                        return Future.failedFuture(new NotFoundException("Resource not found." + transferId));
                    }
                    if (!ExternalTransfer.STATUS_SUBMITTED.equals(et.getStatus())) {
                        return Future.failedFuture(new BadRequestException(
                            "Normalized text" + et.getStatus()));
                    }
                    BigDecimal totalDeduct = et.getAmount().add(et.getFee() != null ? et.getFee() : BigDecimal.ZERO);
                    return transferRepository.confirmExternalTransfer(client, transferId, confirmations)
                        .compose(confirmed ->
                            transferRepository.unlockBalance(client, et.getWalletId(), totalDeduct, false)
                                .map(w -> confirmed));
                }));
    }

    /**
      * Normalized comment.
      * Normalized comment.
      * Normalized comment.
     */
    public Future<ExternalTransfer> failExternalTransferAndRefund(String transferId, String errorCode, String errorMessage) {
        if (redisApi != null) {
            String key = REDIS_KEY_FAILED + transferId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("Normalized log message", transferId);
                        return transferRepository.getExternalTransferById(pool, transferId);
                    }
                    return doFailExternalTransferAndRefund(transferId, errorCode, errorMessage)
                        .compose(et -> redisApi.setex(key, String.valueOf(CONFIRMED_IDEMPOTENCY_TTL_SECONDS), "1")
                            .map(v -> et));
                });
        }
        return doFailExternalTransferAndRefund(transferId, errorCode, errorMessage);
    }

    private Future<ExternalTransfer> doFailExternalTransferAndRefund(String transferId, String errorCode, String errorMessage) {
        return pool.withTransaction(client ->
            transferRepository.getExternalTransferById(client, transferId)
                .compose(et -> {
                    if (et == null) {
                        return Future.failedFuture(new NotFoundException("Resource not found." + transferId));
                    }
                    if (ExternalTransfer.STATUS_CONFIRMED.equals(et.getStatus()) || ExternalTransfer.STATUS_FAILED.equals(et.getStatus())) {
                        return Future.failedFuture(new BadRequestException("Invalid request." + et.getStatus()));
                    }
                    BigDecimal totalRefund = et.getAmount().add(et.getFee() != null ? et.getFee() : BigDecimal.ZERO);
                    return transferRepository.failExternalTransfer(client, transferId, errorCode, errorMessage)
                        .compose(failed ->
                            transferRepository.unlockBalance(client, et.getWalletId(), totalRefund, true)
                                .map(w -> failed));
                }));
    }

    /**
      * Normalized comment.
     */
    public Future<TransferHistoryResponseDto> getTransferHistory(Long userId, int limit, int offset) {
        int fetchSize = Math.max(limit + offset, limit * 4);
        Future<List<AirdropTransfer>> airdropFuture = (airdropRepository != null)
            ? airdropRepository.getTransfersByUserId(pool, userId, fetchSize, 0)
            : Future.succeededFuture(List.of());
        Future<List<TokenDeposit>> tokenDepositFuture = tokenDepositRepository != null
            ? tokenDepositRepository.getTokenDepositsByUserId(pool, userId, null, fetchSize, 0)
            : Future.succeededFuture(List.of());
        Future<List<PaymentDeposit>> paymentDepositFuture = paymentDepositRepository != null
            ? paymentDepositRepository.getPaymentDepositsByUserId(pool, userId, null, fetchSize, 0)
            : Future.succeededFuture(List.of());
        Future<List<Swap>> swapFuture = swapRepository != null
            ? swapRepository.getSwapsByUserId(pool, userId, fetchSize, 0)
            : Future.succeededFuture(List.of());
        Future<List<Exchange>> exchangeFuture = exchangeRepository != null
            ? exchangeRepository.getExchangesByUserId(pool, userId, fetchSize, 0)
            : Future.succeededFuture(List.of());

        return transferRepository.getInternalTransfersByUserId(pool, userId, fetchSize, 0)
            .compose(internalTransfers ->
                transferRepository.getExternalTransfersByUserId(pool, userId, fetchSize, 0)
                    .compose(externalTransfers ->
                        Future.all(List.of(
                            airdropFuture,
                            tokenDepositFuture,
                            paymentDepositFuture,
                            swapFuture,
                            exchangeFuture
                        )).compose(auxResults -> {
                            List<AirdropTransfer> airdropTransfers = airdropFuture.result();
                            List<TokenDeposit> tokenDeposits = tokenDepositFuture.result();
                            List<PaymentDeposit> paymentDeposits = paymentDepositFuture.result();
                            List<Swap> swaps = swapFuture.result();
                            List<Exchange> exchanges = exchangeFuture.result();
                            // Normalized comment.
                            List<Future<TransferResponseDto>> internalDtos = internalTransfers.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("INTERNAL")
                                        .transactionType(resolveInternalTransactionType(t))
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(t.getSenderId())
                                        .receiverId(t.getReceiverId())
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(t.getFee())
                                        .status(t.getStatus())
                                        .memo(t.getMemo())
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getCompletedAt())
                                        .build()))
                                .collect(Collectors.toList());

                            // Normalized comment.
                            List<Future<TransferResponseDto>> externalDtos = externalTransfers.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("EXTERNAL")
                                        .transactionType(t.getTransactionType())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(t.getUserId())
                                        .toAddress(t.getToAddress())
                                        .currencyCode(currency.getCode())
                                        .network(t.getChain())
                                        .amount(t.getAmount())
                                        .fee(t.getFee())
                                        .networkFee(t.getNetworkFee())
                                        .status(t.getStatus())
                                        .txHash(t.getTxHash())
                                        .memo(t.getMemo())
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getConfirmedAt())
                                        .build()))
                                .collect(Collectors.toList());

                            List<Future<TransferResponseDto>> tokenDepositDtos = tokenDeposits.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getDepositId())
                                        .transferType("INTERNAL")
                                        .transactionType(TransactionType.TOKEN_DEPOSIT.getValue())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(t.getUserId())
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(BigDecimal.ZERO)
                                        .status(t.getStatus())
                                        .network(t.getNetwork())
                                        .senderAddress(t.getSenderAddress())
                                        .toAddress(t.getToAddress())
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getConfirmedAt())
                                        .build()))
                                .collect(Collectors.toList());

                            List<Future<TransferResponseDto>> paymentDepositDtos = paymentDeposits.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getDepositId())
                                        .transferType("INTERNAL")
                                        .transactionType(TransactionType.PAYMENT_DEPOSIT.getValue())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(t.getUserId())
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(BigDecimal.ZERO)
                                        .status(t.getStatus())
                                        .depositMethod(t.getDepositMethod())
                                        .paymentAmount(t.getPaymentAmount())
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getCompletedAt())
                                        .build()))
                                .collect(Collectors.toList());

                            List<Future<TransferResponseDto>> swapDtos = swaps.stream()
                                .map(t -> Future.all(List.of(
                                        currencyRepository.getCurrencyById(pool, t.getFromCurrencyId()),
                                        currencyRepository.getCurrencyById(pool, t.getToCurrencyId())
                                    ))
                                    .map(results -> {
                                        Currency fromCurrency = (Currency) results.resultAt(0);
                                        Currency toCurrency = (Currency) results.resultAt(1);
                                        return TransferResponseDto.builder()
                                            .transferId(t.getSwapId())
                                            .transferType("INTERNAL")
                                            .transactionType(TransactionType.SWAP.getValue())
                                            .orderNumber(t.getOrderNumber())
                                            .senderId(t.getUserId())
                                            .currencyCode(fromCurrency.getCode())
                                            .fromCurrencyCode(fromCurrency.getCode())
                                            .toCurrencyCode(toCurrency.getCode())
                                            .amount(t.getFromAmount())
                                            .fromAmount(t.getFromAmount())
                                            .toAmount(t.getToAmount())
                                            .fee(BigDecimal.ZERO)
                                            .status(t.getStatus())
                                            .network(t.getNetwork())
                                            .createdAt(t.getCreatedAt())
                                            .completedAt(t.getCompletedAt())
                                            .build();
                                    }))
                                .collect(Collectors.toList());

                            List<Future<TransferResponseDto>> exchangeDtos = exchanges.stream()
                                .map(t -> Future.all(List.of(
                                        currencyRepository.getCurrencyById(pool, t.getFromCurrencyId()),
                                        currencyRepository.getCurrencyById(pool, t.getToCurrencyId())
                                    ))
                                    .map(results -> {
                                        Currency fromCurrency = (Currency) results.resultAt(0);
                                        Currency toCurrency = (Currency) results.resultAt(1);
                                        return TransferResponseDto.builder()
                                            .transferId(t.getExchangeId())
                                            .transferType("INTERNAL")
                                            .transactionType(TransactionType.EXCHANGE.getValue())
                                            .orderNumber(t.getOrderNumber())
                                            .senderId(t.getUserId())
                                            .currencyCode(fromCurrency.getCode())
                                            .fromCurrencyCode(fromCurrency.getCode())
                                            .toCurrencyCode(toCurrency.getCode())
                                            .amount(t.getFromAmount())
                                            .fromAmount(t.getFromAmount())
                                            .toAmount(t.getToAmount())
                                            .fee(BigDecimal.ZERO)
                                            .status(t.getStatus())
                                            .createdAt(t.getCreatedAt())
                                            .completedAt(t.getCompletedAt())
                                            .build();
                                    }))
                                .collect(Collectors.toList());

                            Set<String> internalOrderNumbers = internalTransfers.stream()
                                .map(InternalTransfer::getOrderNumber)
                                .filter(o -> o != null && !o.isEmpty())
                                .collect(Collectors.toSet());

                            // Normalized comment.
                            List<Future<TransferResponseDto>> airdropDtos = airdropTransfers.stream()
                                .filter(at -> {
                                    String on = at.getOrderNumber();
                                    return on != null && !on.isEmpty() && !internalOrderNumbers.contains(on);
                                })
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("INTERNAL")
                                        .transactionType(TransactionType.AIRDROP_TRANSFER.getValue())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(userId)
                                        .receiverId(userId)
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(BigDecimal.ZERO)
                                        .status(t.getStatus())
                                        .memo("POINT_EXCHANGE_ADJUSTMENT")
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt())
                                        .build()))
                                .collect(Collectors.toList());

                            List<Future<TransferResponseDto>> allDtos = new ArrayList<>();
                            allDtos.addAll(internalDtos);
                            allDtos.addAll(externalDtos);
                            allDtos.addAll(tokenDepositDtos);
                            allDtos.addAll(paymentDepositDtos);
                            allDtos.addAll(swapDtos);
                            allDtos.addAll(exchangeDtos);
                            allDtos.addAll(airdropDtos);

                            return Future.all(allDtos)
                                .map(results -> {
                                    List<TransferResponseDto> allTransfers = results.list();
                                    allTransfers.sort((a, b) -> {
                                        LocalDateTime aTime = a.getCreatedAt() != null ? a.getCreatedAt() : a.getCompletedAt();
                                        LocalDateTime bTime = b.getCreatedAt() != null ? b.getCreatedAt() : b.getCompletedAt();
                                        if (aTime == null && bTime == null) return 0;
                                        if (aTime == null) return 1;
                                        if (bTime == null) return -1;
                                        return bTime.compareTo(aTime);
                                    });
                                    int total = allTransfers.size();
                                    List<TransferResponseDto> limited = allTransfers.stream()
                                        .skip(offset)
                                        .limit(limit)
                                        .collect(Collectors.toList());
                                    return TransferHistoryResponseDto.builder()
                                        .transfers(limited)
                                        .total(total)
                                        .limit(limit)
                                        .offset(offset)
                                        .build();
                                });
                        })));
    }
    
    /**
      * Normalized comment.
     */
    private String resolveInternalTransactionType(InternalTransfer t) {
        if (InternalTransfer.TYPE_REFERRAL_REWARD.equals(t.getTransferType())) {
            return TransactionType.REFERRAL_REWARD.getValue();
        }
        if (InternalTransfer.TYPE_ADMIN_GRANT.equals(t.getTransferType())) {
            String memo = t.getMemo();
            if (memo != null && memo.contains("POINT_EXCHANGE")) {
                return TransactionType.AIRDROP_TRANSFER.getValue();
            }
        }
        return t.getTransactionType() != null ? t.getTransactionType() : TransactionType.TOKEN_DEPOSIT.getValue();
    }

    /**
      * Normalized comment.
     */
    public Future<TransferResponseDto> getTransferDetail(String transferId) {
        // Normalized comment.
        return transferRepository.getInternalTransferById(pool, transferId)
            .compose(internalTransfer -> {
                if (internalTransfer != null) {
                    return currencyRepository.getCurrencyById(pool, internalTransfer.getCurrencyId())
                        .map(currency -> TransferResponseDto.builder()
                            .transferId(internalTransfer.getTransferId())
                            .transferType("INTERNAL")
                            .transactionType(resolveInternalTransactionType(internalTransfer))
                            .orderNumber(internalTransfer.getOrderNumber())
                            .senderId(internalTransfer.getSenderId())
                            .receiverId(internalTransfer.getReceiverId())
                            .currencyCode(currency.getCode())
                            .amount(internalTransfer.getAmount())
                            .fee(internalTransfer.getFee())
                            .status(internalTransfer.getStatus())
                            .memo(internalTransfer.getMemo())
                            .createdAt(internalTransfer.getCreatedAt())
                            .completedAt(internalTransfer.getCompletedAt())
                            .build());
                }
                
                // Normalized comment.
                return transferRepository.getExternalTransferById(pool, transferId)
                    .compose(externalTransfer -> {
                        if (externalTransfer == null) {
                            return Future.succeededFuture(null);
                        }
                        return currencyRepository.getCurrencyById(pool, externalTransfer.getCurrencyId())
                            .map(currency -> TransferResponseDto.builder()
                                .transferId(externalTransfer.getTransferId())
                                .transferType("EXTERNAL")
                                .transactionType(externalTransfer.getTransactionType())
                                .orderNumber(externalTransfer.getOrderNumber())
                                .senderId(externalTransfer.getUserId())
                                .toAddress(externalTransfer.getToAddress())
                                .currencyCode(currency.getCode())
                                .network(externalTransfer.getChain())
                                .amount(externalTransfer.getAmount())
                                .fee(externalTransfer.getFee())
                                .networkFee(externalTransfer.getNetworkFee())
                                .status(externalTransfer.getStatus())
                                .txHash(externalTransfer.getTxHash())
                                .memo(externalTransfer.getMemo())
                                .createdAt(externalTransfer.getCreatedAt())
                                .completedAt(externalTransfer.getConfirmedAt())
                                .build());
                    });
            });
    }
}
