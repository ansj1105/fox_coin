package com.foxya.coin.deposit;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.deposit.dto.TokenDepositListResponseDto;
import com.foxya.coin.deposit.entities.TokenDeposit;
import com.foxya.coin.transfer.TransferRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TokenDepositService extends BaseService {
    
    private final TokenDepositRepository tokenDepositRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    
    public TokenDepositService(PgPool pool, TokenDepositRepository tokenDepositRepository,
                              CurrencyRepository currencyRepository,
                              TransferRepository transferRepository) {
        super(pool);
        this.tokenDepositRepository = tokenDepositRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
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
}

