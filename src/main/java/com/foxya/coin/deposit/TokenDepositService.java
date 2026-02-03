package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.deposit.dto.TokenDepositListResponseDto;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.transfer.TransferRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository) {
        this(pool, tokenDepositRepository, currencyRepository, transferRepository, null);
    }

    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository,
                              RedisAPI redisApi) {
        super(pool);
        this.tokenDepositRepository = tokenDepositRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.redisApi = redisApi;
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
                            .map(v -> deposit));
                });
        }
        return doCompleteTokenDeposit(depositId);
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

