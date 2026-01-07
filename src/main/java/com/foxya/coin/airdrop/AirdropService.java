package com.foxya.coin.airdrop;

import com.foxya.coin.airdrop.dto.AirdropPhaseDto;
import com.foxya.coin.airdrop.dto.AirdropStatusDto;
import com.foxya.coin.airdrop.dto.AirdropTransferRequestDto;
import com.foxya.coin.airdrop.dto.AirdropTransferResponseDto;
import com.foxya.coin.airdrop.entities.AirdropPhase;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class AirdropService extends BaseService {
    
    private final AirdropRepository airdropRepository;
    private final CurrencyRepository currencyRepository;
    private final TransferRepository transferRepository;
    
    // KORI 통화 코드
    private static final String KORI_CURRENCY_CODE = "KORI";
    private static final String INTERNAL_CHAIN = "INTERNAL";
    
    // 최소 전송 금액
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.000001");
    
    public AirdropService(PgPool pool,
                          AirdropRepository airdropRepository,
                          CurrencyRepository currencyRepository,
                          TransferRepository transferRepository) {
        super(pool);
        this.airdropRepository = airdropRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
    }
    
    /**
     * 에어드랍 상태 조회
     */
    public Future<AirdropStatusDto> getAirdropStatus(Long userId) {
        log.info("에어드랍 상태 조회 - userId: {}", userId);
        
        return airdropRepository.getPhasesByUserId(pool, userId)
            .compose(phases -> {
                if (phases == null || phases.isEmpty()) {
                    // Phase가 없으면 빈 상태 반환
                    return Future.succeededFuture(AirdropStatusDto.builder()
                        .totalReceived(BigDecimal.ZERO)
                        .totalReward(BigDecimal.ZERO)
                        .nextUnlockDays(null)
                        .phases(List.of())
                        .build());
                }
                
                // 현재 날짜 기준으로 상태 업데이트
                LocalDateTime now = LocalDateTime.now();
                List<AirdropPhaseDto> phaseDtos = phases.stream()
                    .map(phase -> {
                        // unlockDate가 지났으면 RELEASED로 변경
                        boolean isReleased = phase.getUnlockDate().isBefore(now) || phase.getUnlockDate().isEqual(now);
                        String status = isReleased ? AirdropPhase.STATUS_RELEASED : AirdropPhase.STATUS_PROCESSING;
                        
                        // daysRemaining 계산
                        Integer daysRemaining = null;
                        if (!isReleased) {
                            long days = ChronoUnit.DAYS.between(now, phase.getUnlockDate());
                            daysRemaining = (int) Math.max(0, days);
                        }
                        
                        return AirdropPhaseDto.builder()
                            .id(phase.getId())
                            .phase(phase.getPhase())
                            .status(status)
                            .amount(phase.getAmount())
                            .unlockDate(phase.getUnlockDate())
                            .daysRemaining(daysRemaining)
                            .build();
                    })
                    .sorted(Comparator.comparing(AirdropPhaseDto::getPhase))
                    .collect(Collectors.toList());
                
                // totalReceived 계산 (RELEASED 상태인 Phase들의 합)
                BigDecimal totalReceived = phaseDtos.stream()
                    .filter(p -> AirdropPhase.STATUS_RELEASED.equals(p.getStatus()))
                    .map(AirdropPhaseDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                // totalReward 계산 (모든 Phase의 합)
                BigDecimal totalReward = phaseDtos.stream()
                    .map(AirdropPhaseDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                // nextUnlockDays 계산 (가장 가까운 PROCESSING Phase의 daysRemaining)
                Integer nextUnlockDays = phaseDtos.stream()
                    .filter(p -> AirdropPhase.STATUS_PROCESSING.equals(p.getStatus()))
                    .min(Comparator.comparing(AirdropPhaseDto::getDaysRemaining, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(AirdropPhaseDto::getDaysRemaining)
                    .orElse(null);
                
                return Future.succeededFuture(AirdropStatusDto.builder()
                    .totalReceived(totalReceived)
                    .totalReward(totalReward)
                    .nextUnlockDays(nextUnlockDays)
                    .phases(phaseDtos)
                    .build());
            });
    }
    
    /**
     * 에어드랍 락업 해제 금액을 코리온 지갑으로 전송
     */
    public Future<AirdropTransferResponseDto> transferAirdrop(Long userId, AirdropTransferRequestDto request) {
        log.info("에어드랍 전송 요청 - userId: {}, amount: {}", userId, request.getAmount());
        
        // 1. 유효성 검사
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 전송 금액은 " + MIN_TRANSFER_AMOUNT + " 입니다."));
        }
        
        // 2. KORI 통화 조회
        return currencyRepository.getCurrencyByCodeAndChain(pool, KORI_CURRENCY_CODE, INTERNAL_CHAIN)
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("KORI 통화를 찾을 수 없습니다."));
                }
                
                // 3. 사용자의 RELEASED Phase 조회
                return airdropRepository.getPhasesByUserId(pool, userId)
                    .compose(phases -> {
                        LocalDateTime now = LocalDateTime.now();
                        
                        // RELEASED 상태인 Phase들의 총 금액 계산
                        BigDecimal availableAmount = phases.stream()
                            .filter(phase -> {
                                boolean isReleased = phase.getUnlockDate().isBefore(now) || phase.getUnlockDate().isEqual(now);
                                return isReleased && AirdropPhase.STATUS_RELEASED.equals(phase.getStatus());
                            })
                            .map(AirdropPhase::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                        if (availableAmount.compareTo(request.getAmount()) < 0) {
                            return Future.failedFuture(new BadRequestException(
                                "전송 가능한 금액이 부족합니다. 가능: " + availableAmount + ", 요청: " + request.getAmount()));
                        }
                        
                        // 4. 사용자 지갑 조회 (없으면 생성)
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                            .compose(wallet -> {
                                if (wallet == null) {
                                    return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                                }
                                
                                // 5. 전송 실행 (트랜잭션)
                                return executeAirdropTransfer(userId, wallet, currency, request.getAmount());
                            });
                    });
            });
    }
    
    /**
     * 에어드랍 전송 실행 (트랜잭션)
     */
    private Future<AirdropTransferResponseDto> executeAirdropTransfer(
            Long userId, Wallet wallet, Currency currency, BigDecimal amount) {
        
        String transferId = UUID.randomUUID().toString();
        
        return pool.withTransaction(client -> {
            // 1. 에어드랍 전송 기록 생성
            String orderNumber = com.foxya.coin.common.utils.OrderNumberUtils.generateOrderNumber();
            AirdropTransfer airdropTransfer = AirdropTransfer.builder()
                .transferId(transferId)
                .userId(userId)
                .walletId(wallet.getId())
                .currencyId(currency.getId())
                .amount(amount)
                .status(AirdropTransfer.STATUS_PENDING)
                .orderNumber(orderNumber)
                .build();
            
            return airdropRepository.createTransfer(client, airdropTransfer)
                .compose(createdTransfer -> {
                    // 2. 내부 전송으로 지갑에 금액 추가
                    // 에어드랍은 시스템에서 사용자 지갑으로 전송하는 것이므로
                    // 내부 전송을 사용하되, sender는 시스템(0 또는 null)으로 처리
                    InternalTransfer internalTransfer = InternalTransfer.builder()
                        .transferId(UUID.randomUUID().toString())
                        .senderId(null)  // 시스템 전송
                        .senderWalletId(null)
                        .receiverId(userId)
                        .receiverWalletId(wallet.getId())
                        .currencyId(currency.getId())
                        .amount(amount)
                        .fee(BigDecimal.ZERO)  // 에어드랍은 수수료 없음
                        .status(InternalTransfer.STATUS_COMPLETED)
                        .transferType(InternalTransfer.TYPE_ADMIN_GRANT)
                        .orderNumber(orderNumber)
                        .transactionType(com.foxya.coin.common.enums.TransactionType.TOKEN_DEPOSIT.getValue())
                        .memo("에어드랍 락업 해제")
                        .requestIp("system")
                        .build();
                    
                    return transferRepository.createInternalTransfer(client, internalTransfer)
                        .compose(createdInternalTransfer -> {
                            // 3. 수신자 잔액 추가
                            return transferRepository.addBalance(client, wallet.getId(), amount);
                        })
                        .compose(updatedWallet -> {
                            // 4. 에어드랍 전송 상태를 COMPLETED로 업데이트
                            return airdropRepository.updateTransferStatus(client, transferId, AirdropTransfer.STATUS_COMPLETED);
                        });
                });
        }).map(completedTransfer -> {
            log.info("에어드랍 전송 완료 - transferId: {}, userId: {}, amount: {}", 
                transferId, userId, amount);
            
            return AirdropTransferResponseDto.builder()
                .transferId(transferId)
                .amount(amount)
                .currencyCode(currency.getCode())
                .status(AirdropTransfer.STATUS_COMPLETED)
                .createdAt(completedTransfer.getCreatedAt())
                .build();
        });
    }
}

