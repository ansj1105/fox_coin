package com.foxya.coin.swap;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.swap.dto.SwapRequestDto;
import com.foxya.coin.swap.dto.SwapResponseDto;
import com.foxya.coin.swap.entities.Swap;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
public class SwapService extends BaseService {
    
    private final SwapRepository swapRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    
    // 스왑 수수료 (0.5%)
    private static final BigDecimal SWAP_FEE_RATE = new BigDecimal("0.005");
    // 최소 스왑 금액
    private static final BigDecimal MIN_SWAP_AMOUNT = new BigDecimal("0.000001");
    
    public SwapService(PgPool pool, SwapRepository swapRepository, 
                      CurrencyRepository currencyRepository,
                      TransferRepository transferRepository) {
        super(pool);
        this.swapRepository = swapRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
    }
    
    /**
     * 스왑 실행
     */
    public Future<SwapResponseDto> executeSwap(Long userId, SwapRequestDto request, String requestIp) {
        log.info("스왑 실행 요청 - userId: {}, fromCurrency: {}, toCurrency: {}, fromAmount: {}, network: {}", 
            userId, request.getFromCurrencyCode(), request.getToCurrencyCode(), request.getFromAmount(), request.getNetwork());
        
        // 1. 유효성 검사
        if (request.getFromAmount() == null || request.getFromAmount().compareTo(MIN_SWAP_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 스왑 금액은 " + MIN_SWAP_AMOUNT + " 입니다."));
        }
        
        if (request.getFromCurrencyCode() == null || request.getToCurrencyCode() == null) {
            return Future.failedFuture(new BadRequestException("통화 코드를 입력해주세요."));
        }
        
        if (request.getFromCurrencyCode().equals(request.getToCurrencyCode())) {
            return Future.failedFuture(new BadRequestException("같은 통화로는 스왑할 수 없습니다."));
        }
        
        // 2. 통화 조회
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getFromCurrencyCode(), request.getNetwork())
            .compose(fromCurrency -> {
                if (fromCurrency == null) {
                    return Future.failedFuture(new NotFoundException("FROM 통화를 찾을 수 없습니다: " + request.getFromCurrencyCode()));
                }
                
                return currencyRepository.getCurrencyByCodeAndChain(pool, request.getToCurrencyCode(), request.getNetwork())
                    .compose(toCurrency -> {
                        if (toCurrency == null) {
                            return Future.failedFuture(new NotFoundException("TO 통화를 찾을 수 없습니다: " + request.getToCurrencyCode()));
                        }
                        
                        // 3. 사용자 지갑 조회
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, fromCurrency.getId())
                            .compose(fromWallet -> {
                                if (fromWallet == null) {
                                    return Future.failedFuture(new NotFoundException("FROM 지갑을 찾을 수 없습니다."));
                                }
                                
                                // 4. 수수료 계산 및 TO 금액 계산 (간단한 환율 계산, 실제로는 외부 API 사용)
                                BigDecimal fee = request.getFromAmount().multiply(SWAP_FEE_RATE);
                                BigDecimal netAmount = request.getFromAmount().subtract(fee);
                                // TODO: 실제 환율 API 연동 필요
                                BigDecimal exchangeRate = BigDecimal.ONE; // 임시로 1:1
                                BigDecimal toAmount = netAmount.multiply(exchangeRate).setScale(18, RoundingMode.DOWN);
                                
                                // 5. 잔액 확인
                                if (fromWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                                    return Future.failedFuture(new BadRequestException("잔액이 부족합니다."));
                                }
                                
                                // 6. 스왑 실행
                                return executeSwapTransaction(userId, fromCurrency, toCurrency, fromWallet, 
                                    request.getFromAmount(), toAmount, request.getNetwork(), requestIp);
                            });
                    });
            });
    }
    
    /**
     * 스왑 트랜잭션 실행
     */
    private Future<SwapResponseDto> executeSwapTransaction(Long userId, Currency fromCurrency, Currency toCurrency,
                                                           Wallet fromWallet, BigDecimal fromAmount, BigDecimal toAmount,
                                                           String network, String requestIp) {
        String swapId = UUID.randomUUID().toString();
        String orderNumber = OrderNumberUtils.generateOrderNumber();
        
        return pool.withTransaction(client -> {
            // 1. FROM 지갑 잔액 차감
            return transferRepository.deductBalance(client, fromWallet.getId(), fromAmount)
                .compose(updatedFromWallet -> {
                    if (updatedFromWallet == null) {
                        return Future.failedFuture(new BadRequestException("잔액 차감 실패"));
                    }
                    
                    // 2. TO 지갑 조회 또는 생성
                    return transferRepository.getWalletByUserIdAndCurrencyId(client, userId, toCurrency.getId())
                        .compose(toWallet -> {
                            if (toWallet == null) {
                                return Future.failedFuture(new NotFoundException("TO 지갑을 찾을 수 없습니다."));
                            }
                            
                            // 3. TO 지갑 잔액 추가
                            return transferRepository.addBalance(client, toWallet.getId(), toAmount)
                                .compose(updatedToWallet -> {
                                    // 4. 스왑 기록 생성
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
                });
        });
    }
    
    /**
     * 스왑 상세 조회
     */
    public Future<SwapResponseDto> getSwap(Long userId, String swapId) {
        return swapRepository.getSwapBySwapId(pool, swapId)
            .compose(swap -> {
                if (swap == null) {
                    return Future.failedFuture(new NotFoundException("스왑을 찾을 수 없습니다."));
                }
                
                if (!swap.getUserId().equals(userId)) {
                    return Future.failedFuture(new BadRequestException("권한이 없습니다."));
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
}

