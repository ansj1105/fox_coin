package com.foxya.coin.airdrop;

import com.foxya.coin.airdrop.dto.AirdropPhaseDto;
import com.foxya.coin.airdrop.dto.AirdropPhaseReleaseResponseDto;
import com.foxya.coin.airdrop.dto.AirdropStatusDto;
import com.foxya.coin.airdrop.dto.AirdropTransferRequestDto;
import com.foxya.coin.airdrop.dto.AirdropTransferResponseDto;
import com.foxya.coin.airdrop.entities.AirdropPhase;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.TransactionType;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.exceptions.ForbiddenException;
import com.foxya.coin.common.utils.OrderNumberUtils;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.notification.utils.NotificationI18nUtils;
import com.foxya.coin.transfer.TransferRepository;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
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
    private final WalletRepository walletRepository;
    private final NotificationService notificationService;
    
    // KORI 통화 코드
    private static final String KORI_CURRENCY_CODE = "KORI";
    private static final String INTERNAL_CHAIN = "INTERNAL";
    
    // 최소 전송 금액
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.000001");
    private static final String AIRDROP_UNLOCKED_NOTICE_TITLE = "에어드랍 락 해제";
    private static final String AIRDROP_UNLOCKED_NOTICE_MESSAGE = "에어드랍 락이 해제되었습니다. 이제 수령할 수 있습니다.";
    private static final String AIRDROP_UNLOCKED_NOTICE_TITLE_KEY = "notifications.airdropUnlocked.title";
    private static final String AIRDROP_UNLOCKED_NOTICE_MESSAGE_KEY = "notifications.airdropUnlocked.message";
    
    public AirdropService(PgPool pool,
                          AirdropRepository airdropRepository,
                          CurrencyRepository currencyRepository,
                          TransferRepository transferRepository,
                          WalletRepository walletRepository,
                          NotificationService notificationService) {
        super(pool);
        this.airdropRepository = airdropRepository;
        this.currencyRepository = currencyRepository;
        this.transferRepository = transferRepository;
        this.walletRepository = walletRepository;
        this.notificationService = notificationService;
    }
    
    /**
     * 에어드랍 상태 조회
     */
    public Future<AirdropStatusDto> getAirdropStatus(Long userId) {
        log.info("에어드랍 상태 조회 - userId: {}", userId);
        
        return airdropRepository.getPhasesByUserId(pool, userId)
            .recover(throwable -> {
                log.error("에어드랍 Phase 조회 실패 - userId: {}", userId, throwable);
                return Future.succeededFuture(java.util.Collections.emptyList());
            })
            .compose(phases -> {
                // phases가 null이거나 비어있으면 빈 상태 반환
                if (phases == null) {
                    phases = java.util.Collections.emptyList();
                }
                
                if (phases.isEmpty()) {
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
                        
                        Boolean claimed = phase.getClaimed() != null ? phase.getClaimed() : Boolean.FALSE;
                        
                        return AirdropPhaseDto.builder()
                            .id(phase.getId())
                            .phase(phase.getPhase())
                            .status(status)
                            .amount(phase.getAmount())
                            .claimed(claimed)
                            .unlockDate(phase.getUnlockDate())
                            .daysRemaining(daysRemaining)
                            .createdAt(phase.getCreatedAt())
                            .build();
                    })
                    .sorted(Comparator.comparing(AirdropPhaseDto::getPhase))
                    .collect(Collectors.toList());
                
                // totalReceived: RELEASED && claimed 인 phase의 (amount - transferredAmount) 합 = 전송가능 금액
                BigDecimal totalReceived = phases.stream()
                    .filter(p -> {
                        boolean pastUnlock = p.getUnlockDate() != null && (p.getUnlockDate().isBefore(now) || p.getUnlockDate().isEqual(now));
                        boolean isReleased = pastUnlock || AirdropPhase.STATUS_RELEASED.equals(p.getStatus());
                        return isReleased && Boolean.TRUE.equals(p.getClaimed());
                    })
                    .map(p -> {
                        BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                        BigDecimal ta = p.getTransferredAmount() != null ? p.getTransferredAmount() : BigDecimal.ZERO;
                        return amt.subtract(ta);
                    })
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
     * Phase Release: 보상형 영상 시청 완료 후 해당 Phase를 락업 해제 금액에 반영.
     * status === RELEASED && claimed === false 인 경우만 성공.
     */
    public Future<AirdropPhaseReleaseResponseDto> releasePhase(Long userId, Long phaseId) {
        if (phaseId == null) {
            return Future.failedFuture(new BadRequestException("phaseId가 필요합니다."));
        }
        return airdropRepository.getPhaseByIdAndUserId(pool, phaseId, userId)
            .compose(phase -> {
                if (phase == null) {
                    return Future.failedFuture(new NotFoundException("해당 Phase를 찾을 수 없습니다."));
                }
                LocalDateTime now = LocalDateTime.now();
                boolean isReleased = phase.getUnlockDate().isBefore(now) || phase.getUnlockDate().isEqual(now);
                if (!isReleased) {
                    return Future.failedFuture(new BadRequestException("해제 가능한 Phase가 아닙니다. (언락일 도래 후 Release 가능)"));
                }
                if (Boolean.TRUE.equals(phase.getClaimed())) {
                    return Future.failedFuture(new BadRequestException("이미 Release 완료된 Phase입니다."));
                }
                return airdropRepository.updatePhaseClaimed(pool, phaseId, userId)
                    .compose(updated -> {
                        if (updated == null) {
                            return Future.failedFuture(new ForbiddenException("Release 처리에 실패했습니다."));
                        }
                        return createAirdropUnlockedNotification(userId, updated)
                            .map(v -> AirdropPhaseReleaseResponseDto.builder()
                            .phaseId(updated.getId())
                            .amount(updated.getAmount())
                            .build());
                    });
            });
    }

    private Future<Void> createAirdropUnlockedNotification(Long userId, AirdropPhase phase) {
        if (notificationService == null || userId == null || phase == null || phase.getId() == null) {
            return Future.succeededFuture();
        }
        String metadata = NotificationI18nUtils.buildMetadata(
            AIRDROP_UNLOCKED_NOTICE_TITLE_KEY,
            AIRDROP_UNLOCKED_NOTICE_MESSAGE_KEY,
            new JsonObject()
                .put("phaseId", phase.getId())
                .put("amount", phase.getAmount() != null ? phase.getAmount().stripTrailingZeros().toPlainString() : null)
                .put("unlockDate", phase.getUnlockDate() != null ? phase.getUnlockDate().toString() : null)
        );
        return notificationService.createNotificationIfAbsentByRelatedId(
                userId,
                NotificationType.AIRDROP_UNLOCKED,
                AIRDROP_UNLOCKED_NOTICE_TITLE,
                AIRDROP_UNLOCKED_NOTICE_MESSAGE,
                phase.getId(),
                metadata
            )
            .<Void>mapEmpty()
            .recover(err -> {
                log.warn("에어드랍 락 해제 알림 생성 실패(무시) - userId: {}, phaseId: {}", userId, phase.getId(), err);
                return Future.succeededFuture((Void) null);
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
        
        // 2. KORI 통화 조회 (채굴·에어드랍용 — is_active 무관)
        return currencyRepository.getCurrencyByCodeAndChainAllowInactive(pool, KORI_CURRENCY_CODE, INTERNAL_CHAIN)
            .recover(throwable -> {
                log.error("KORI 통화 조회 실패", throwable);
                return Future.failedFuture(new NotFoundException("KORI 통화를 찾을 수 없습니다."));
            })
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("KORI 통화를 찾을 수 없습니다."));
                }
                
                // 3. 사용자의 RELEASED Phase 조회
                return airdropRepository.getPhasesByUserId(pool, userId)
                    .recover(throwable -> {
                        log.error("에어드랍 Phase 조회 실패 - userId: {}", userId, throwable);
                        return Future.succeededFuture(java.util.Collections.emptyList());
                    })
                    .compose(phases -> {
                        if (phases == null || phases.isEmpty()) {
                            return Future.failedFuture(new BadRequestException("전송 가능한 에어드랍이 없습니다."));
                        }
                        
                        LocalDateTime now = LocalDateTime.now();
                        
                        // 전송 가능 금액: (unlockDate <= now 또는 RELEASED) && claimed 인 phase의 (amount - transferredAmount) 합
                        BigDecimal availableAmount = phases.stream()
                            .filter(phase -> {
                                boolean pastUnlock = phase.getUnlockDate() != null
                                    && (phase.getUnlockDate().isBefore(now) || phase.getUnlockDate().isEqual(now));
                                boolean isReleased = pastUnlock || AirdropPhase.STATUS_RELEASED.equals(phase.getStatus());
                                boolean claimed = Boolean.TRUE.equals(phase.getClaimed());
                                return isReleased && claimed;
                            })
                            .map(phase -> {
                                BigDecimal amt = phase.getAmount() != null ? phase.getAmount() : BigDecimal.ZERO;
                                BigDecimal ta = phase.getTransferredAmount() != null ? phase.getTransferredAmount() : BigDecimal.ZERO;
                                return amt.subtract(ta);
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                        if (availableAmount.compareTo(request.getAmount()) < 0) {
                            return Future.failedFuture(new BadRequestException(
                                "전송 가능한 금액이 부족합니다. 가능: " + availableAmount + ", 요청: " + request.getAmount()));
                        }
                        
                        // 4. 사용자 지갑 조회 (없으면 생성)
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                            .recover(throwable -> {
                                log.error("지갑 조회 실패 - userId: {}, currencyId: {}", userId, currency.getId(), throwable);
                                return Future.succeededFuture((Wallet) null);
                            })
                            .compose(wallet -> {
                                if (wallet == null) {
                                    // 지갑이 없으면 자동 생성 (KORI는 내부 통화이므로 더미 주소 사용)
                                    log.info("지갑이 없어 자동 생성 - userId: {}, currencyId: {}", userId, currency.getId());
                                    String dummyAddress = "KORI_" + userId + "_" + currency.getId();
                                    return walletRepository.createWallet(pool, userId, currency.getId(), dummyAddress)
                                        .recover(throwable -> {
                                            // 중복 키 오류 발생 시 기존 지갑 조회
                                            if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                                                log.warn("지갑 생성 중 중복 감지, 기존 지갑 조회 - userId: {}, currencyId: {}", userId, currency.getId());
                                                return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId());
                                            }
                                            return Future.failedFuture(throwable);
                                        });
                                }
                                return Future.succeededFuture(wallet);
                            })
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
            String orderNumber = OrderNumberUtils.generateOrderNumber();
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
                    // 내부 전송을 사용하되, sender_id는 NOT NULL이므로 receiver_id를 sender_id로도 사용 (시스템 자동 지급)
                    InternalTransfer internalTransfer = InternalTransfer.builder()
                        .transferId(UUID.randomUUID().toString())
                        .senderId(userId)  // 시스템 전송: sender_id는 NOT NULL이므로 receiver_id를 사용
                        .senderWalletId(wallet.getId())  // 시스템 전송: sender_wallet_id는 NOT NULL이므로 receiver_wallet_id를 사용
                        .receiverId(userId)
                        .receiverWalletId(wallet.getId())
                        .currencyId(currency.getId())
                        .amount(amount)
                        .fee(BigDecimal.ZERO)  // 에어드랍은 수수료 없음
                        .status(InternalTransfer.STATUS_COMPLETED)
                        .transferType(InternalTransfer.TYPE_ADMIN_GRANT)
                        .orderNumber(orderNumber)
                        .transactionType(TransactionType.AIRDROP_TRANSFER.getValue())
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
                        })
                        .compose(completedTransfer -> {
                            // 5. 전송한 금액만큼 claimed Phase에 transferred_amount 할당 (잔량 유지)
                            return airdropRepository.allocateTransferredAmount(client, userId, amount)
                                .map(v -> completedTransfer);
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
