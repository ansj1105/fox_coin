package com.foxya.coin.transfer;

import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.currency.entities.Currency;
import com.foxya.coin.event.EventPublisher;
import com.foxya.coin.event.EventType;
import com.foxya.coin.transfer.dto.ExternalTransferRequestDto;
import com.foxya.coin.transfer.dto.InternalTransferRequestDto;
import com.foxya.coin.transfer.dto.TransferResponseDto;
import com.foxya.coin.transfer.entities.ExternalTransfer;
import com.foxya.coin.transfer.entities.InternalTransfer;
import com.foxya.coin.user.UserRepository;
import com.foxya.coin.user.entities.User;
import com.foxya.coin.wallet.entities.Wallet;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class TransferService extends BaseService {
    
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final EventPublisher eventPublisher;
    
    // 내부 전송 수수료 (0.1%)
    private static final BigDecimal INTERNAL_FEE_RATE = new BigDecimal("0.001");
    // 최소 전송 금액
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.000001");
    
    public TransferService(PgPool pool, 
                          TransferRepository transferRepository,
                          UserRepository userRepository,
                          CurrencyRepository currencyRepository,
                          EventPublisher eventPublisher) {
        super(pool);
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.currencyRepository = currencyRepository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 내부 전송 실행
     */
    public Future<TransferResponseDto> executeInternalTransfer(Long senderId, InternalTransferRequestDto request, String requestIp) {
        log.info("내부 전송 요청 - senderId: {}, receiverType: {}, receiverValue: {}, amount: {}", 
            senderId, request.getReceiverType(), request.getReceiverValue(), request.getAmount());
        
        // 1. 유효성 검사
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("최소 전송 금액은 " + MIN_TRANSFER_AMOUNT + " 입니다."));
        }
        
        // 2. 통화 조회
        return currencyRepository.getCurrencyByCode(pool, request.getCurrencyCode())
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + request.getCurrencyCode()));
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
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, senderId, currency.getId())
                            .compose(senderWallet -> {
                                if (senderWallet == null) {
                                    return Future.failedFuture(new NotFoundException("송신자 지갑을 찾을 수 없습니다."));
                                }
                                
                                // 5. 수신자 지갑 조회
                                return transferRepository.getWalletByUserIdAndCurrencyId(pool, receiver.getId(), currency.getId())
                                    .compose(receiverWallet -> {
                                        if (receiverWallet == null) {
                                            return Future.failedFuture(new NotFoundException("수신자 지갑을 찾을 수 없습니다."));
                                        }
                                        
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
                                    });
                            });
                    });
            });
    }
    
    /**
     * 내부 전송 트랜잭션 실행
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
                    
                    // 3. 전송 기록 생성
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
    
    /**
     * 외부 전송 요청 (출금)
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
        
        // 2. 통화 조회
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getCurrencyCode(), request.getChain())
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + request.getCurrencyCode() + " on " + request.getChain()));
                }
                
                // 3. 사용자 지갑 조회
                return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                        }
                        
                        // 4. 수수료 계산 (네트워크 수수료는 Node.js에서 계산)
                        BigDecimal serviceFee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                        BigDecimal totalDeduct = request.getAmount().add(serviceFee);
                        
                        // 5. 잔액 확인
                        if (wallet.getBalance().compareTo(totalDeduct) < 0) {
                            return Future.failedFuture(new BadRequestException("잔액이 부족합니다. 필요: " + totalDeduct + ", 보유: " + wallet.getBalance()));
                        }
                        
                        // 6. 외부 전송 요청 생성 (트랜잭션)
                        return createExternalTransferRequest(userId, wallet, currency, request, serviceFee, requestIp);
                    });
            });
    }
    
    /**
     * 외부 전송 요청 생성
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
                Map<String, Object> payload = new java.util.HashMap<>();
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
     * 전송 내역 조회
     */
    public Future<List<TransferResponseDto>> getTransferHistory(Long userId, int limit, int offset) {
        return transferRepository.getInternalTransfersByUserId(pool, userId, limit, offset)
            .map(transfers -> transfers.stream()
                .map(t -> TransferResponseDto.builder()
                    .transferId(t.getTransferId())
                    .transferType("INTERNAL")
                    .senderId(t.getSenderId())
                    .receiverId(t.getReceiverId())
                    .amount(t.getAmount())
                    .fee(t.getFee())
                    .status(t.getStatus())
                    .memo(t.getMemo())
                    .createdAt(t.getCreatedAt())
                    .completedAt(t.getCompletedAt())
                    .build())
                .collect(Collectors.toList()));
    }
    
    /**
     * 전송 상세 조회
     */
    public Future<TransferResponseDto> getTransferDetail(String transferId) {
        // 먼저 내부 전송에서 조회
        return transferRepository.getInternalTransferById(pool, transferId)
            .compose(internalTransfer -> {
                if (internalTransfer != null) {
                    return Future.succeededFuture(TransferResponseDto.builder()
                        .transferId(internalTransfer.getTransferId())
                        .transferType("INTERNAL")
                        .senderId(internalTransfer.getSenderId())
                        .receiverId(internalTransfer.getReceiverId())
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
                    .map(externalTransfer -> {
                        if (externalTransfer == null) {
                            return null;
                        }
                        return TransferResponseDto.builder()
                            .transferId(externalTransfer.getTransferId())
                            .transferType("EXTERNAL")
                            .senderId(externalTransfer.getUserId())
                            .toAddress(externalTransfer.getToAddress())
                            .amount(externalTransfer.getAmount())
                            .fee(externalTransfer.getFee())
                            .networkFee(externalTransfer.getNetworkFee())
                            .status(externalTransfer.getStatus())
                            .txHash(externalTransfer.getTxHash())
                            .memo(externalTransfer.getMemo())
                            .createdAt(externalTransfer.getCreatedAt())
                            .completedAt(externalTransfer.getConfirmedAt())
                            .build();
                    });
            });
    }
}

