package com.foxya.coin.transfer;

import com.foxya.coin.airdrop.AirdropRepository;
import com.foxya.coin.airdrop.entities.AirdropTransfer;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.enums.ChainType;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.common.enums.TransactionType;
import com.foxya.coin.common.utils.OrderNumberUtils;
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

    /** Redis 筌롪퉭踰???TTL (7?? ????μ맄) */
    private static final int CONFIRMED_IDEMPOTENCY_TTL_SECONDS = 7 * 24 * 3600;
    private static final String REDIS_KEY_CONFIRMED = "transfer:confirmed:";
    private static final String REDIS_KEY_FAILED = "transfer:failed:";
    private static final String INTERNAL_CHAIN = "INTERNAL";
    private static final int WITHDRAWAL_REDISPATCH_MAX_RETRY = 50;

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final WalletRepository walletRepository;
    private final EventPublisher eventPublisher;
    private final RedisAPI redisApi;
    private final NotificationService notificationService;
    private final AirdropRepository airdropRepository;

    // ??? ?袁⑸꽊 ??뤿땾??(0.1%)
    private static final BigDecimal INTERNAL_FEE_RATE = new BigDecimal("0.001");
    // 筌ㅼ뮇???袁⑸꽊 疫뀀뜆釉?
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
     * ??? ?袁⑸꽊 ??쎈뻬.
     * ??? ?袁⑸꽊 = DB筌????? ?됰뗀以됵㎗?곸뵥 ?紐껋삏??????곸벉. user_wallets ?遺용만 筌앹빓而?+ internal_transfers 疫꿸퀡以됵쭕???묐뻬.
     */
    public Future<TransferResponseDto> executeInternalTransfer(Long senderId, InternalTransferRequestDto request, String requestIp) {
        log.info("??? ?袁⑸꽊 ?遺욧퍕 - senderId: {}, receiverType: {}, receiverValue: {}, amount: {}", 
            senderId, request.getReceiverType(), request.getReceiverValue(), request.getAmount());
        
        // 1. ?醫륁뒞??野꺜??
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("筌ㅼ뮇???袁⑸꽊 疫뀀뜆釉?? " + MIN_TRANSFER_AMOUNT + " ??낅빍??"));
        }
        
        // 2. ???넅 鈺곌퀬??(??? ?袁⑸꽊?? ??湲?INTERNAL 筌ｋ똻??????
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getCurrencyCode(), "INTERNAL")
            .compose(externalCurrency -> {
                if (externalCurrency == null) {
                    return Future.failedFuture(new NotFoundException("???넅??筌≪뼚??????곷뮸??덈뼄: " + request.getCurrencyCode() + " on INTERNAL"));
                }
                
                // 3. ??뤿뻿??鈺곌퀬??
                return findReceiver(request.getReceiverType(), request.getReceiverValue())
                    .compose(receiver -> {
                        if (receiver == null) {
                            return Future.failedFuture(new NotFoundException("??뤿뻿?癒? 筌≪뼚??????곷뮸??덈뼄."));
                        }
                        
                        if (receiver.getId().equals(senderId)) {
                            return Future.failedFuture(new BadRequestException("?癒?┛ ?癒?뻿?癒?쓺 ?袁⑸꽊??????곷뮸??덈뼄."));
                        }
                        
                        // 4. ??る뻿??筌왖揶?鈺곌퀬??
                        return getOrCreateInternalWallet(senderId, externalCurrency, false)
                            .compose(senderWallet ->
                                getOrCreateInternalWallet(receiver.getId(), externalCurrency, true)
                                    .compose(receiverWallet -> {
                                        // 6. ??뤿땾???④쑴沅?
                                        BigDecimal fee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                                        BigDecimal totalDeduct = request.getAmount().add(fee);

                                        // 7. ?遺용만 ?類ㅼ뵥
                                        if (senderWallet.getBalance().compareTo(totalDeduct) < 0) {
                                            return Future.failedFuture(new BadRequestException("?遺용만???봔鈺곌퉲鍮??덈뼄. ?袁⑹뒄: " + totalDeduct + ", 癰귣똻?: " + senderWallet.getBalance()));
                                        }

                                        // 8. ?袁⑸꽊 ??쎈뻬 (?紐껋삏????
                                        return executeInternalTransferTransaction(
                                            senderId, receiver.getId(),
                                            senderWallet, receiverWallet,
                                            externalCurrency, request.getAmount(), fee,
                                            request.getMemo(), requestIp
                                        );
                                    })
                            );
                    });
            });
    }
    
    /**
     * ??? ?袁⑸꽊 ?紐껋삏??????쎈뻬 (DB筌? deductBalance / addBalance / internal_transfers)
     */
    private Future<TransferResponseDto> executeInternalTransferTransaction(
            Long senderId, Long receiverId,
            Wallet senderWallet, Wallet receiverWallet,
            Currency currency, BigDecimal amount, BigDecimal fee,
            String memo, String requestIp) {
        
        String transferId = UUID.randomUUID().toString();
        BigDecimal totalDeduct = amount.add(fee);
        
        // ?紐껋삏?????곗쨮 筌ｌ꼶??
        return pool.withTransaction(client -> {
            // 1. ??る뻿???遺용만 筌△몿而?
            return transferRepository.deductBalance(client, senderWallet.getId(), totalDeduct)
                .compose(updatedSenderWallet -> {
                    if (updatedSenderWallet == null) {
                        return Future.failedFuture(new BadRequestException("?遺용만 筌△몿而???쎈솭 (?遺용만 ?봔鈺?"));
                    }
                    
                    // 2. ??뤿뻿???遺용만 ?곕떽?
                    return transferRepository.addBalance(client, receiverWallet.getId(), amount);
                })
                .compose(updatedReceiverWallet -> {
                    if (updatedReceiverWallet == null) {
                        return Future.failedFuture(new BadRequestException("?遺용만 ?곕떽? ??쎈솭"));
                    }
                    
                    // 3. ?袁⑸꽊 疫꿸퀡以???밴쉐 (??? ?紐껋삏??????袁⑥셽???곕뗄????袁る퉸 transfer_id, order_number ??湲?疫꿸퀡以?
                    String orderNumber = OrderNumberUtils.generateOrderNumber();
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
                        .transactionType(TransactionType.WITHDRAW.getValue())
                        .memo(memo)
                        .requestIp(requestIp)
                        .build();
                    
                    return transferRepository.createInternalTransfer(client, transfer);
                })
                .compose(createdTransfer -> {
                    // 4. ?袁⑸꽊 ?袁⑥┷ 筌ｌ꼶??
                    return transferRepository.completeInternalTransfer(client, transferId);
                });
        }).map(completedTransfer -> {
            log.info("??? ?袁⑸꽊 ?袁⑥┷ - transferId: {}, sender: {}, receiver: {}, amount: {}", 
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
     * Use INTERNAL wallet first when available; fallback to external-chain wallet.
     */
    private Future<Wallet> resolvePreferredWalletForWithdrawal(Long userId, Currency externalCurrency) {
        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, externalCurrency.getId())
            .compose(externalWallet ->
                currencyRepository.getCurrencyByCodeAndChain(pool, externalCurrency.getCode(), INTERNAL_CHAIN)
                    .compose(internalCurrency -> {
                        if (internalCurrency == null) {
                            if (externalWallet == null) {
                                return Future.failedFuture(new NotFoundException("筌왖揶쏅쵐??筌≪뼚??????곷뮸??덈뼄."));
                            }
                            return Future.succeededFuture(externalWallet);
                        }
                        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, internalCurrency.getId())
                            .compose(internalWallet -> {
                                if (internalWallet != null) {
                                    return Future.succeededFuture(internalWallet);
                                }
                                if (externalWallet != null) {
                                    return Future.succeededFuture(externalWallet);
                                }
                                return Future.failedFuture(new NotFoundException("筌왖揶쏅쵐??筌≪뼚??????곷뮸??덈뼄."));
                            });
                    }));
    }
    
    /**
     * ??묐쓠????륁뵡 筌왖疫?(REFERRAL_REWARD: sender ??곸벉, ??뤿뻿??筌왖揶쏅쵐肉?KORI ?곕떽?)
     */
    public Future<InternalTransfer> createReferralRewardTransfer(Long referrerId, BigDecimal amount, String memo) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Future.failedFuture(new BadRequestException("筌왖疫?疫뀀뜆釉?? 0癰귣????뚣끉鍮???몃빍??"));
        }
        return currencyRepository.getCurrencyByCodeAndChainAllowInactive(pool, "KORI", "INTERNAL")
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("KORI ???넅??筌≪뼚??????곷뮸??덈뼄."));
                }
                return getOrCreateInternalWallet(referrerId, currency, true)
                    .compose(receiverWallet -> {
                        if (receiverWallet == null) {
                            return Future.failedFuture(new NotFoundException("?곕뗄荑??筌왖揶쏅쵐??筌≪뼚??????곷뮸??덈뼄."));
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
                            .orderNumber(OrderNumberUtils.generateOrderNumber())
                            .transactionType(TransactionType.REFERRAL_REWARD.getValue())
                            .memo(memo != null ? memo : "REFERRAL_REWARD")
                            .requestIp(null)
                            .build();
                        return pool.withTransaction(client -> transferRepository.addBalance(client, receiverWallet.getId(), amount)
                            .compose(updated -> transferRepository.createInternalTransfer(client, transfer)));
                    });
            });
    }
    
    /**
     * ??뤿뻿??鈺곌퀬??(????녿퓠 ?怨뺤뵬)
     */
    private Future<User> findReceiver(String receiverType, String receiverValue) {
        return switch (receiverType) {
            case InternalTransferRequestDto.RECEIVER_TYPE_ADDRESS -> 
                // 筌왖揶?雅뚯눘?쇗에?鈺곌퀬??
                transferRepository.getWalletByAddress(pool, receiverValue)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.succeededFuture(null);
                        }
                        return userRepository.getUserById(pool, wallet.getUserId());
                    });
            
            case InternalTransferRequestDto.RECEIVER_TYPE_REFERRAL_CODE -> 
                // ?곕뗄荑???꾨뗀諭뜻에?鈺곌퀬??
                userRepository.getUserByReferralCode(pool, receiverValue);
            
            case InternalTransferRequestDto.RECEIVER_TYPE_USER_ID -> 
                // ?醫? ID嚥?鈺곌퀬??(?온?귐딆쁽??
                userRepository.getUserById(pool, Long.parseLong(receiverValue));
            
            default -> Future.failedFuture(new BadRequestException("??롢걵????뤿뻿??????놁뿯??덈뼄: " + receiverType));
        };
    }

    private Future<Wallet> getOrCreateInternalWallet(Long userId, Currency currency, boolean createIfMissing) {
        return transferRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
            .compose(existing -> {
                if (existing != null) {
                    return Future.succeededFuture(existing);
                }
                if (!createIfMissing) {
                    return Future.failedFuture(new NotFoundException("筌왖揶쏅쵐??筌≪뼚??????곷뮸??덈뼄."));
                }
                return createInternalWalletIfNeeded(userId, currency)
                    .compose(created -> {
                        if (created == null) {
                            return Future.failedFuture(new NotFoundException("筌왖揶쏅쵐??筌≪뼚??????곷뮸??덈뼄."));
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
     * ?紐? ?袁⑸꽊 ?遺욧퍕 (?곗뮄??.
     * - ?醫??癒?쓺 癰귣똻肉э쭪???椰???? 筌왖揶쏅쵎彛????? ?곗뮄????뽯퓠?????쒖쮯筌△몿而????怨? ?醫?????? 筌왖揶?user_wallets) ??
     * - DB???곗뮄???遺욧퍕 疫꿸퀡以?+ ??? 筌왖揶??遺용만 ?醫됲닊. ??쇱젫 ??κ퍥???袁⑸꽊?? ???삸??筌롫뗄??筌왖揶?餓λ쵐釉곤쭪?揶??癒?퐣 Node ?源놁뵠 PENDING 椰꾨똻??筌ｌ꼶??
     */
    public Future<TransferResponseDto> requestExternalTransfer(Long userId, ExternalTransferRequestDto request, String requestIp) {
        log.info("?紐? ?袁⑸꽊 ?遺욧퍕 - userId: {}, toAddress: {}, amount: {}, chain: {}",
            userId, request.getToAddress(), request.getAmount(), request.getChain());

        // 1. ?醫륁뒞??野꺜??
        if (request.getAmount() == null || request.getAmount().compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            return Future.failedFuture(new BadRequestException("筌ㅼ뮇???袁⑸꽊 疫뀀뜆釉?? " + MIN_TRANSFER_AMOUNT + " ??낅빍??"));
        }

        if (request.getToAddress() == null || request.getToAddress().isEmpty()) {
            return Future.failedFuture(new BadRequestException("??뤿뻿 雅뚯눘?쇘몴???낆젾??곻폒?紐꾩뒄."));
        }

        // 2. 筌ｋ똻???醫륁뒞??野꺜??
        ChainType chainType = ChainType.fromValue(request.getChain());
        if (chainType == null) {
            return Future.failedFuture(new BadRequestException("筌왖?癒곕릭筌왖 ??낅뮉 筌ｋ똻???낅빍?? " + request.getChain()));
        }

        // 3. ???넅 鈺곌퀬??(?? KORI + TRON)
        return currencyRepository.getCurrencyByCodeAndChain(pool, request.getCurrencyCode(), request.getChain())
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("???넅??筌≪뼚??????곷뮸??덈뼄: " + request.getCurrencyCode() + " on " + request.getChain()));
                }

                // 4. ?醫? ??? 筌왖揶쏅쵎彛?鈺곌퀬?띠쮯????(?紐? 筌왖揶쏅쵐? ?????? ??놁벉)
                return resolvePreferredWalletForWithdrawal(userId, currency)
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("筌왖揶쏅쵐??筌≪뼚??????곷뮸??덈뼄."));
                        }

                        // 5. ??뤿땾???④쑴沅?(??쎈뱜??곌쾿 ??뤿땾?룸슢??Node.js?癒?퐣 ?④쑴沅?
                        BigDecimal serviceFee = request.getAmount().multiply(INTERNAL_FEE_RATE);
                        BigDecimal totalDeduct = request.getAmount().add(serviceFee);

                        // 6. ??? 筌왖揶?揶쎛???遺용만 ?類ㅼ뵥 (balance = ??? 揶쎛??명뀋筌???됱벉, locked_balance ??뽰뇚)
                        if (wallet.getBalance().compareTo(totalDeduct) < 0) {
                            return Future.failedFuture(new BadRequestException("?遺용만???봔鈺곌퉲鍮??덈뼄. ?袁⑹뒄: " + totalDeduct + ", 癰귣똻?: " + wallet.getBalance()));
                        }

                        // 7. ??? 筌왖揶??醫됲닊 + ?곗뮄???遺욧퍕 ??밴쉐 (??쇱젫 ??뷀닊?? 筌롫뗄??筌왖揶쏅쵐肉??筌ｌ꼶??
                        return createExternalTransferRequest(userId, wallet, currency, request, serviceFee, requestIp);
                    });
            });
    }
    
    /**
     * ?紐? ?袁⑸꽊 ?遺욧퍕 ??밴쉐: ?醫? ??? 筌왖揶??醫됲닊 + external_transfers PENDING 疫꿸퀡以?
     * ??쇱젫 ??κ퍥???袁⑸꽊?? ???삸??筌롫뗄??筌왖揶?餓λ쵐釉곤쭪?揶? ???묽揶쎛 PENDING 椰꾨똻????뚮선 筌ｌ꼶??
     */
    private Future<TransferResponseDto> createExternalTransferRequest(
            Long userId, Wallet wallet, Currency currency,
            ExternalTransferRequestDto request, BigDecimal serviceFee, String requestIp) {
        
        String transferId = UUID.randomUUID().toString();
        BigDecimal totalDeduct = request.getAmount().add(serviceFee);
        
        return pool.withTransaction(client -> {
            // 1. ?遺용만 ?醫됲닊
            return transferRepository.lockBalance(client, wallet.getId(), totalDeduct)
                .compose(updatedWallet -> {
                    if (updatedWallet == null) {
                        return Future.failedFuture(new BadRequestException("?遺용만 ?醫됲닊 ??쎈솭 (?遺용만 ?봔鈺?"));
                    }
                    
                    // 2. ?紐? ?袁⑸꽊 疫꿸퀡以???밴쉐
                    String orderNumber = OrderNumberUtils.generateOrderNumber();
                    ExternalTransfer transfer = ExternalTransfer.builder()
                        .transferId(transferId)
                        .userId(userId)
                        .walletId(wallet.getId())
                        .currencyId(currency.getId())
                        .toAddress(request.getToAddress())
                        .amount(request.getAmount())
                        .fee(serviceFee)
                        .networkFee(BigDecimal.ZERO) // Node.js?癒?퐣 ?④쑴沅?????낅쑓??꾨뱜
                        .status(ExternalTransfer.STATUS_PENDING)
                        .orderNumber(orderNumber)
                        .transactionType(TransactionType.WITHDRAW.getValue())
                        .chain(request.getChain())
                        .requiredConfirmations(getRequiredConfirmations(request.getChain()))
                        .retryCount(0)
                        .memo(request.getMemo())
                        .requestIp(requestIp)
                        .build();
                    
                    return transferRepository.createExternalTransfer(client, transfer);
                });
        }).compose(createdTransfer -> {
            log.info("?紐? ?袁⑸꽊 ?遺욧퍕 ??밴쉐 ?袁⑥┷ - transferId: {}", transferId);
            
            // 3. ??源??獄쏆뮉六?(Node.js ??뺥돩??쇰퓠??筌ｌ꼶??
            if (eventPublisher != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("transferId", transferId);
                payload.put("userId", userId);
                payload.put("toAddress", request.getToAddress());
                payload.put("amount", request.getAmount().toPlainString());
                payload.put("currencyCode", currency.getCode());
                payload.put("chain", request.getChain());
                
                eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                    .onFailure(e -> log.error("?紐? ?袁⑸꽊 ??源??獄쏆뮉六???쎈솭: {}", e.getMessage()));
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
     * 筌ｋ똻?ㅸ퉪??袁⑹뒄 ?뚢뫂????
     */
    private int getRequiredConfirmations(String chain) {
        return switch (chain) {
            case ExternalTransfer.CHAIN_TRON -> 20;
            case ExternalTransfer.CHAIN_ETH -> 12;
            default -> 1;
        };
    }

    /**
     * ?怨밴묶癰??紐? ?袁⑸꽊 筌뤴뫖以?(?곗뮄?????묽 ?뚢뫂???곕뗄???.
     */
    public Future<List<ExternalTransfer>> listExternalTransfersByStatus(String status, int limit) {
        return transferRepository.getExternalTransfersByStatus(pool, status, limit);
    }

    /**
     * Periodically republishes pending withdrawals so external settlement is eventually executed.
     */
    public Future<Integer> redispatchPendingWithdrawals(int limit) {
        if (eventPublisher == null) {
            return Future.succeededFuture(0);
        }
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return transferRepository.getPendingExternalTransfers(pool, safeLimit)
            .compose(pendingTransfers -> {
                if (pendingTransfers == null || pendingTransfers.isEmpty()) {
                    return Future.succeededFuture(0);
                }

                List<Future<Integer>> tasks = pendingTransfers.stream()
                    .map(transfer -> {
                        int retryCount = transfer.getRetryCount() != null ? transfer.getRetryCount() : 0;
                        if (retryCount >= WITHDRAWAL_REDISPATCH_MAX_RETRY) {
                            return Future.succeededFuture(0);
                        }
                        return currencyRepository.getCurrencyById(pool, transfer.getCurrencyId())
                            .compose(currency -> {
                                if (currency == null) {
                                    return Future.succeededFuture(0);
                                }
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("transferId", transfer.getTransferId());
                                payload.put("userId", transfer.getUserId());
                                payload.put("toAddress", transfer.getToAddress());
                                payload.put("amount", transfer.getAmount() != null ? transfer.getAmount().toPlainString() : null);
                                payload.put("currencyCode", currency.getCode());
                                payload.put("chain", transfer.getChain());
                                payload.put("retryCount", retryCount + 1);
                                return eventPublisher.publishToStream(EventType.WITHDRAWAL_REQUESTED, payload)
                                    .compose(v -> transferRepository.incrementExternalTransferRetryCount(pool, transfer.getTransferId()))
                                    .map(updated -> updated != null ? 1 : 0)
                                    .recover(err -> {
                                        log.warn("Failed to redispatch withdrawal - transferId: {}", transfer.getTransferId(), err);
                                        return Future.succeededFuture(0);
                                    });
                            });
                    })
                    .collect(Collectors.toList());

                return Future.all(tasks)
                    .map(result -> result.list().stream()
                        .mapToInt(item -> item instanceof Integer ? (Integer) item : 0)
                        .sum());
            });
    }

    /**
     * ?紐? ?袁⑸꽊 ??뽱뀱 筌ｌ꼶??(Node.js ???묽 ?源녿퓠???紐꾪뀱).
     * ??κ퍥??tx ??뽱뀱 ??txHash??疫꿸퀡以? ?怨밴묶??SUBMITTED嚥?癰궰野?
     */
    public Future<ExternalTransfer> submitExternalTransfer(String transferId, String txHash) {
        return transferRepository.getExternalTransferById(pool, transferId)
            .compose(et -> {
                if (et == null) {
                    return Future.failedFuture(new NotFoundException("?紐? ?袁⑸꽊??筌≪뼚??????곷뮸??덈뼄: " + transferId));
                }
                if (!ExternalTransfer.STATUS_PENDING.equals(et.getStatus()) && !ExternalTransfer.STATUS_PROCESSING.equals(et.getStatus())) {
                    return Future.failedFuture(new BadRequestException("??뽱뀱 揶쎛?館釉??怨밴묶揶쎛 ?袁⑤뻸??덈뼄. status=" + et.getStatus()));
                }
                return transferRepository.submitExternalTransfer(pool, transferId, txHash);
            });
    }

    /**
     * ?紐? ?袁⑸꽊 ?뚢뫂???袁⑥┷ 筌ｌ꼶??(Node.js ???묽 ?源녿퓠???紐꾪뀱).
     * ??? 筌왖揶??醫됲닊 ??곸젫(unlockBalance, refund=false)???紐껋삏?????곗쨮 ??묐뻬??뤿연
     * ?紐? 筌왖揶?筌△몿而???類ㅼ젟??롢늺 ??? ?貫???筌ㅼ뮇伊?筌△몿而?獄쏆꼷??
     * Redis 筌롪퉭踰???살쨮 餓λ쵎??筌ｌ꼶??獄쎻뫗?.
     */
    public Future<ExternalTransfer> confirmExternalTransfer(String transferId, int confirmations) {
        if (redisApi != null) {
            String key = REDIS_KEY_CONFIRMED + transferId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("?紐? ?袁⑸꽊 ??? ?뚢뫂??筌ｌ꼶???(筌롪퉭踰? - transferId: {}", transferId);
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

    /** ?곗뮄???袁⑥┷ ??notifications ?紐꾧퐣??(?????뿺, ?곕???FCM ?紐꾨뻻 ??뽰뒠) */
    private Future<ExternalTransfer> createWithdrawalCompletedNotification(ExternalTransfer confirmed) {
        if (notificationService == null || confirmed == null || confirmed.getUserId() == null) {
            return Future.succeededFuture(confirmed);
        }
        return currencyRepository.getCurrencyById(pool, confirmed.getCurrencyId())
            .compose(currency -> {
                String currencyCode = currency != null ? currency.getCode() : "";
                String amountStr = confirmed.getAmount() != null ? confirmed.getAmount().toPlainString() : "";
                String title = "\uCD9C\uAE08 \uC644\uB8CC";
                String message = amountStr + " " + currencyCode + " \uCD9C\uAE08\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";
                JsonObject meta = new JsonObject()
                    .put("transferId", confirmed.getTransferId())
                    .put("amount", amountStr)
                    .put("currencyCode", currencyCode)
                    .put("txHash", confirmed.getTxHash())
                    .put("toAddress", confirmed.getToAddress());
                return notificationService.createNotificationIfAbsentByRelatedId(
                    confirmed.getUserId(), NotificationType.WITHDRAW_SUCCESS, title, message, confirmed.getId(), meta.encode());
            })
            .map(v -> confirmed)
            .recover(err -> {
                log.warn("?곗뮄???袁⑥┷ ???뵝 ??밴쉐 ??쎈솭(?얜똻??: transferId={}", confirmed.getTransferId(), err);
                return Future.succeededFuture(confirmed);
            });
    }

    private Future<ExternalTransfer> doConfirmExternalTransfer(String transferId, int confirmations) {
        return pool.withTransaction(client ->
            transferRepository.getExternalTransferById(client, transferId)
                .compose(et -> {
                    if (et == null) {
                        return Future.failedFuture(new NotFoundException("?紐? ?袁⑸꽊??筌≪뼚??????곷뮸??덈뼄: " + transferId));
                    }
                    if (!ExternalTransfer.STATUS_SUBMITTED.equals(et.getStatus())) {
                        return Future.failedFuture(new BadRequestException(
                            "?뚢뫂??揶쎛?館釉??怨밴묶揶쎛 ?袁⑤뻸??덈뼄. status=" + et.getStatus()));
                    }
                    BigDecimal totalDeduct = et.getAmount().add(et.getFee() != null ? et.getFee() : BigDecimal.ZERO);
                    return transferRepository.confirmExternalTransfer(client, transferId, confirmations)
                        .compose(confirmed ->
                            transferRepository.unlockBalance(client, et.getWalletId(), totalDeduct, false)
                                .map(w -> confirmed));
                }));
    }

    /**
     * ?紐? ?袁⑸꽊 ??쎈솭 筌ｌ꼶??獄??遺용만 癰귣벀??(Node.js ???묽 ?源녿퓠???紐꾪뀱).
     * ??쎈솭 ????? 筌왖揶??醫됲닊 ??곸젫( refund=true )嚥??遺용만 癰귣벀??
     * Redis 筌롪퉭踰???살쨮 餓λ쵎??癰귣벀??獄쎻뫗?.
     */
    public Future<ExternalTransfer> failExternalTransferAndRefund(String transferId, String errorCode, String errorMessage) {
        if (redisApi != null) {
            String key = REDIS_KEY_FAILED + transferId;
            return redisApi.exists(List.of(key))
                .compose(reply -> {
                    if (reply != null && reply.toInteger() != null && reply.toInteger() > 0) {
                        log.info("?紐? ?袁⑸꽊 ??? ??쎈솭 筌ｌ꼶???(筌롪퉭踰? - transferId: {}", transferId);
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
                        return Future.failedFuture(new NotFoundException("?紐? ?袁⑸꽊??筌≪뼚??????곷뮸??덈뼄: " + transferId));
                    }
                    if (ExternalTransfer.STATUS_CONFIRMED.equals(et.getStatus()) || ExternalTransfer.STATUS_FAILED.equals(et.getStatus())) {
                        return Future.failedFuture(new BadRequestException("??? 筌ㅼ뮇伊?筌ｌ꼶????袁⑸꽊??낅빍?? status=" + et.getStatus()));
                    }
                    BigDecimal totalRefund = et.getAmount().add(et.getFee() != null ? et.getFee() : BigDecimal.ZERO);
                    return transferRepository.failExternalTransfer(client, transferId, errorCode, errorMessage)
                        .compose(failed ->
                            transferRepository.unlockBalance(client, et.getWalletId(), totalRefund, true)
                                .map(w -> failed));
                }));
    }

    /**
     * ?袁⑸꽊 ??곷열 鈺곌퀬??(??? + ?紐? + ?癒?선??뺤뿻 ????, OpenAPI TransferHistory ?類ㅻ뻼??곗쨮 獄쏆꼹??
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
                            // ??? ?袁⑸꽊 筌띲끋釉?(??묐쓠???癒?선??뺤뿻?? transactionType 癰귣똻???뤿연 ??곌볼 ?닌됲뀋)
                            List<Future<TransferResponseDto>> internalDtos = internalTransfers.stream()
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("INTERNAL")
                                        .transactionType(resolveInternalTransactionType(t))
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
                                .collect(Collectors.toList());

                            // ?紐? ?袁⑸꽊 筌띲끋釉?
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
                                .collect(Collectors.toList());

                            Set<String> internalOrderNumbers = internalTransfers.stream()
                                .map(InternalTransfer::getOrderNumber)
                                .filter(o -> o != null && !o.isEmpty())
                                .collect(Collectors.toSet());

                            // ?癒?선??뺤뿻 ?袁⑸꽊 筌띲끋釉?(internal??揶쏆늿? order_number揶쎛 ??곸뱽 ???춸 ??釉???餓λ쵎????볤탢)
                            List<Future<TransferResponseDto>> airdropDtos = airdropTransfers.stream()
                                .filter(at -> {
                                    String on = at.getOrderNumber();
                                    return on != null && !on.isEmpty() && !internalOrderNumbers.contains(on);
                                })
                                .map(t -> currencyRepository.getCurrencyById(pool, t.getCurrencyId())
                                    .map(currency -> TransferResponseDto.builder()
                                        .transferId(t.getTransferId())
                                        .transferType("INTERNAL")
                                        .transactionType(TransactionType.AIRDROP_TRANSFER.getValue())
                                        .orderNumber(t.getOrderNumber())
                                        .senderId(userId)
                                        .receiverId(userId)
                                        .currencyCode(currency.getCode())
                                        .amount(t.getAmount())
                                        .fee(BigDecimal.ZERO)
                                        .status(t.getStatus())
                                        .memo("?癒?선??뺤뿻 ??뚮씜 ??곸젫")
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
     * ??? ?袁⑸꽊???紐꾪뀱??transactionType 野껉퀣??(??묐쓠????륁뵡 / ?癒?선??뺤뿻 ?袁⑸꽊 / 疫꿸퀬?)
     */
    private String resolveInternalTransactionType(InternalTransfer t) {
        if (InternalTransfer.TYPE_REFERRAL_REWARD.equals(t.getTransferType())) {
            return TransactionType.REFERRAL_REWARD.getValue();
        }
        if (InternalTransfer.TYPE_ADMIN_GRANT.equals(t.getTransferType())) {
            String memo = t.getMemo();
            if (memo != null && memo.contains("?癒?선??뺤뿻")) {
                return TransactionType.AIRDROP_TRANSFER.getValue();
            }
        }
        return t.getTransactionType() != null ? t.getTransactionType() : TransactionType.TOKEN_DEPOSIT.getValue();
    }

    /**
     * ?袁⑸꽊 ?怨멸쉭 鈺곌퀬??
     */
    public Future<TransferResponseDto> getTransferDetail(String transferId) {
        // ?믪눘? ??? ?袁⑸꽊?癒?퐣 鈺곌퀬??
        return transferRepository.getInternalTransferById(pool, transferId)
            .compose(internalTransfer -> {
                if (internalTransfer != null) {
                    return currencyRepository.getCurrencyById(pool, internalTransfer.getCurrencyId())
                        .map(currency -> TransferResponseDto.builder()
                            .transferId(internalTransfer.getTransferId())
                            .transferType("INTERNAL")
                            .transactionType(resolveInternalTransactionType(internalTransfer))
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
                
                // ?紐? ?袁⑸꽊?癒?퐣 鈺곌퀬??
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



