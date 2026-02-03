package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
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

    /** Redis 멱등 키 TTL (7일) */
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
     * 토큰 입금 목록 조회
     */
    public Future<TokenDepositListResponseDto> getTokenDeposits(Long userId, String currencyCode, int limit, int offset) {
        Integer currencyId = null;
        
        if (currencyCode != null && !currencyCode.isEmpty()) {
            return currencyRepository.getCurrencyByCode(pool, currencyCode)
                .compose(currency -> {
                    if (currency == null) {
                        return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyCode));
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
                // 총 개수 조회 (간단하게 현재 조회된 개수로 대체, 실제로는 별도 COUNT 쿼리 필요)
                long total = deposits.size();
                
                // 통화 정보 매핑
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
     * 토큰 입금 감지 등록 (블록 스캐너/워커에서 호출).
     * 온체인에서 입금 트랜잭션을 감지했을 때 PENDING 레코드를 생성하고 DEPOSIT_DETECTED 이벤트를 발행하여
     * 클라이언트가 "처리 중(Processing)" 상태를 폴링 없이 알 수 있게 한다.
     */
    public Future<TokenDeposit> registerTokenDeposit(TokenDeposit deposit) {
        if (deposit == null) {
            return Future.failedFuture(new BadRequestException("입금 정보가 없습니다."));
        }
        TokenDeposit toInsert = TokenDeposit.builder()
            .depositId(deposit.getDepositId())
            .userId(deposit.getUserId())
            .orderNumber(deposit.getOrderNumber())
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
     * 입금 감지 시 DEPOSIT_DETECTED 이벤트 발행 (클라이언트가 "처리 중" 상태를 알 수 있도록).
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
                log.warn("DEPOSIT_DETECTED 이벤트 발행 실패(등록은 유지): depositId={}", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    /**
     * 토큰 입금 완료 처리 (Node.js 입금 감지 워커 등에서 호출).
     * 코인 잔액(온체인) 확인 후 내부 지갑에 반영: 트랜잭션 내 지갑 잔액 추가 + 입금 상태 COMPLETED.
     * Redis 멱등 키로 중복 지급 방지.
     */
    public Future<TokenDeposit> completeTokenDeposit(String depositId) {
        if (redisApi != null) {
            String key = REDIS_KEY_DEPOSIT_COMPLETED + depositId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("토큰 입금 이미 완료 처리됨 (멱등) - depositId: {}", depositId);
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

    /** 입금 완료 시 notifications 인서트 (앱 알람, 추후 FCM 푸시 활용) */
    private Future<TokenDeposit> createDepositCompletedNotification(TokenDeposit deposit) {
        if (notificationService == null || deposit == null || deposit.getUserId() == null) {
            return Future.succeededFuture(deposit);
        }
        return currencyRepository.getCurrencyById(pool, deposit.getCurrencyId())
            .compose(currency -> {
                String currencyCode = currency != null ? currency.getCode() : "";
                String amountStr = deposit.getAmount() != null ? deposit.getAmount().toPlainString() : "";
                String title = "입금 완료";
                String message = amountStr + " " + currencyCode + " 입금이 완료되었습니다.";
                JsonObject meta = new JsonObject()
                    .put("depositId", deposit.getDepositId())
                    .put("amount", amountStr)
                    .put("currencyCode", currencyCode)
                    .put("txHash", deposit.getTxHash());
                return notificationService.createNotification(
                    deposit.getUserId(), NotificationType.DEPOSIT_SUCCESS, title, message, null, meta.encode());
            })
            .map(v -> deposit)
            .recover(err -> {
                log.warn("입금 완료 알림 생성 실패(무시): depositId={}", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    /**
     * 입금 완료 시 DEPOSIT_CONFIRMED 이벤트 발행 (클라이언트/구독자가 폴링 없이 입금 반영 시점을 알 수 있도록).
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
                log.warn("DEPOSIT_CONFIRMED 이벤트 발행 실패(입금 완료는 유지): depositId={}", deposit.getDepositId(), err);
                return Future.succeededFuture(deposit);
            });
    }

    private Future<TokenDeposit> doCompleteTokenDeposit(String depositId) {
        return tokenDepositRepository.getTokenDepositByDepositId(pool, depositId)
            .compose(deposit -> {
                if (deposit == null) {
                    return Future.failedFuture(new NotFoundException("토큰 입금을 찾을 수 없습니다: " + depositId));
                }
                if (!TokenDeposit.STATUS_PENDING.equals(deposit.getStatus())) {
                    return Future.failedFuture(new BadRequestException("이미 처리된 입금입니다. status=" + deposit.getStatus()));
                }
                if (deposit.getUserId() == null) {
                    return Future.failedFuture(new BadRequestException("입금에 사용자 매칭이 없습니다. depositId=" + depositId));
                }
                return pool.withTransaction(client ->
                    transferRepository.getWalletByUserIdAndCurrencyId(client, deposit.getUserId(), deposit.getCurrencyId())
                        .compose(wallet -> {
                            if (wallet == null) {
                                return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다. userId=" + deposit.getUserId()));
                            }
                            return transferRepository.addBalance(client, wallet.getId(), deposit.getAmount())
                                .compose(updated -> tokenDepositRepository.completeTokenDeposit(client, depositId));
                        }));
            });
    }
}

