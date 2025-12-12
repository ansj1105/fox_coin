package com.foxya.coin.swap;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.CurrencyService;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.swap.dto.SwapRequestDto;
import com.foxya.coin.swap.dto.SwapResponseDto;
import com.foxya.coin.swap.dto.SwapQuoteDto;
import com.foxya.coin.swap.dto.SwapCurrenciesDto;
import com.foxya.coin.swap.dto.SwapInfoDto;
import com.foxya.coin.swap.entities.Swap;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SwapService extends BaseService {
    
    private final SwapRepository swapRepository;
    private final CurrencyRepository currencyRepository;
    private final CurrencyService currencyService;
    private final TransferRepository transferRepository;
    
    // 스왑 수수료 (0.0%)
    private static final BigDecimal SWAP_FEE_RATE = BigDecimal.ZERO;
    // 스프레드 (0.0%)
    private static final BigDecimal SWAP_SPREAD_RATE = BigDecimal.ZERO;
    // 최소 스왑 금액
    private static final BigDecimal MIN_SWAP_AMOUNT = new BigDecimal("0.000001");
    // KRWT 최소 스왑 금액
    private static final BigDecimal MIN_SWAP_AMOUNT_KRWT = new BigDecimal("1000.0");
    
    // 임시 환율 (Oracle 연동 전까지 사용)
    // KRWT 기준 환율
    private static final BigDecimal RATE_ETH = new BigDecimal("5000000.0");
    private static final BigDecimal RATE_USDT = new BigDecimal("1300.0");
    private static final BigDecimal RATE_KRWT = BigDecimal.ONE;
    
    public SwapService(PgPool pool, SwapRepository swapRepository, 
                      CurrencyRepository currencyRepository,
                      CurrencyService currencyService,
                      TransferRepository transferRepository) {
        super(pool);
        this.swapRepository = swapRepository;
        this.currencyRepository = currencyRepository;
        this.currencyService = currencyService;
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
                                
                                // 4. 환율 계산 (CurrencyService 사용)
                                BigDecimal exchangeRate = currencyService.getExchangeRate(request.getFromCurrencyCode(), request.getToCurrencyCode());
                                
                                // 5. 수수료 계산
                                BigDecimal feeAmount = request.getFromAmount().multiply(SWAP_FEE_RATE).setScale(18, RoundingMode.DOWN);
                                
                                // 6. 스프레드 계산
                                BigDecimal spreadAmount = request.getFromAmount().multiply(exchangeRate).multiply(SWAP_SPREAD_RATE).setScale(18, RoundingMode.DOWN);
                                
                                // 7. TO 금액 계산: toAmount = (fromAmount * exchangeRate) - feeAmount - spreadAmount
                                BigDecimal toAmount = request.getFromAmount().multiply(exchangeRate)
                                    .subtract(feeAmount)
                                    .subtract(spreadAmount)
                                    .setScale(18, RoundingMode.DOWN);
                                
                                // 8. 잔액 확인
                                if (fromWallet.getBalance().compareTo(request.getFromAmount()) < 0) {
                                    return Future.failedFuture(new BadRequestException("잔액이 부족합니다."));
                                }
                                
                                // 9. 스왑 실행
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
    
    /**
     * 스왑 예상 수량 조회
     */
    public Future<SwapQuoteDto> getSwapQuote(String fromCurrencyCode, String toCurrencyCode, 
                                             BigDecimal fromAmount, String network) {
        // 1. 통화 조회
        return currencyRepository.getCurrencyByCodeAndChain(pool, fromCurrencyCode, network)
            .compose(fromCurrency -> {
                if (fromCurrency == null) {
                    return Future.failedFuture(new NotFoundException("FROM 통화를 찾을 수 없습니다: " + fromCurrencyCode));
                }
                
                return currencyRepository.getCurrencyByCodeAndChain(pool, toCurrencyCode, network)
                    .map(toCurrency -> {
                        if (toCurrency == null) {
                            throw new NotFoundException("TO 통화를 찾을 수 없습니다: " + toCurrencyCode);
                        }
                        
                                // 2. 환율 계산 (CurrencyService 사용)
                        BigDecimal exchangeRate = currencyService.getExchangeRate(fromCurrencyCode, toCurrencyCode);
                        
                        // 3. 수수료 계산
                        BigDecimal fee = SWAP_FEE_RATE;
                        BigDecimal feeAmount = fromAmount.multiply(fee).setScale(18, RoundingMode.DOWN);
                        
                        // 4. 스프레드 계산
                        BigDecimal spread = SWAP_SPREAD_RATE;
                        BigDecimal spreadAmount = fromAmount.multiply(exchangeRate).multiply(spread).setScale(18, RoundingMode.DOWN);
                        
                        // 5. TO 금액 계산: toAmount = (fromAmount * exchangeRate) - feeAmount - spreadAmount
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
     * 스왑 가능한 통화 목록 조회
     */
    public Future<SwapCurrenciesDto> getSwapCurrencies() {
        return currencyRepository.getAllActiveCurrencies(pool)
            .map(currencies -> {
                List<SwapCurrenciesDto.CurrencyInfo> currencyInfos = currencies.stream()
                    .filter(c -> !"INTERNAL".equals(c.getChain())) // INTERNAL 체인 제외 (스왑 불가)
                    .map(c -> SwapCurrenciesDto.CurrencyInfo.builder()
                        .code(c.getCode())
                        .name(c.getName())
                        .symbol(c.getCode()) // 임시로 code를 symbol로 사용
                        .network(c.getChain())
                        .decimals(18) // 임시로 18로 설정 (실제로는 DB에 저장되어야 함)
                        .minSwapAmount("KRWT".equals(c.getCode()) ? MIN_SWAP_AMOUNT_KRWT : MIN_SWAP_AMOUNT)
                        .build())
                    .collect(Collectors.toList());
                
                return SwapCurrenciesDto.builder()
                    .currencies(currencyInfos)
                    .build();
            });
    }
    
    /**
     * 스왑 정보 조회
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
            .note("실시간 가격: (Oracle) 시세 기준")
            .build());
    }
    
}

