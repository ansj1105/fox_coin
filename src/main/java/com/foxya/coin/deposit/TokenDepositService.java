package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseService;
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

    private final TokenDepositRepository tokenDepositRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    private final RedisAPI redisApi;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, null, null, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, null, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi,
                              EventPublisher eventPublisher) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, redisApi, eventPublisher, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi,
                              EventPublisher eventPublisher,
                              NotificationService notificationService) {
        super(pool);
        this.tokenDepositRepository = tokenDepositRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.redisApi = redisApi;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
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
                            .status(deposit.getStatus())
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
                        .compose(this::createDepositCompletedNotification);
                });
        }
        return doCompleteTokenDeposit(depositId)
            .compose(this::publishDepositConfirmedIfPresent)
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
                    return Future.failedFuture(new BadRequestException("Invalid request." + deposit.getStatus()));
                }
                if (deposit.getUserId() == null) {
                    return Future.failedFuture(new BadRequestException("Invalid request." + depositId));
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

