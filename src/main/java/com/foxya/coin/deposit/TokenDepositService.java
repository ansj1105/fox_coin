package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.app.AppConfigRepository;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.deposit.dto.TokenDepositListResponseDto;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventType;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.WalletRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TokenDepositService extends BaseService {

    /** Normalized comment. */
    private static final int DEPOSIT_COMPLETED_IDEMPOTENCY_TTL_SECONDS = 7 * 24 * 3600;
    private static final String REDIS_KEY_DEPOSIT_COMPLETED = "deposit:completed:";
    private static final String SWEEP_ENABLED_PREFIX = "sweep_enabled.";
    private static final String SWEEP_MIN_PREFIX = "sweep_min_amount.";
    private static final String SWEEP_GAS_PAYER_KEY = "sweep_gas_payer";
    private static final String HOT_WALLET_USER_ID_KEY = "hot_wallet_user_id";

    private final TokenDepositRepository tokenDepositRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    private final WalletRepository walletRepository;
    private final RedisAPI redisApi;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final AppConfigRepository appConfigRepository;

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, null, null, null, null, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, null, null, null, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi,
                              EventPublisher eventPublisher) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, eventPublisher, null, null, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi,
                              EventPublisher eventPublisher,
                              NotificationService notificationService,
                              WalletRepository walletRepository,
                              AppConfigRepository appConfigRepository) {
        super(pool);
        this.tokenDepositRepository = tokenDepositRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.redisApi = redisApi;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.walletRepository = walletRepository;
        this.appConfigRepository = appConfigRepository;
    }
    
    /**
      * Normalized comment.
     */
    public Future<TokenDepositListResponseDto> getTokenDeposits(Long userId, String currencyCode, int limit, int offset) {
        Integer currencyId = null;
        
        if (currencyCode != null && !currencyCode.isEmpty()) {
            return currencyRepository.getCurrencyByCode(pool, currencyCode)
                .compose(currency -> {
                    if (currency == null) {
                        return Future.failedFuture(new NotFoundException("Resource not found." + currencyCode));
                    }
                    
                    return getTokenDepositsWithCurrencyId(userId, currency.getId(), limit, offset);
                });
        } else {
            return getTokenDepositsWithCurrencyId(userId, null, limit, offset);
        }
    }
    
    private Future<TokenDepositListResponseDto> getTokenDepositsWithCurrencyId(Long userId, Integer currencyId, int limit, int offset) {
        return tokenDepositRepository.getTokenDepositsByUserId(pool, userId, currencyId, limit, offset)
            .compose(deposits -> {
                // Normalized comment.
                long total = deposits.size();
                
                // Normalized comment.
                List<Future<TokenDepositListResponseDto.TokenDepositInfo>> depositInfoFutures = deposits.stream()
                    .map(deposit -> currencyRepository.getCurrencyById(pool, deposit.getCurrencyId())
                        .map(currency -> TokenDepositListResponseDto.TokenDepositInfo.builder()
                            .depositId(deposit.getDepositId())
                            .orderNumber(deposit.getOrderNumber())
                            .currencyCode(currency.getCode())
                            .amount(deposit.getAmount())
                            .network(deposit.getNetwork())
                            .senderAddress(deposit.getSenderAddress())
                            .toAddress(deposit.getToAddress())
                            .logIndex(deposit.getLogIndex())
                            .blockNumber(deposit.getBlockNumber())
                            .txHash(deposit.getTxHash())
                            .status(deposit.getStatus())
                            .sweepStatus(deposit.getSweepStatus())
                            .sweepTxHash(deposit.getSweepTxHash())
                            .sweepRequestedAt(deposit.getSweepRequestedAt())
                            .sweepSubmittedAt(deposit.getSweepSubmittedAt())
                            .sweepFailedAt(deposit.getSweepFailedAt())
                            .sweepErrorMessage(deposit.getSweepErrorMessage())
                            .createdAt(deposit.getCreatedAt())
                            .build()))
                    .collect(Collectors.toList());
                
                return Future.all(depositInfoFutures)
                    .map(results -> {
                        List<TokenDepositListResponseDto.TokenDepositInfo> depositInfos = results.list();
                        return TokenDepositListResponseDto.builder()
                            .deposits(depositInfos)
                            .total(total)
                            .limit(limit)
                            .offset(offset)
                            .build();
                    });
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
      * Normalized comment.
     */
    public Future<TokenDeposit> registerTokenDeposit(TokenDeposit deposit) {
        if (deposit == null) {
            return Future.failedFuture(new BadRequestException("Invalid request."));
        }
        String orderNumber = deposit.getOrderNumber();
        if (orderNumber == null || orderNumber.isBlank()) {
            orderNumber = OrderNumberUtils.generateOrderNumber();
        }

        TokenDeposit toInsert = TokenDeposit.builder()
            .depositId(deposit.getDepositId())
            .userId(deposit.getUserId())
            .orderNumber(orderNumber)
            .currencyId(deposit.getCurrencyId())
            .amount(deposit.getAmount())
            .network(deposit.getNetwork())
            .senderAddress(deposit.getSenderAddress())
            .txHash(deposit.getTxHash())
            .status(TokenDeposit.STATUS_PENDING)
            .build();
        return tokenDepositRepository.createTokenDeposit(pool, toInsert)
            .compose(this::publishDepositDetectedIfPresent);
    }

    /**
      * Normalized comment.
     */
    private Future<TokenDeposit> publishDepositDetectedIfPresent(TokenDeposit deposit) {
        if (eventPublisher == null || deposit == null) {
            return Future.succeededFuture(deposit);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("depositId", deposit.getDepositId());
        payload.put("userId", deposit.getUserId());
        payload.put("currencyId", deposit.getCurrencyId());
        payload.put("amount", deposit.getAmount() != null ? deposit.getAmount().toPlainString() : null);
        payload.put("status", deposit.getStatus());
        payload.put("orderNumber", deposit.getOrderNumber());
        payload.put("txHash", deposit.getTxHash());
        payload.put("senderAddress", deposit.getSenderAddress());
        payload.put("network", deposit.getNetwork());
        payload.put("createdAt", deposit.getCreatedAt());
        return eventPublisher.publish(EventType.DEPOSIT_DETECTED, payload)
            .map(v -> deposit)
            .recover(err -> {
                log.warn("Normalized log message", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    /**
      * Normalized comment.
      * Normalized comment.
      * Normalized comment.
     */
    private io.vertx.core.Future<Boolean> isSweepEnabled(String chain, String currencyCode) {
        if (appConfigRepository == null) {
            return io.vertx.core.Future.succeededFuture(false);
        }
        String key = SWEEP_ENABLED_PREFIX + chain + "." + currencyCode;
        return appConfigRepository.getByKey(pool, key)
            .map(value -> value != null && ("true".equalsIgnoreCase(value) || "1".equals(value)));
    }

    private io.vertx.core.Future<java.math.BigDecimal> getSweepMinAmount(String chain, String currencyCode) {
        if (appConfigRepository == null) {
            return io.vertx.core.Future.succeededFuture(null);
        }
        String key = SWEEP_MIN_PREFIX + chain + "." + currencyCode;
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

    private io.vertx.core.Future<Long> getHotWalletUserId() {
        if (appConfigRepository == null) {
            return io.vertx.core.Future.succeededFuture(null);
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

    private io.vertx.core.Future<TokenDeposit> maybeRequestSweep(TokenDeposit deposit) {
        if (eventPublisher == null || deposit == null) {
            return io.vertx.core.Future.succeededFuture(deposit);
        }
        if (deposit.getUserId() == null || deposit.getAmount() == null || deposit.getNetwork() == null || deposit.getCurrencyId() == null) {
            return io.vertx.core.Future.succeededFuture(deposit);
        }
        return currencyRepository.getCurrencyById(pool, deposit.getCurrencyId())
            .compose(currency -> {
                if (currency == null) return io.vertx.core.Future.succeededFuture(deposit);
                String chain = deposit.getNetwork();
                String currencyCode = currency.getCode();
                return isSweepEnabled(chain, currencyCode)
                    .compose(enabled -> {
                        if (!enabled) return io.vertx.core.Future.succeededFuture(deposit);
                        return getSweepMinAmount(chain, currencyCode)
                            .compose(minAmount -> {
                                if (minAmount == null || deposit.getAmount().compareTo(minAmount) < 0) {
                                    return io.vertx.core.Future.succeededFuture(deposit);
                                }
                                return getHotWalletUserId()
                                    .compose(hotUserId -> {
                                        if (hotUserId == null) return io.vertx.core.Future.succeededFuture(deposit);
                                        if (walletRepository == null) return io.vertx.core.Future.succeededFuture(deposit);
                                        return walletRepository.getWalletByUserIdAndCurrencyId(pool, deposit.getUserId(), deposit.getCurrencyId())
                                            .compose(fromWallet -> {
                                                if (fromWallet == null || fromWallet.getAddress() == null) {
                                                    return io.vertx.core.Future.succeededFuture(deposit);
                                                }
                                                return walletRepository.getWalletByUserIdAndCurrencyId(pool, hotUserId, deposit.getCurrencyId())
                                                    .compose(toWallet -> {
                                                        if (toWallet == null || toWallet.getAddress() == null) {
                                                            return io.vertx.core.Future.succeededFuture(deposit);
                                                        }
                                                        if (TokenDeposit.SWEEP_STATUS_SUBMITTED.equalsIgnoreCase(deposit.getSweepStatus())) {
                                                            return io.vertx.core.Future.succeededFuture(deposit);
                                                        }
                                                        return tokenDepositRepository.markSweepRequested(pool, deposit.getDepositId())
                                                            .recover(err -> {
                                                                log.warn("Sweep requested update failed - depositId: {}", deposit.getDepositId(), err);
                                                                return io.vertx.core.Future.succeededFuture(deposit);
                                                            })
                                                            .map(updated -> {
                                                                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                                                                payload.put("depositId", deposit.getDepositId());
                                                        payload.put("userId", deposit.getUserId());
                                                        payload.put("fromAddress", fromWallet.getAddress());
                                                        payload.put("toAddress", toWallet.getAddress());
                                                        payload.put("amount", deposit.getAmount().toPlainString());
                                                        payload.put("currencyCode", currencyCode);
                                                        payload.put("chain", chain);
                                                        payload.put("gasPayer", "ADMIN");
                                                                eventPublisher.publishToStream(com.foxya.coin.event.EventType.SWEEP_REQUESTED, payload)
                                                                    .onFailure(err -> log.warn("Sweep request publish failed - depositId: {}", deposit.getDepositId(), err));
                                                                return updated != null ? updated : deposit;
                                                            });
                                                    });
                                            });
                                    });
                            });
                    });
            })
            .recover(err -> {
                log.warn("Sweep check failed - depositId: {}", deposit.getDepositId(), err);
                return io.vertx.core.Future.succeededFuture(deposit);
            });
    }


    public Future<TokenDeposit> submitSweepStatus(String depositId, String txHash) {
        return tokenDepositRepository.submitSweep(pool, depositId, txHash);
    }

    public Future<TokenDeposit> failSweepStatus(String depositId, String errorMessage) {
        return tokenDepositRepository.failSweep(pool, depositId, errorMessage);
    }

    public Future<TokenDeposit> getTokenDepositByDepositId(String depositId) {
        return tokenDepositRepository.getTokenDepositByDepositId(pool, depositId);
    }

    public Future<TokenDeposit> completeTokenDeposit(String depositId) {
        if (redisApi != null) {
            String key = REDIS_KEY_DEPOSIT_COMPLETED + depositId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("Normalized log message", depositId);
                        return tokenDepositRepository.getTokenDepositByDepositId(pool, depositId);
                    }
                    return doCompleteTokenDeposit(depositId)
                        .compose(deposit -> redisApi.setex(key, String.valueOf(DEPOSIT_COMPLETED_IDEMPOTENCY_TTL_SECONDS), "1")
                            .map(v -> deposit))
                        .compose(this::publishDepositConfirmedIfPresent)
                        .compose(this::maybeRequestSweep)
                        .compose(this::createDepositCompletedNotification);
                });
        }
        return doCompleteTokenDeposit(depositId)
            .compose(this::publishDepositConfirmedIfPresent)
            .compose(this::maybeRequestSweep)
            .compose(this::createDepositCompletedNotification);
    }

    /** Normalized comment. */
    private Future<TokenDeposit> createDepositCompletedNotification(TokenDeposit deposit) {
        if (notificationService == null || deposit == null || deposit.getUserId() == null) {
            return Future.succeededFuture(deposit);
        }
        return currencyRepository.getCurrencyById(pool, deposit.getCurrencyId())
            .compose(currency -> {
                String currencyCode = currency != null ? currency.getCode() : "";
                String amountStr = deposit.getAmount() != null ? deposit.getAmount().toPlainString() : "";
                String title = "\uC785\uAE08 \uC644\uB8CC";
                String message = amountStr + " " + currencyCode + " \uC785\uAE08\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
                JsonObject meta = new JsonObject()
                    .put("depositId", deposit.getDepositId())
                    .put("amount", amountStr)
                    .put("currencyCode", currencyCode)
                    .put("txHash", deposit.getTxHash());
                return notificationService.createNotificationIfAbsentByRelatedId(
                    deposit.getUserId(), NotificationType.DEPOSIT_SUCCESS, title, message, deposit.getId(), meta.encode()).mapEmpty();
            })
            .map(v -> deposit)
            .recover(err -> {
                log.warn("Normalized log message", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    /**
      * Normalized comment.
     */
    private Future<TokenDeposit> publishDepositConfirmedIfPresent(TokenDeposit deposit) {
        if (eventPublisher == null || deposit == null) {
            return Future.succeededFuture(deposit);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("depositId", deposit.getDepositId());
        payload.put("userId", deposit.getUserId());
        payload.put("currencyId", deposit.getCurrencyId());
        payload.put("amount", deposit.getAmount() != null ? deposit.getAmount().toPlainString() : null);
        payload.put("status", deposit.getStatus());
        payload.put("orderNumber", deposit.getOrderNumber());
        payload.put("txHash", deposit.getTxHash());
        payload.put("confirmedAt", deposit.getConfirmedAt());
        return eventPublisher.publish(EventType.DEPOSIT_CONFIRMED, payload)
            .map(v -> deposit)
            .recover(err -> {
                log.warn("Normalized log message", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    private Future<TokenDeposit> doCompleteTokenDeposit(String depositId) {
        return tokenDepositRepository.getTokenDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("Resource not found." + depositId));
                }
                if (!TokenDeposit.STATUS_PENDING.equals(deposit.getStatus())) {
                    return Future.failedFuture(new BadRequestException("이미 처리된 입금 상태입니다. currentStatus=" + deposit.getStatus()));
                }
                if (deposit.getUserId() == null) {
                    return Future.failedFuture(new BadRequestException("사용자 매칭이 없습니다. depositId=" + depositId));
                }
                return pool.withTransaction(client ->
                    transferRepository.getWalletByUserIdAndCurrencyId(client, deposit.getUserId(), deposit.getCurrencyId())
                        .compose(wallet -> {
                            if (wallet == null) {
                                return Future.failedFuture(new NotFoundException("Resource not found." + deposit.getUserId()));
                            }
                            return transferRepository.addBalance(client, wallet.getId(), deposit.getAmount())
                                .compose(updated -> tokenDepositRepository.completeTokenDeposit(client, depositId));
                        }));
            });
    }
}
