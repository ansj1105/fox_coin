package com.foxya.coin.transfer;

import com.foxya.coin.airdrop.AirdropRepository;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.ChainType;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.enums.TransactionType;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventType;
import com.foxya.coin.notification.NotificationService;
import com.foxya.coin.notification.enums.NotificationType;
import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import com.foxya.coin.transfer.dto.InternalTransferRequestDto;
import com.foxya.coin.transfer.dto.TransferHistoryResponseDto;
import com.foxya.coin.transfer.dto.TransferResponseDto;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.wallet.WalletRepository;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class TransferService extends BaseService {

    /** Redis 멱등 키 TTL (7일, 초 단위) */
    private static final int CONFIRMED_IDEMPOTENCY_TTL_SECONDS = 7 * 24 * 3600;
    private static final String REDIS_KEY_CONFIRMED = "transfer:confirmed:";
    private static final String REDIS_KEY_FAILED = "transfer:failed:";

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final WalletRepository walletRepository;
    private final EventPublisher eventPublisher;
    private final RedisAPI redisApi;
    private final NotificationService notificationService;
    private final AirdropRepository airdropRepository;

    // 내부 전송 수수료 (0.1%)
    private static final BigDecimal INTERNAL_FEE_RATE = new BigDecimal("0.001");
    // 최소 전송 금액
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.000001");

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, null, null, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, null, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi,
                          NotificationService notificationService) {
        this(pool, transferRepository, userRepository, currencyRepository, walletRepository, eventPublisher, redisApi, notificationService, null);
    }

    public TransferService(PgPool pool,
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          WalletRepository walletRepository,
                          EventPublisher eventPublisher,
                          RedisAPI redisApi,
                          NotificationService notificationService,
                          AirdropRepository airdropRepository) {
        super(pool);
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.currencyRepository = currencyRepository;
        this.walletRepository = walletRepository;
        this.eventPublisher = eventPublisher;
        this.redisApi = redisApi;
        this.notificationService = notificationService;
        this.airdropRepository = airdropRepository;
    }
    
    /**
     * 내부 전송 실행.
     * 내부 전송 = DB만 사용. 블록체인 트랜잭션 없음. user_wallets 잔액 증감 + internal_transfers 기록만 수행.
     */
    public Future<TransferResponseDto> executeInternalTransfer(Long senderId, InternalTransferRequestDto request, String requestIp) {
        log.info("내부 전송 요청 - senderId: {}, receiverType: {}, receiverValue: {}, amount: {}", 
            senderId, request.getReceiverType(), request.getReceiverValue(), request.getAmount());
        
        // 1. 유효성 검사
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 전송 금액은 " + MIN_TRANSFER_AMOUNT + " 입니다."));
        }
        
        // 2. 통화 조회 (내부 전송은 항상 INTERNAL 체인 사용)
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getCurrencyCode(), "INTERNAL")
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + request.getCurrencyCode() + " on INTERNAL"));
                }
                
                // 3. 수신자 조회
                return findReceiver(request.getReceiverType(), request.getReceiverValue())
                    .compose(receiver -> {
                        if (receiver == null) {
                            return Future.failedFuture(new NotFoundException("수신자를 찾을 수 없습니다."));
                        }
                        
                        if (receiver.getId().equals(senderId)) {
                            return Future.failedFuture(new BadRequestException("자기 자신에게 전송할 수 없습니다."));
                        }
                        
                        // 4. 송신자 지갑 조회
                        return getOrCreateInternalWallet(senderId, currency, false)
                            .compose(senderWallet ->
                                getOrCreateInternalWallet(receiver.getId(), currency, true)
                                    .compose(receiverWallet -> {
                                        // 6. 수수료 계산
                                        BigDecimal fee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                                        BigDecimal totalDeduct = request.getAmount().add(fee);

                                        // 7. 잔액 확인
                                        if (senderWallet.getBalance().compareTo(totalDeduct) < 0) {
                                            return Future.failedFuture(new BadRequestException("잔액이 부족합니다. 필요: " + totalDeduct + ", 보유: " + senderWallet.getBalance()));
                                        }

                                        // 8. 전송 실행 (트랜잭션)
                                        return executeInternalTransferTransaction(
                                            senderId, receiver.getId(),
                                            senderWallet, receiverWallet,
                                            currency, request.getAmount(), fee,
                                            request.getMemo(), requestIp
                                        );
                                    })
                            );
                    });
            });
    }
    
    /**
     * 내부 전송 트랜잭션 실행 (DB만: deductBalance / addBalance / internal_transfers)
     */
    private Future<TransferResponseDto> executeInternalTransferTransaction(
            Long senderId, Long receiverId,
            Wallet senderWallet, Wallet receiverWallet,
            Currency currency, BigDecimal amount, BigDecimal fee,
            String memo, String requestIp) {
        
        String transferId = UUID.randomUUID().toString();
        BigDecimal totalDeduct = amount.add(fee);
        
        // 트랜잭션으로 처리
        return pool.withTransaction(client -> {
            // 1. 송신자 잔액 차감
            return transferRepository.deductBalance(client, senderWallet.getId(), totalDeduct)
                .compose(updatedSenderWallet -> {
                    if (updatedSenderWallet == null) {
                        return Future.failedFuture(new BadRequestException("잔액 차감 실패 (잔액 부족)"));
                    }
                    
                    // 2. 수신자 잔액 추가
                    return transferRepository.addBalance(client, receiverWallet.getId(), amount);
                })
                .compose(updatedReceiverWallet -> {
                    if (updatedReceiverWallet == null) {
                        return Future.failedFuture(new BadRequestException("잔액 추가 실패"));
                    }
                    
                    // 3. 전송 기록 생성 (내부 트랜잭션도 전략적 추적을 위해 transfer_id, order_number 항상 기록)
                    String orderNumber = com.foxya.coin.common.utils.OrderNumberUtils.generateOrderNumber();
                    InternalTransfer transfer = InternalTransfer.builder()
                        .transferId(transferId)
                        .senderId(senderId)
                        .senderWalletId(senderWallet.getId())
                        .receiverId(receiverId)
                        .receiverWalletId(receiverWallet.getId())
                        .currencyId(currency.getId())
                        .amount(amount)
                        .fee(fee)
                        .status(InternalTransfer.STATUS_COMPLETED)
                        .transferType(InternalTransfer.TYPE_INTERNAL)
                        .orderNumber(orderNumber)
                        .transactionType(com.foxya.coin.common.enums.TransactionType.WITHDRAW.getValue())
                        .memo(memo)
                        .requestIp(requestIp)
                        .build();
                    
                    return transferRepository.createInternalTransfer(client, transfer);
                })
                .compose(createdTransfer -> {
                    // 4. 전송 완료 처리
                    return transferRepository.completeInternalTransfer(client, transferId);
                });
        }).map(completedTransfer -> {
            log.info("내부 전송 완료 - transferId: {}, sender: {}, receiver: {}, amount: {}", 
                transferId, senderId, receiverId, amount);
            
            return TransferResponseDto.builder()
                .transferId(transferId)
                .transferType("INTERNAL")
                .senderId(senderId)
                .receiverId(receiverId)
                .currencyCode(currency.getCode())
                .amount(amount)
                .fee(fee)
                .status(InternalTransfer.STATUS_COMPLETED)
                .memo(memo)
                .createdAt(completedTransfer.getCreatedAt())
                .completedAt(completedTransfer.getCompletedAt())
                .build();
        });
    }
    
    /**
     * 래퍼럴 수익 지급 (REFERRAL_REWARD: sender 없음, 수신자 지갑에 KORI 추가)
     */
    public Future<InternalTransfer> createReferralRewardTransfer(Long referrerId, BigDecimal amount, String memo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.failedFuture(new BadRequestException("지급 금액은 0보다 커야 합니다."));
        }
        return currencyRepository.getCurrencyByCodeAndChainAllowInactive(pool, "KORI", "INTERNAL")
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("KORI 통화를 찾을 수 없습니다."));
                }
                return getOrCreateInternalWallet(referrerId, currency, true)
                    .compose(receiverWallet -> {
                        if (receiverWallet == null) {
                            return Future.failedFuture(new NotFoundException("추천인 지갑을 찾을 수 없습니다."));
                        }
                        String transferId = UUID.randomUUID().toString();
                        InternalTransfer transfer = InternalTransfer.builder()
                            .transferId(transferId)
                            .senderId(null)
                            .senderWalletId(null)
                            .receiverId(referrerId)
                            .receiverWalletId(receiverWallet.getId())
                            .currencyId(currency.getId())
                            .amount(amount)
                            .fee(BigDecimal.ZERO)
                            .status(InternalTransfer.STATUS_COMPLETED)
                            .transferType(InternalTransfer.TYPE_REFERRAL_REWARD)
                            .orderNumber(com.foxya.coin.common.utils.OrderNumberUtils.generateOrderNumber())
                            .transactionType(com.foxya.coin.common.enums.TransactionType.TOKEN_DEPOSIT.getValue())
                            .memo(memo != null ? memo : "REFERRAL_REWARD")
                            .requestIp(null)
                            .build();
                        return pool.withTransaction(client -> transferRepository.addBalance(client, receiverWallet.getId(), amount)
                            .compose(updated -> transferRepository.createInternalTransfer(client, transfer)));
                    });
            });
    }
    
    /**
     * 수신자 조회 (타입에 따라)
     */
    private Future<User> findReceiver(String receiverType, String receiverValue) {
        return switch (receiverType) {
            case InternalTransferRequestDto.RECEIVER_TYPE_ADDRESS -> 
                // 지갑 주소로 조회
                transferRepository.getWalletByAddress(pool, receiverValue)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.succeededFuture(null);
                        }
                        return userRepository.getUserById(pool, wallet.getUserId());
                    });
            
            case InternalTransferRequestDto.RECEIVER_TYPE_REFERRAL_CODE -> 
                // 추천인 코드로 조회
                userRepository.getUserByReferralCode(pool, receiverValue);
            
            case InternalTransferRequestDto.RECEIVER_TYPE_USER_ID -> 
                // 유저 ID로 조회 (관리자용)
                userRepository.getUserById(pool, Long.parseLong(receiverValue));
            
            default -> Future.failedFuture(new BadRequestException("잘못된 수신자 타입입니다: " + receiverType));
        };
    }

    private Future<Wallet> getOrCreateInternalWallet(Long userId, Currency currency, boolean createIfMissing) {
        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
            .compose(existing -> {
                if (existing != null) {
                    return Future.succeededFuture(existing);
                }
                if (!createIfMissing) {
                    return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                }
                return createInternalWalletIfNeeded(userId, currency)
                    .compose(created -> {
                        if (created == null) {
                            return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                        }
                        return Future.succeededFuture(created);
                    });
            });
    }

    private Future<Wallet> createInternalWalletIfNeeded(Long userId, Currency currency) {
        if (!"INTERNAL".equalsIgnoreCase(currency.getChain())) {
            return Future.succeededFuture(null);
        }
        String address = currency.getCode() + "_INTERNAL_" + userId;
        return walletRepository.createWallet(pool, userId, currency.getId(), address)
            .recover(throwable -> {
                if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                    return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId());
                }
                return Future.failedFuture(throwable);
            });
    }
    
    /**
     * 외부 전송 요청 (출금).
     * - 유저에게 보여지는 건 내부 지갑만 해당. 출금 시에도 사용·차감 대상은 유저의 내부 지갑(user_wallets) 뿐.
     * - DB에 출금 요청 기록 + 내부 지갑 잔액 잠금. 실제 온체인 전송은 플랫폼 메인 지갑(중앙지갑)에서 Node 등이 PENDING 건을 처리.
     */
    public Future<TransferResponseDto> requestExternalTransfer(Long userId, ExternalTransferRequestDto request, String requestIp) {
        log.info("외부 전송 요청 - userId: {}, toAddress: {}, amount: {}, chain: {}",
            userId, request.getToAddress(), request.getAmount(), request.getChain());

        // 1. 유효성 검사
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 전송 금액은 " + MIN_TRANSFER_AMOUNT + " 입니다."));
        }

        if (request.getToAddress() == null || request.getToAddress().isEmpty()) {
            return Future.failedFuture(new BadRequestException("수신 주소를 입력해주세요."));
        }

        // 2. 체인 유효성 검사
        ChainType chainType = ChainType.fromValue(request.getChain());
        if (chainType == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 체인입니다: " + request.getChain()));
        }

        // 3. 통화 조회 (예: KORI + TRON)
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getCurrencyCode(), request.getChain())
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + request.getCurrencyCode() + " on " + request.getChain()));
                }

                // 4. 유저 내부 지갑만 조회·사용 (외부 지갑은 사용하지 않음)
                return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                        }

                        // 5. 수수료 계산 (네트워크 수수료는 Node.js에서 계산)
                        BigDecimal serviceFee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                        BigDecimal totalDeduct = request.getAmount().add(serviceFee);

                        // 6. 내부 지갑 가용 잔액 확인 (balance = 이미 가용분만 있음, locked_balance 제외)
                        if (wallet.getBalance().compareTo(totalDeduct) < 0) {
                            return Future.failedFuture(new BadRequestException("잔액이 부족합니다. 필요: " + totalDeduct + ", 보유: " + wallet.getBalance()));
                        }

                        // 7. 내부 지갑 잠금 + 출금 요청 생성 (실제 송금은 메인 지갑에서 처리)
                        return createExternalTransferRequest(userId, wallet, currency, request, serviceFee, requestIp);
                    });
            });
    }
    
    /**
     * 외부 전송 요청 생성: 유저 내부 지갑 잠금 + external_transfers PENDING 기록.
     * 실제 온체인 전송은 플랫폼 메인 지갑(중앙지갑) 워커가 PENDING 건을 읽어 처리.
     */
    private Future<TransferResponseDto> createExternalTransferRequest(
            Long userId, Wallet wallet, Currency currency,
            ExternalTransferRequestDto request, BigDecimal serviceFee, String requestIp) {
        
        String transferId = UUID.randomUUID().toString();
        BigDecimal totalDeduct = request.getAmount().add(serviceFee);
        
        return pool.withTransaction(client -> {
            // 1. 잔액 잠금
            return transferRepository.lockBalance(client, wallet.getId(), totalDeduct)
                .compose(updatedWallet -> {
                    if (updatedWallet == null) {
                        return Future.failedFuture(new BadRequestException("잔액 잠금 실패 (잔액 부족)"));
                    }
                    
                    // 2. 외부 전송 기록 생성
                    String orderNumber = com.foxya.coin.common.utils.OrderNumberUtils.generateOrderNumber();
                    ExternalTransfer transfer = ExternalTransfer.builder()
                        .transferId(transferId)
                        .userId(userId)
                        .walletId(wallet.getId())
                        .currencyId(currency.getId())
                        .toAddress(request.getToAddress())
                        .amount(request.getAmount())
                        .fee(serviceFee)
                        .networkFee(BigDecimal.ZERO) // Node.js에서 계산 후 업데이트
                        .status(ExternalTransfer.STATUS_PENDING)
                        .orderNumber(orderNumber)
                        .transactionType(com.foxya.coin.common.enums.TransactionType.WITHDRAW.getValue())
                        .chain(request.getChain())
                        .requiredConfirmations(getRequiredConfirmations(request.getChain()))
                        .memo(request.getMemo())
                        .requestIp(requestIp)
                        .build();
                    
                    return transferRepository.createExternalTransfer(client, transfer);
                });
        }).compose(createdTransfer -> {
            log.info("외부 전송 요청 생성 완료 - transferId: {}", transferId);
            
            // 3. 이벤트 발행 (Node.js 서비스에서 처리)
            if (eventPublisher != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("transferId", transferId);
                payload.put("userId", userId);
                payload.put("toAddress", request.getToAddress());
                payload.put("amount", request.getAmount().toPlainString());
                payload.put("currencyCode", currency.getCode());
                payload.put("chain", request.getChain());
                
                eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                    .onFailure(e -> log.error("외부 전송 이벤트 발행 실패: {}", e.getMessage()));
            }
            
            return Future.succeededFuture(TransferResponseDto.builder()
                .transferId(transferId)
                .transferType("EXTERNAL")
                .senderId(userId)
                .toAddress(request.getToAddress())
                .currencyCode(currency.getCode())
                .amount(request.getAmount())
                .fee(serviceFee)
                .status(ExternalTransfer.STATUS_PENDING)
                .memo(request.getMemo())
                .createdAt(createdTransfer.getCreatedAt())
                .build());
        });
    }
    
    /**
     * 체인별 필요 컨펌 수
     */
    private int getRequiredConfirmations(String chain) {
        return switch (chain) {
            case ExternalTransfer.CHAIN_TRON -> 20;
            case ExternalTransfer.CHAIN_ETH -> 12;
            default -> 1;
        };
    }

    /**
     * 상태별 외부 전송 목록 (출금 워커 컨펌 추적용).
     */
    public Future<List<ExternalTransfer>> listExternalTransfersByStatus(String status, int limit) {
        return transferRepository.getExternalTransfersByStatus(pool, status, limit);
    }

    /**
     * 외부 전송 제출 처리 (Node.js 워커 등에서 호출).
     * 온체인 tx 제출 후 txHash를 기록. 상태를 SUBMITTED로 변경.
     */
    public Future<ExternalTransfer> submitExternalTransfer(String transferId, String txHash) {
        return transferRepository.getExternalTransferById(pool, transferId)
            .compose(et -> {
                if (et == null) {
                    return Future.failedFuture(new NotFoundException("외부 전송을 찾을 수 없습니다: " + transferId));
                }
                if (!ExternalTransfer.STATUS_PENDING.equals(et.getStatus()) && !ExternalTransfer.STATUS_PROCESSING.equals(et.getStatus())) {
                    return Future.failedFuture(new BadRequestException("제출 가능한 상태가 아닙니다. status=" + et.getStatus()));
                }
                return transferRepository.submitExternalTransfer(pool, transferId, txHash);
            });
    }

    /**
     * 외부 전송 컨펌 완료 처리 (Node.js 워커 등에서 호출).
     * 내부 지갑 잠금 해제(unlockBalance, refund=false)를 트랜잭션으로 수행하여
     * 외부 지갑 차감이 확정되면 내부 장부도 최종 차감 반영.
     * Redis 멱등 키로 중복 처리 방지.
     */
    public Future<ExternalTransfer> confirmExternalTransfer(String transferId, int confirmations) {
        if (redisApi != null) {
            String key = REDIS_KEY_CONFIRMED + transferId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("외부 전송 이미 컨펌 처리됨 (멱등) - transferId: {}", transferId);
                        return transferRepository.getExternalTransferById(pool, transferId);
                    }
                    return doConfirmExternalTransfer(transferId, confirmations)
                        .compose(et -> redisApi.setex(key, String.valueOf(CONFIRMED_IDEMPOTENCY_TTL_SECONDS), "1")
                            .map(v -> et))
                        .compose(this::createWithdrawalCompletedNotification);
                });
        }
        return doConfirmExternalTransfer(transferId, confirmations)
            .compose(this::createWithdrawalCompletedNotification);
    }

    /** 출금 완료 시 notifications 인서트 (앱 알람, 추후 FCM 푸시 활용) */
    private Future<ExternalTransfer> createWithdrawalCompletedNotification(ExternalTransfer confirmed) {
        if (notificationService == null || confirmed == null || confirmed.getUserId() == null) {
            return Future.succeededFuture(confirmed);
        }
        return currencyRepository.getCurrencyById(pool, confirmed.getCurrencyId())
            .compose(currency -> {
                String currencyCode = currency != null ? currency.getCode() : "";
                String amountStr = confirmed.getAmount() != null ? confirmed.getAmount().toPlainString() : "";
                String title = "출금 완료";
                String message = amountStr + " " + currencyCode + " 출금이 완료되었습니다.";
                JsonObject meta = new JsonObject()
                    .put("transferId", confirmed.getTransferId())
                    .put("amount", amountStr)
                    .put("currencyCode", currencyCode)
                    .put("txHash", confirmed.getTxHash())
                    .put("toAddress", confirmed.getToAddress());
                return notificationService.createNotification(
                    confirmed.getUserId(), NotificationType.WITHDRAW_SUCCESS, title, message, null, meta.encode());
            })
            .map(v -> confirmed)
            .recover(err -> {
                log.warn("출금 완료 알림 생성 실패(무시): transferId={}", confirmed.getTransferId(), err);
                return Future.succeededFuture(confirmed);
            });
    }

    private Future<ExternalTransfer> doConfirmExternalTransfer(String transferId, int confirmations) {
        return pool.withTransaction(client ->
            transferRepository.getExternalTransferById(client, transferId)
                .compose(et -> {
                    if (et == null) {
                        return Future.failedFuture(new NotFoundException("외부 전송을 찾을 수 없습니다: " + transferId));
                    }
                    if (!ExternalTransfer.STATUS_SUBMITTED.equals(et.getStatus())) {
                        return Future.failedFuture(new BadRequestException(
                            "컨펌 가능한 상태가 아닙니다. status=" + et.getStatus()));
                    }
                    BigDecimal totalDeduct = et.getAmount().add(et.getFee() != null ? et.getFee() : BigDecimal.ZERO);
                    return transferRepository.confirmExternalTransfer(client, transferId, confirmations)
                        .compose(confirmed ->
                            transferRepository.unlockBalance(client, et.getWalletId(), totalDeduct, false)
                                .map(w -> confirmed));
                }));
    }

    /**
     * 외부 전송 실패 처리 및 잔액 복구 (Node.js 워커 등에서 호출).
     * 실패 시 내부 지갑 잠금 해제( refund=true )로 잔액 복구.
     * Redis 멱등 키로 중복 복구 방지.
     */
    public Future<ExternalTransfer> failExternalTransferAndRefund(String transferId, String errorCode, String errorMessage) {
        if (redisApi != null) {
            String key = REDIS_KEY_FAILED + transferId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("외부 전송 이미 실패 처리됨 (멱등) - transferId: {}", transferId);
                        return transferRepository.getExternalTransferById(pool, transferId);
                    }
                    return doFailExternalTransferAndRefund(transferId, errorCode, errorMessage)
                        .compose(et -> redisApi.setex(key, String.valueOf(CONFIRMED_IDEMPOTENCY_TTL_SECONDS), "1")
                            .map(v -> et));
                });
        }
        return doFailExternalTransferAndRefund(transferId, errorCode, errorMessage);
    }

    private Future<ExternalTransfer> doFailExternalTransferAndRefund(String transferId, String errorCode, String errorMessage) {
        return pool.withTransaction(client ->
            transferRepository.getExternalTransferById(client, transferId)
                .compose(et -> {
                    if (et == null) {
                        return Future.failedFuture(new NotFoundException("외부 전송을 찾을 수 없습니다: " + transferId));
                    }
                    if (ExternalTransfer.STATUS_CONFIRMED.equals(et.getStatus()) || ExternalTransfer.STATUS_FAILED.equals(et.getStatus())) {
                        return Future.failedFuture(new BadRequestException("이미 최종 처리된 전송입니다. status=" + et.getStatus()));
                    }
                    BigDecimal totalRefund = et.getAmount().add(et.getFee() != null ? et.getFee() : BigDecimal.ZERO);
                    return transferRepository.failExternalTransfer(client, transferId, errorCode, errorMessage)
                        .compose(failed ->
                            transferRepository.unlockBalance(client, et.getWalletId(), totalRefund, true)
                                .map(w -> failed));
                }));
    }

    /**
     * 전송 내역 조회 (내부 + 외부 + 에어드랍 통합, OpenAPI TransferHistory 형식으로 반환)
     */
    public Future<TransferHistoryResponseDto> getTransferHistory(Long userId, int limit, int offset) {
        Future<List<AirdropTransfer>> airdropFuture = (airdropRepository != null)
            ? airdropRepository.getTransfersByUserId(pool, userId, limit * 2, 0)
            : Future.succeededFuture(List.of());

        return transferRepository.getInternalTransfersByUserId(pool, userId, limit, offset)
            .compose(internalTransfers ->
                transferRepository.getExternalTransfersByUserId(pool, userId, limit, offset)
                    .compose(externalTransfers ->
                        airdropFuture.compose(airdropTransfers -> {
                            // 내부 전송 매핑
                            List<Future<TransferResponseDto>> internalDtos = internalTransfers.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("INTERNAL")
                                        .transactionType(t.getTransactionType())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(t.getSenderId())
                                        .receiverId(t.getReceiverId())
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(t.getFee())
                                        .status(t.getStatus())
                                        .memo(t.getMemo())
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getCompletedAt())
                                        .build()))
                                .collect(Collectors.toList()));

                            // 외부 전송 매핑
                            List<Future<TransferResponseDto>> externalDtos = externalTransfers.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("EXTERNAL")
                                        .transactionType(t.getTransactionType())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(t.getUserId())
                                        .toAddress(t.getToAddress())
                                        .currencyCode(currency.getCode())
                                        .network(t.getChain())
                                        .amount(t.getAmount())
                                        .fee(t.getFee())
                                        .networkFee(t.getNetworkFee())
                                        .status(t.getStatus())
                                        .txHash(t.getTxHash())
                                        .memo(t.getMemo())
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getConfirmedAt())
                                        .build()))
                                .collect(Collectors.toList()));

                            Set<String> internalOrderNumbers = internalTransfers.stream()
                                .map(InternalTransfer::getOrderNumber)
                                .filter(o -> o != null && !o.isEmpty())
                                .collect(Collectors.toSet());

                            // 에어드랍 전송 매핑 (internal에 같은 order_number가 없을 때만 포함 — 중복 제거)
                            List<Future<TransferResponseDto>> airdropDtos = airdropTransfers.stream()
                                .filter(at -> {
                                    String on = at.getOrderNumber();
                                    return on != null && !on.isEmpty() && !internalOrderNumbers.contains(on);
                                })
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("INTERNAL")
                                        .transactionType(TransactionType.TOKEN_DEPOSIT.getValue())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(userId)
                                        .receiverId(userId)
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(BigDecimal.ZERO)
                                        .status(t.getStatus())
                                        .memo("에어드랍 락업 해제")
                                        .createdAt(t.getCreatedAt())
                                        .completedAt(t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt())
                                        .build()))
                                .collect(Collectors.toList());

                            List<Future<TransferResponseDto>> allDtos = new java.util.ArrayList<>();
                            allDtos.addAll(internalDtos);
                            allDtos.addAll(externalDtos);
                            allDtos.addAll(airdropDtos);

                            return Future.all(allDtos)
                                .map(results -> {
                                    List<TransferResponseDto> allTransfers = results.list();
                                    allTransfers.sort((a, b) -> {
                                        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                                        if (a.getCreatedAt() == null) return 1;
                                        if (b.getCreatedAt() == null) return -1;
                                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                                    });
                                    List<TransferResponseDto> limited = allTransfers.stream()
                                        .limit(limit)
                                        .collect(Collectors.toList());
                                    return TransferHistoryResponseDto.builder()
                                        .transfers(limited)
                                        .total(limited.size())
                                        .limit(limit)
                                        .offset(offset)
                                        .build();
                                });
                        })));
    }
    
    /**
     * 전송 상세 조회
     */
    public Future<TransferResponseDto> getTransferDetail(String transferId) {
        // 먼저 내부 전송에서 조회
        return transferRepository.getInternalTransferById(pool, transferId)
            .compose(internalTransfer -> {
                if (internalTransfer != null) {
                    return currencyRepository.getCurrencyById(pool, internalTransfer.getCurrencyId())
                        .map(currency -> TransferResponseDto.builder()
                            .transferId(internalTransfer.getTransferId())
                            .transferType("INTERNAL")
                            .transactionType(internalTransfer.getTransactionType())
                            .orderNumber(internalTransfer.getOrderNumber())
                            .senderId(internalTransfer.getSenderId())
                            .receiverId(internalTransfer.getReceiverId())
                            .currencyCode(currency.getCode())
                            .amount(internalTransfer.getAmount())
                            .fee(internalTransfer.getFee())
                            .status(internalTransfer.getStatus())
                            .memo(internalTransfer.getMemo())
                            .createdAt(internalTransfer.getCreatedAt())
                            .completedAt(internalTransfer.getCompletedAt())
                            .build());
                }
                
                // 외부 전송에서 조회
                return transferRepository.getExternalTransferById(pool, transferId)
                    .compose(externalTransfer -> {
                        if (externalTransfer == null) {
                            return Future.succeededFuture(null);
                        }
                        return currencyRepository.getCurrencyById(pool, externalTransfer.getCurrencyId())
                            .map(currency -> TransferResponseDto.builder()
                                .transferId(externalTransfer.getTransferId())
                                .transferType("EXTERNAL")
                                .transactionType(externalTransfer.getTransactionType())
                                .orderNumber(externalTransfer.getOrderNumber())
                                .senderId(externalTransfer.getUserId())
                                .toAddress(externalTransfer.getToAddress())
                                .currencyCode(currency.getCode())
                                .network(externalTransfer.getChain())
                                .amount(externalTransfer.getAmount())
                                .fee(externalTransfer.getFee())
                                .networkFee(externalTransfer.getNetworkFee())
                                .status(externalTransfer.getStatus())
                                .txHash(externalTransfer.getTxHash())
                                .memo(externalTransfer.getMemo())
                                .createdAt(externalTransfer.getCreatedAt())
                                .completedAt(externalTransfer.getConfirmedAt())
                                .build());
                    });
            });
    }
}

