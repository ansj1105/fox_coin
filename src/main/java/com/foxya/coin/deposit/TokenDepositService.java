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

    /** Redis 硫깅벑 ??TTL (7?? */
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
     * ?좏겙 ?낃툑 紐⑸줉 議고쉶
     */
    public Future<TokenDepositListResponseDto> getTokenDeposits(Long userId, String currencyCode, int limit, int offset) {
        Integer currencyId = null;
        
        if (currencyCode != null && !currencyCode.isEmpty()) {
            return currencyRepository.getCurrencyByCode(pool, currencyCode)
                .compose(currency -> {
                    if (currency == null) {
                        return Future.failedFuture(new NotFoundException("?듯솕瑜?李얠쓣 ???놁뒿?덈떎: " + currencyCode));
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
                // 珥?媛쒖닔 議고쉶 (媛꾨떒?섍쾶 ?꾩옱 議고쉶??媛쒖닔濡??泥? ?ㅼ젣濡쒕뒗 蹂꾨룄 COUNT 荑쇰━ ?꾩슂)
                long total = deposits.size();
                
                // ?듯솕 ?뺣낫 留ㅽ븨
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
     * ?좏겙 ?낃툑 媛먯? ?깅줉 (釉붾줉 ?ㅼ틦???뚯빱?먯꽌 ?몄텧).
     * ?⑥껜?몄뿉???낃툑 ?몃옖??뀡??媛먯??덉쓣 ??PENDING ?덉퐫?쒕? ?앹꽦?섍퀬 DEPOSIT_DETECTED ?대깽?몃? 諛쒗뻾?섏뿬
     * ?대씪?댁뼵?멸? "泥섎━ 以?Processing)" ?곹깭瑜??대쭅 ?놁씠 ?????덇쾶 ?쒕떎.
     */
    public Future<TokenDeposit> registerTokenDeposit(TokenDeposit deposit) {
        if (deposit == null) {
            return Future.failedFuture(new BadRequestException("?낃툑 ?뺣낫媛 ?놁뒿?덈떎."));
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
     * ?낃툑 媛먯? ??DEPOSIT_DETECTED ?대깽??諛쒗뻾 (?대씪?댁뼵?멸? "泥섎━ 以? ?곹깭瑜??????덈룄濡?.
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
                log.warn("DEPOSIT_DETECTED ?대깽??諛쒗뻾 ?ㅽ뙣(?깅줉? ?좎?): depositId={}", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    /**
     * ?좏겙 ?낃툑 ?꾨즺 泥섎━ (Node.js ?낃툑 媛먯? ?뚯빱 ?깆뿉???몄텧).
     * 肄붿씤 ?붿븸(?⑥껜?? ?뺤씤 ???대? 吏媛묒뿉 諛섏쁺: ?몃옖??뀡 ??吏媛??붿븸 異붽? + ?낃툑 ?곹깭 COMPLETED.
     * Redis 硫깅벑 ?ㅻ줈 以묐났 吏湲?諛⑹?.
     */
    public Future<TokenDeposit> completeTokenDeposit(String depositId) {
        if (redisApi != null) {
            String key = REDIS_KEY_DEPOSIT_COMPLETED + depositId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("?좏겙 ?낃툑 ?대? ?꾨즺 泥섎━??(硫깅벑) - depositId: {}", depositId);
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

    /** ?낃툑 ?꾨즺 ??notifications ?몄꽌??(???뚮엺, 異뷀썑 FCM ?몄떆 ?쒖슜) */
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
                log.warn("?낃툑 ?꾨즺 ?뚮┝ ?앹꽦 ?ㅽ뙣(臾댁떆): depositId={}", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    /**
     * ?낃툑 ?꾨즺 ??DEPOSIT_CONFIRMED ?대깽??諛쒗뻾 (?대씪?댁뼵??援щ룆?먭? ?대쭅 ?놁씠 ?낃툑 諛섏쁺 ?쒖젏???????덈룄濡?.
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
                log.warn("DEPOSIT_CONFIRMED ?대깽??諛쒗뻾 ?ㅽ뙣(?낃툑 ?꾨즺???좎?): depositId={}", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    private Future<TokenDeposit> doCompleteTokenDeposit(String depositId) {
        return tokenDepositRepository.getTokenDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("?좏겙 ?낃툑??李얠쓣 ???놁뒿?덈떎: " + depositId));
                }
                if (!TokenDeposit.STATUS_PENDING.equals(deposit.getStatus())) {
                    return Future.failedFuture(new BadRequestException("?대? 泥섎━???낃툑?낅땲?? status=" + deposit.getStatus()));
                }
                if (deposit.getUserId() == null) {
                    return Future.failedFuture(new BadRequestException("?낃툑???ъ슜??留ㅼ묶???놁뒿?덈떎. depositId=" + depositId));
                }
                return pool.withTransaction(client ->
                    transferRepository.getWalletByUserIdAndCurrencyId(client, deposit.getUserId(), deposit.getCurrencyId())
                        .compose(wallet -> {
                            if (wallet == null) {
                                return Future.failedFuture(new NotFoundException("吏媛묒쓣 李얠쓣 ???놁뒿?덈떎. userId=" + deposit.getUserId()));
                            }
                            return transferRepository.addBalance(client, wallet.getId(), deposit.getAmount())
                                .compose(updated -> tokenDepositRepository.completeTokenDeposit(client, depositId));
                        }));
            });
    }
}

