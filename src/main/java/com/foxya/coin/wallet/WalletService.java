package com.foxya.coin.wallet;

import com.foxya.coin.app.AppConfigRepository;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import io.vertx.redis.client.RedisAPI;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.auth.RecoverySignatureVerifier;
import com.foxya.coin.common.utils.PrivateKeyEncryptionUtil;
import com.foxya.coin.wallet.entities.VirtualWalletMapping;
import com.foxya.coin.wallet.entities.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.web3j.crypto.Credentials;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class WalletService extends BaseService {
    
    private final WalletRepository walletRepository;
    private final CurrencyRepository currencyRepository;
    private final WebClient webClient;
    private final String tronServiceUrl;
    private final RedisAPI redisApi;
    private final VirtualWalletMappingRepository virtualWalletMappingRepository;
    private final AppConfigRepository appConfigRepository;

    private static final String WALLET_RECOVERY_NONCE_PREFIX = "wallet:nonce:";
    private static final int WALLET_RECOVERY_TTL_SECONDS = 600;
    private static final String HOT_WALLET_USER_ID_KEY = "hot_wallet_user_id";
    private static final String HOT_WALLET_ADDRESS_ENV = "TRON_HOT_WALLET_ADDRESS";
    private static final String TRON_NETWORK = "TRON";
    /** TRON 체인에서 동일 지갑 주소를 쓰는 통화 (KORI, TRX, USDT) */
    private static final Set<String> TRON_SAME_ADDRESS_CURRENCIES = Set.of("KORI", "TRX", "USDT");
    
    public WalletService(
        PgPool pool,
        WalletRepository walletRepository,
        CurrencyRepository currencyRepository,
        WebClient webClient,
        String tronServiceUrl,
        RedisAPI redisApi,
        VirtualWalletMappingRepository virtualWalletMappingRepository,
        com.foxya.coin.app.AppConfigRepository appConfigRepository
    ) {
        super(pool);
        this.walletRepository = walletRepository;
        this.currencyRepository = currencyRepository;
        this.webClient = webClient;
        this.tronServiceUrl = tronServiceUrl;
        this.redisApi = redisApi;
        this.virtualWalletMappingRepository = virtualWalletMappingRepository;
        this.appConfigRepository = appConfigRepository;
    }
    
    /**
     * 사용자 지갑 조회 (필요한 네트워크 지갑 자동 생성)
     * TRC(TRON), BTC, ETH 네트워크의 지갑이 없으면 자동으로 생성
     */
    public Future<List<Wallet>> getUserWallets(Long userId) {
        return ensureSharedTronWallets(userId)
            .compose(v -> walletRepository.getWalletsByUserId(pool, userId))
            .map(WalletClientViewUtils::normalizeWalletsForClient);
    }

    public Future<CanonicalWalletSnapshot> getCanonicalWalletSnapshot(Long userId, String currencyCode) {
        String normalizedCurrencyCode = currencyCode == null || currencyCode.isBlank()
            ? "KORI"
            : currencyCode.trim().toUpperCase();
        return walletRepository.getWalletsByUserId(pool, userId)
            .map(wallets -> {
                List<Wallet> normalizedWallets = WalletClientViewUtils.normalizeWalletsForClient(wallets);
                BigDecimal totalBalance = BigDecimal.ZERO;
                BigDecimal lockedBalance = BigDecimal.ZERO;
                int walletCount = 0;
                for (Wallet wallet : normalizedWallets) {
                    if (wallet == null || !normalizedCurrencyCode.equalsIgnoreCase(wallet.getCurrencyCode())) {
                        continue;
                    }
                    totalBalance = totalBalance.add(wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance());
                    lockedBalance = lockedBalance.add(wallet.getLockedBalance() == null ? BigDecimal.ZERO : wallet.getLockedBalance());
                    walletCount += 1;
                }
                return CanonicalWalletSnapshot.builder()
                    .userId(userId)
                    .currencyCode(normalizedCurrencyCode)
                    .totalBalance(totalBalance)
                    .lockedBalance(lockedBalance)
                    .walletCount(walletCount)
                    .canonicalBasis("FOX_CLIENT_VISIBLE_TOTAL_KORI")
                    .build();
            });
    }
    
    /**
     * 지갑 생성
     * @param userId 사용자 ID
     * @param currencyCode 통화 코드 (예: KORI, USDT, ETH, BTC 등)
     * @return 생성된 지갑
     */
    public Future<Wallet> createWallet(Long userId, String currencyCode) {
        // 0. KRWT는 미구현 처리
        if ("KRWT".equalsIgnoreCase(currencyCode)) {
            return Future.failedFuture(new BadRequestException("KRWT 지갑 생성은 아직 구현되지 않았습니다."));
        }
        
        // 1. 통화 조회 (체인별로 조회, 여러 체인 시도)
        String chain = determineChain(currencyCode);
        if (isSeedBasedChain(chain)) {
            return Future.failedFuture(new BadRequestException("시드 기반 지갑 등록이 필요합니다."));
        }
        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, chain)
            .compose(currency -> {
                if (currency == null) {
                    // ETH의 경우 "Ether" 체인도 시도
                    if ("ETH".equalsIgnoreCase(currencyCode) && "ETH".equals(chain)) {
                        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, "Ether")
                            .compose(etherCurrency -> {
                                if (etherCurrency != null) {
                                    return Future.succeededFuture(etherCurrency);
                                }
                                // Ether도 없으면 일반 조회 시도
                                return currencyRepository.getCurrencyByCode(pool, currencyCode);
                            });
                    }
                    // 체인별 조회 실패 시 일반 조회 시도
                    return currencyRepository.getCurrencyByCode(pool, currencyCode);
                }
                return Future.succeededFuture(currency);
            })
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyCode + ". 통화를 먼저 등록해주세요."));
                }
                return Future.succeededFuture(currency);
            })
            .compose(currency -> {
                // 2. 이미 해당 통화의 지갑이 있는지 확인
                return walletRepository.existsByUserIdAndCurrencyId(pool, userId, currency.getId())
                    .compose(exists -> {
                        if (exists) {
                            // 기존 지갑 조회하여 반환
                            return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                                .compose(existingWallet -> {
                                    if (existingWallet == null) {
                                        return Future.failedFuture(new BadRequestException("이미 해당 통화의 지갑이 존재합니다: " + currencyCode));
                                    }
                                    // 기존 지갑 정보 반환 (통화 정보 포함)
                                    return Future.succeededFuture(Wallet.builder()
                                        .id(existingWallet.getId())
                                        .userId(existingWallet.getUserId())
                                        .currencyId(existingWallet.getCurrencyId())
                                        .currencyCode(currency.getCode())
                                        .currencyName(currency.getName())
                                        .currencySymbol(currency.getCode())
                                        .network(currency.getChain())
                                        .address(existingWallet.getAddress())
                                        .balance(existingWallet.getBalance())
                                        .lockedBalance(existingWallet.getLockedBalance())
                                        .status(existingWallet.getStatus())
                                        .createdAt(existingWallet.getCreatedAt())
                                        .updatedAt(existingWallet.getUpdatedAt())
                                        .build());
                                });
                        }
                        
                        // 3. 지갑 주소 생성 (TRON.js 서버 호출 또는 더미 주소)
                        return generateWalletAddress(userId, currency, currencyCode)
                            .compose(address -> createWalletRecord(userId, currency, address))
                            .recover(throwable -> {
                                // 중복 키 오류 발생 시 기존 지갑 조회하여 반환
                                if (throwable.getMessage() != null && throwable.getMessage().contains("uk_user_wallets_user_currency")) {
                                    log.warn("지갑 생성 중 중복 감지, 기존 지갑 반환 - userId: {}, currencyId: {}", userId, currency.getId());
                                    return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                                        .compose(existingWallet -> {
                                            if (existingWallet == null) {
                                                return Future.failedFuture(new BadRequestException("이미 해당 통화의 지갑이 존재합니다: " + currencyCode));
                                            }
                                            return Future.succeededFuture(existingWallet);
                                        });
                                }
                                return Future.failedFuture(throwable);
                            })
                            .map(wallet -> Wallet.builder()
                                .id(wallet.getId())
                                .userId(wallet.getUserId())
                                .currencyId(wallet.getCurrencyId())
                                .currencyCode(currency.getCode())
                                .currencyName(currency.getName())
                                .currencySymbol(currency.getCode())
                                .network(currency.getChain())
                                .address(wallet.getAddress())
                                .balance(wallet.getBalance())
                                .lockedBalance(wallet.getLockedBalance())
                                .status(wallet.getStatus())
                                .createdAt(wallet.getCreatedAt())
                                .updatedAt(wallet.getUpdatedAt())
                                .build());
                    });
            });
    }

    @lombok.Value
    @lombok.Builder
    public static class CanonicalWalletSnapshot {
        Long userId;
        String currencyCode;
        BigDecimal totalBalance;
        BigDecimal lockedBalance;
        Integer walletCount;
        String canonicalBasis;
    }

    public Future<String> requestWalletRegistrationChallenge(Long userId, String address, String chain) {
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("지갑 등록을 사용할 수 없습니다."));
        }
        String normalizedChain = normalizeChain(chain);
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        if (address == null || address.isBlank()) {
            return Future.failedFuture(new BadRequestException("지갑 주소를 입력해주세요."));
        }
        String normalizedAddress = normalizeAddress(normalizedChain, address);
        Future<Long> ownerUserIdFuture;
        if (TRON_NETWORK.equals(normalizedChain) && virtualWalletMappingRepository != null) {
            ownerUserIdFuture = virtualWalletMappingRepository.findByOwnerAddressAndNetwork(pool, normalizedAddress, normalizedChain)
                .map(existing -> existing != null ? existing.getUserId() : null);
        } else {
            ownerUserIdFuture = walletRepository.getWalletByAddressIgnoreCase(pool, normalizedAddress)
                .map(existing -> existing != null ? existing.getUserId() : null);
        }
        return ownerUserIdFuture.compose(ownerUserId -> {
            if (ownerUserId != null && !ownerUserId.equals(userId)) {
                return Future.failedFuture(new BadRequestException("이미 등록된 지갑 주소입니다."));
            }
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String key = buildWalletNonceKey(userId, normalizedChain, normalizedAddress);
            String message = buildWalletMessage(userId, normalizedChain, normalizedAddress, nonce);
            return redisApi.setex(key, String.valueOf(WALLET_RECOVERY_TTL_SECONDS), nonce)
                .map(v -> message);
        });
    }

    public Future<Wallet> registerWalletWithSignature(Long userId, String currencyCode, String chain, String address, String signature) {
        if (redisApi == null) {
            return Future.failedFuture(new BadRequestException("지갑 등록을 사용할 수 없습니다."));
        }
        String normalizedChain = normalizeChain(chain);
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        if (address == null || address.isBlank()) {
            return Future.failedFuture(new BadRequestException("지갑 주소를 입력해주세요."));
        }
        if (signature == null || signature.isBlank()) {
            return Future.failedFuture(new BadRequestException("서명 값이 필요합니다."));
        }
        String normalizedAddress = normalizeAddress(normalizedChain, address);
        String normalizedSignature = signature.trim();
        String key = buildWalletNonceKey(userId, normalizedChain, normalizedAddress);
        return redisApi.get(key)
            .compose(nonceValue -> {
                if (nonceValue == null || nonceValue.toString() == null || nonceValue.toString().isBlank()) {
                    return Future.failedFuture(new BadRequestException("지갑 등록 요청이 만료되었습니다."));
                }
                String nonce = nonceValue.toString();
                String message = buildWalletMessage(userId, normalizedChain, normalizedAddress, nonce);
                boolean verified = verifySignature(normalizedChain, message, normalizedSignature, normalizedAddress);
                if (!verified) {
                    return Future.failedFuture(new BadRequestException("서명 검증에 실패했습니다."));
                }
                Future<Void> bindingFuture = TRON_NETWORK.equals(normalizedChain)
                    ? bindTronVirtualWalletMapping(userId, normalizedAddress)
                    : Future.succeededFuture();
                return bindingFuture
                    .compose(v -> createWalletWithAddress(userId, currencyCode, normalizedChain, normalizedAddress))
                    .compose(wallet -> redisApi.del(List.of(key)).map(v -> wallet));
            });
    }

    public Future<Wallet> storeWalletPrivateKey(Long userId, String currencyCode, String chain, String privateKey) {
        if (privateKey == null || privateKey.isBlank()) {
            return Future.failedFuture(new BadRequestException("privateKey is required."));
        }
        String normalizedChain = normalizeChain(chain);
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, normalizedChain)
            .compose(currency -> {
                if (currency == null) {
                    if ("ETH".equalsIgnoreCase(currencyCode) && "ETH".equalsIgnoreCase(normalizedChain)) {
                        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, "Ether")
                            .compose(etherCurrency -> etherCurrency != null ? Future.succeededFuture(etherCurrency) : currencyRepository.getCurrencyByCode(pool, currencyCode));
                    }
                    return currencyRepository.getCurrencyByCode(pool, currencyCode);
                }
                return Future.succeededFuture(currency);
            })
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyCode + ". 통화를 먼저 등록해주세요."));
                }
                String currencyChain = normalizeChain(currency.getChain());
                if (currencyChain == null) {
                    currencyChain = normalizedChain;
                }
                if (!currencyChain.equalsIgnoreCase(normalizedChain)) {
                    return Future.failedFuture(new BadRequestException("통화 네트워크가 일치하지 않습니다."));
                }
                return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                    .compose(wallet -> {
                        if (wallet == null) {
                            return Future.failedFuture(new NotFoundException("지갑을 찾을 수 없습니다."));
                        }
                        if ("ETH".equalsIgnoreCase(normalizedChain)) {
                            String normalizedKey = privateKey.startsWith("0x") ? privateKey.substring(2) : privateKey;
                            try {
                                String derivedAddress = Credentials.create(normalizedKey).getAddress();
                                if (!derivedAddress.equalsIgnoreCase(wallet.getAddress())) {
                                    return Future.failedFuture(new BadRequestException("privateKey가 지갑 주소와 일치하지 않습니다."));
                                }
                            } catch (Exception ex) {
                                return Future.failedFuture(new BadRequestException("유효하지 않은 privateKey 입니다."));
                            }
                        }
                        String encrypted = PrivateKeyEncryptionUtil.encrypt(privateKey);
                        return walletRepository.updatePrivateKeyById(pool, wallet.getId(), encrypted)
                            .map(v -> Wallet.builder()
                                .id(wallet.getId())
                                .userId(wallet.getUserId())
                                .currencyId(wallet.getCurrencyId())
                                .currencyCode(currency.getCode())
                                .currencyName(currency.getName())
                                .currencySymbol(currency.getCode())
                                .network(currency.getChain())
                                .address(wallet.getAddress())
                                .balance(wallet.getBalance())
                                .lockedBalance(wallet.getLockedBalance())
                                .status(wallet.getStatus())
                                .createdAt(wallet.getCreatedAt())
                                .updatedAt(wallet.getUpdatedAt())
                                .build());
                    });
            });
    }

    public Future<Wallet> syncInternalVirtualWallet(Long userId, Integer currencyId, String address, String privateKey, Boolean verified) {
        if (userId == null) {
            return Future.failedFuture(new BadRequestException("userId is required."));
        }
        if (currencyId == null) {
            return Future.failedFuture(new BadRequestException("currencyId is required."));
        }
        if (address == null || address.isBlank()) {
            return Future.failedFuture(new BadRequestException("address is required."));
        }

        String normalizedAddress = address.trim();
        return currencyRepository.getCurrencyById(pool, currencyId)
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyId));
                }

                String encryptedPrivateKey = privateKey == null || privateKey.isBlank()
                    ? null
                    : PrivateKeyEncryptionUtil.encrypt(privateKey);
                return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currencyId)
                    .compose(existingWallet -> {
                        if (existingWallet == null) {
                            if (encryptedPrivateKey == null) {
                                return Future.failedFuture(new BadRequestException("privateKey is required for first sync."));
                            }
                            return walletRepository.createWallet(pool, userId, currencyId, normalizedAddress, encryptedPrivateKey)
                                .compose(wallet -> {
                                    if (verified == null || !verified) {
                                        return Future.succeededFuture(wallet);
                                    }
                                    return walletRepository.updateVerifiedById(pool, wallet.getId(), true).map(v -> Wallet.builder()
                                        .id(wallet.getId())
                                        .userId(wallet.getUserId())
                                        .currencyId(wallet.getCurrencyId())
                                        .address(wallet.getAddress())
                                        .privateKey(wallet.getPrivateKey())
                                        .balance(wallet.getBalance())
                                        .lockedBalance(wallet.getLockedBalance())
                                        .verified(true)
                                        .status(wallet.getStatus())
                                        .createdAt(wallet.getCreatedAt())
                                        .updatedAt(wallet.getUpdatedAt())
                                        .build());
                                })
                                .map(wallet -> Wallet.builder()
                                    .id(wallet.getId())
                                    .userId(wallet.getUserId())
                                    .currencyId(wallet.getCurrencyId())
                                    .currencyCode(currency.getCode())
                                    .currencyName(currency.getName())
                                    .currencySymbol(currency.getCode())
                                    .network(currency.getChain())
                                    .address(wallet.getAddress())
                                    .balance(wallet.getBalance())
                                    .lockedBalance(wallet.getLockedBalance())
                                    .verified(Boolean.TRUE.equals(verified))
                                    .status(wallet.getStatus())
                                    .createdAt(wallet.getCreatedAt())
                                    .updatedAt(wallet.getUpdatedAt())
                                    .build());
                        }

                        Future<Wallet> walletFuture = Future.succeededFuture(existingWallet);
                        if (!normalizedAddress.equals(existingWallet.getAddress())) {
                            walletFuture = walletFuture.compose(wallet -> walletRepository.updateWalletAddressById(pool, wallet.getId(), normalizedAddress));
                        }
                        if (encryptedPrivateKey != null) {
                            walletFuture = walletFuture
                                .compose(wallet -> walletRepository.updatePrivateKeyById(pool, wallet.getId(), encryptedPrivateKey).map(v -> wallet));
                        }
                        if (verified != null && !verified.equals(existingWallet.getVerified())) {
                            walletFuture = walletFuture
                                .compose(wallet -> walletRepository.updateVerifiedById(pool, wallet.getId(), verified).map(v -> Wallet.builder()
                                    .id(wallet.getId())
                                    .userId(wallet.getUserId())
                                    .currencyId(wallet.getCurrencyId())
                                    .address(wallet.getAddress())
                                    .privateKey(wallet.getPrivateKey())
                                    .balance(wallet.getBalance())
                                    .lockedBalance(wallet.getLockedBalance())
                                    .verified(verified)
                                    .status(wallet.getStatus())
                                    .createdAt(wallet.getCreatedAt())
                                    .updatedAt(wallet.getUpdatedAt())
                                    .build()));
                        }

                        return walletFuture
                            .map(wallet -> Wallet.builder()
                                .id(wallet.getId())
                                .userId(wallet.getUserId())
                                .currencyId(wallet.getCurrencyId())
                                .currencyCode(currency.getCode())
                                .currencyName(currency.getName())
                                .currencySymbol(currency.getCode())
                                .network(currency.getChain())
                                .address(normalizedAddress)
                                .balance(wallet.getBalance())
                                .lockedBalance(wallet.getLockedBalance())
                                .verified(verified != null ? verified : wallet.getVerified())
                                .status(wallet.getStatus())
                                .createdAt(wallet.getCreatedAt())
                                .updatedAt(wallet.getUpdatedAt())
                                .build());
                    });
            });
    }

    public Future<Wallet> createWalletWithAddress(Long userId, String currencyCode, String chain, String address) {
        if ("KRWT".equalsIgnoreCase(currencyCode)) {
            return Future.failedFuture(new BadRequestException("KRWT 지갑 생성은 아직 구현되지 않았습니다."));
        }
        String normalizedChain = normalizeChain(chain);
        if (normalizedChain == null) {
            return Future.failedFuture(new BadRequestException("지원하지 않는 네트워크입니다."));
        }
        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, chain)
            .compose(currency -> {
                if (currency == null) {
                    if ("ETH".equalsIgnoreCase(currencyCode) && "ETH".equalsIgnoreCase(chain)) {
                        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, "Ether")
                            .compose(etherCurrency -> etherCurrency != null ? Future.succeededFuture(etherCurrency) : currencyRepository.getCurrencyByCode(pool, currencyCode));
                    }
                    return currencyRepository.getCurrencyByCode(pool, currencyCode);
                }
                return Future.succeededFuture(currency);
            })
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyCode + ". 통화를 먼저 등록해주세요."));
                }
                String currencyChain = normalizeChain(currency.getChain());
                if (currencyChain == null) {
                    currencyChain = normalizedChain;
                }
                if (!currencyChain.equalsIgnoreCase(normalizedChain)) {
                    return Future.failedFuture(new BadRequestException("통화 네트워크가 일치하지 않습니다."));
                }
                return walletRepository.existsByUserIdAndCurrencyId(pool, userId, currency.getId())
                    .compose(exists -> {
                        if (exists) {
                            return walletRepository.getWalletByUserIdAndCurrencyId(pool, userId, currency.getId())
                                .compose(existingWallet -> {
                                    if (existingWallet == null) {
                                        return Future.failedFuture(new BadRequestException("이미 해당 통화의 지갑이 존재합니다: " + currencyCode));
                                    }
                                    return Future.succeededFuture(existingWallet);
                                });
                        }
                        return createWalletRecord(userId, currency, address)
                            .map(wallet -> Wallet.builder()
                                .id(wallet.getId())
                                .userId(wallet.getUserId())
                                .currencyId(wallet.getCurrencyId())
                                .currencyCode(currency.getCode())
                                .currencyName(currency.getName())
                                .currencySymbol(currency.getCode())
                                .network(currency.getChain())
                                .address(wallet.getAddress())
                                .balance(wallet.getBalance())
                                .lockedBalance(wallet.getLockedBalance())
                                .status(wallet.getStatus())
                                .createdAt(wallet.getCreatedAt())
                                .updatedAt(wallet.getUpdatedAt())
                                .build());
                    });
            });
    }

    private Future<Void> ensureSharedTronWallets(Long userId) {
        return walletRepository.getWalletsByUserId(pool, userId)
            .compose(wallets -> {
                List<Wallet> tronWallets = wallets.stream()
                    .filter(this::isManagedTronWallet)
                    .toList();

                String sharedAddress = tronWallets.stream()
                    .map(Wallet::getAddress)
                    .filter(address -> address != null && !address.isBlank())
                    .findFirst()
                    .orElse(null);

                String sharedPrivateKey = tronWallets.stream()
                    .filter(wallet -> sharedAddress == null || sharedAddress.equals(wallet.getAddress()))
                    .map(Wallet::getPrivateKey)
                    .filter(privateKey -> privateKey != null && !privateKey.isBlank())
                    .findFirst()
                    .orElse(null);

                Future<Void> future = backfillSharedTronPrivateKeys(tronWallets, sharedPrivateKey);

                if (sharedAddress == null || sharedAddress.isBlank()) {
                    return future
                        .compose(v -> createWallet(userId, "TRX").mapEmpty())
                        .compose(v -> createWallet(userId, "USDT").mapEmpty())
                        .compose(v -> createWallet(userId, "KORI").mapEmpty());
                }

                for (String currencyCode : TRON_SAME_ADDRESS_CURRENCIES) {
                    boolean exists = tronWallets.stream()
                        .anyMatch(wallet -> currencyCode.equalsIgnoreCase(wallet.getCurrencyCode()));
                    if (!exists) {
                        future = future.compose(v -> createWalletWithAddress(userId, currencyCode, "TRON", sharedAddress).mapEmpty());
                    }
                }
                return future;
            });
    }

    private Future<Void> backfillSharedTronPrivateKeys(List<Wallet> tronWallets, String sharedPrivateKey) {
        if (sharedPrivateKey == null || sharedPrivateKey.isBlank()) {
            return Future.succeededFuture();
        }

        Future<Void> future = Future.succeededFuture();
        for (Wallet wallet : tronWallets) {
            if (wallet.getPrivateKey() == null || wallet.getPrivateKey().isBlank()) {
                future = future.compose(v -> walletRepository.updatePrivateKeyById(pool, wallet.getId(), sharedPrivateKey));
            }
        }
        return future;
    }

    private boolean isManagedTronWallet(Wallet wallet) {
        return wallet != null
            && "TRON".equalsIgnoreCase(wallet.getNetwork())
            && wallet.getCurrencyCode() != null
            && TRON_SAME_ADDRESS_CURRENCIES.contains(wallet.getCurrencyCode().toUpperCase());
    }

    private Future<Wallet> createWalletRecord(Long userId, com.foxya.coin.currency.entities.Currency currency, String address) {
        if (address == null || address.isBlank()) {
            return Future.failedFuture(new BadRequestException("지갑 주소가 비어 있습니다."));
        }

        Future<String> privateKeyFuture;
        if ("TRON".equalsIgnoreCase(currency.getChain()) && TRON_SAME_ADDRESS_CURRENCIES.contains(currency.getCode().toUpperCase())) {
            privateKeyFuture = resolveSharedTronPrivateKey(userId, address);
        } else {
            privateKeyFuture = Future.succeededFuture(null);
        }

        return privateKeyFuture.compose(privateKey -> walletRepository.createWallet(pool, userId, currency.getId(), address, privateKey));
    }

    private Future<String> resolveSharedTronPrivateKey(Long userId, String address) {
        return walletRepository.getWalletsByUserId(pool, userId)
            .map(wallets -> wallets.stream()
                .filter(this::isManagedTronWallet)
                .filter(wallet -> address.equals(wallet.getAddress()))
                .map(Wallet::getPrivateKey)
                .filter(privateKey -> privateKey != null && !privateKey.isBlank())
                .findFirst()
                .orElse(null));
    }

    private Future<Void> bindTronVirtualWalletMapping(Long userId, String ownerAddress) {
        if (virtualWalletMappingRepository == null) {
            return Future.succeededFuture();
        }
        return resolveTronHotWalletContext()
            .compose(context -> ensureVirtualWalletMapping(userId, context.hotWalletAddress(), ownerAddress))
            .map(mapping -> (Void) null)
            .recover(throwable -> {
                log.warn("Failed to bind TRON virtual wallet mapping. userId={}, ownerAddress={}, cause={}",
                    userId, ownerAddress, throwable.getMessage());
                return Future.<Void>succeededFuture();
            });
    }

    private Future<VirtualWalletMapping> ensureVirtualWalletMapping(Long userId, String hotWalletAddress, String ownerAddress) {
        String mappingSeed = "user:" + userId + ":" + TRON_NETWORK;
        String normalizedOwnerAddress = ownerAddress == null || ownerAddress.isBlank()
            ? null
            : normalizeAddress(TRON_NETWORK, ownerAddress);
        return virtualWalletMappingRepository.findByUserIdAndNetwork(pool, userId, TRON_NETWORK)
            .compose(existing -> {
                Future<Void> ownerValidationFuture = validateTronOwnerAddress(userId, normalizedOwnerAddress, existing != null ? existing.getId() : null);
                if (existing != null) {
                    return ownerValidationFuture.compose(v -> {
                        String nextOwnerAddress = normalizedOwnerAddress != null ? normalizedOwnerAddress : existing.getOwnerAddress();
                        boolean sameHotWallet = hotWalletAddress.equals(existing.getHotWalletAddress());
                        boolean sameOwner = Objects.equals(nextOwnerAddress, existing.getOwnerAddress());
                        if (sameHotWallet && sameOwner) {
                            return Future.succeededFuture(existing);
                        }
                        return virtualWalletMappingRepository.updateBinding(pool, existing.getId(), hotWalletAddress, nextOwnerAddress);
                    });
                }

                return ownerValidationFuture.compose(v -> {
                    String virtualAddress = VirtualWalletAddressGenerator.generateTronAddress(hotWalletAddress, mappingSeed);
                    return virtualWalletMappingRepository.create(pool, userId, TRON_NETWORK, hotWalletAddress, virtualAddress, normalizedOwnerAddress, mappingSeed);
                });
            });
    }

    private Future<Void> validateTronOwnerAddress(Long userId, String ownerAddress, Long currentMappingId) {
        if (ownerAddress == null || ownerAddress.isBlank()) {
            return Future.succeededFuture();
        }
        return virtualWalletMappingRepository.findByOwnerAddressAndNetwork(pool, ownerAddress, TRON_NETWORK)
            .compose(existing -> {
                if (existing == null) {
                    return Future.succeededFuture();
                }
                boolean sameMapping = currentMappingId != null && currentMappingId.equals(existing.getId());
                if (sameMapping || existing.getUserId().equals(userId)) {
                    return Future.succeededFuture();
                }
                return Future.failedFuture(new BadRequestException("이미 등록된 지갑 주소입니다."));
            });
    }

    private Future<TronHotWalletContext> resolveTronHotWalletContext() {
        String envHotWalletAddress = System.getenv(HOT_WALLET_ADDRESS_ENV);
        if (envHotWalletAddress != null && !envHotWalletAddress.isBlank()) {
            return Future.succeededFuture(new TronHotWalletContext(null, envHotWalletAddress.trim()));
        }
        return getHotWalletUserId()
            .compose(hotWalletUserId -> {
                if (hotWalletUserId == null) {
                    return Future.failedFuture(new NotFoundException("TRON 핫월렛 설정이 없습니다."));
                }
                return walletRepository.getWalletsByUserId(pool, hotWalletUserId)
                    .compose(wallets -> wallets.stream()
                        .filter(wallet -> TRON_NETWORK.equalsIgnoreCase(wallet.getNetwork()))
                        .filter(wallet -> wallet.getAddress() != null && !wallet.getAddress().isBlank())
                        .findFirst()
                        .<Future<TronHotWalletContext>>map(wallet ->
                            Future.succeededFuture(new TronHotWalletContext(hotWalletUserId, wallet.getAddress()))
                        )
                        .orElseGet(() -> Future.failedFuture(new NotFoundException("TRON 핫월렛 주소를 찾을 수 없습니다."))));
            });
    }

    private Future<Long> getHotWalletUserId() {
        if (appConfigRepository == null) {
            return Future.succeededFuture(null);
        }
        return appConfigRepository.getByKey(pool, HOT_WALLET_USER_ID_KEY)
            .map(value -> {
                if (value == null || value.isBlank()) {
                    return null;
                }
                try {
                    return Long.parseLong(value.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            });
    }

    private String normalizeChain(String chain) {
        if (chain == null) {
            return null;
        }
        String normalized = chain.trim().toUpperCase();
        return switch (normalized) {
            case "ETH", "TRON", "BTC", "INTERNAL" -> normalized;
            default -> null;
        };
    }

    private boolean isSeedBasedChain(String chain) {
        if (chain == null) {
            return false;
        }
        String normalized = chain.trim().toUpperCase();
        return "ETH".equals(normalized) || "TRON".equals(normalized) || "BTC".equals(normalized) || "ETHER".equals(normalized);
    }

    private String normalizeAddress(String chain, String address) {
        return "ETH".equals(chain) ? address.trim().toLowerCase() : address.trim();
    }

    private String buildWalletNonceKey(Long userId, String chain, String address) {
        return WALLET_RECOVERY_NONCE_PREFIX + userId + ":" + chain + ":" + address;
    }

    private String buildWalletMessage(Long userId, String chain, String address, String nonce) {
        return "FOXya Wallet Register\nUserId: " + userId + "\nChain: " + chain + "\nAddress: " + address + "\nNonce: " + nonce;
    }

    private boolean verifySignature(String chain, String message, String signature, String address) {
        if ("ETH".equals(chain)) {
            return RecoverySignatureVerifier.verifyEthSignature(message, signature, address);
        }
        if ("TRON".equals(chain)) {
            return RecoverySignatureVerifier.verifyTronSignature(message, signature, address);
        }
        if ("BTC".equals(chain)) {
            return RecoverySignatureVerifier.verifyBtcSignature(message, signature, address);
        }
        return false;
    }
    
    /**
     * 통화 코드에 따른 체인 결정
     * DB에 저장된 체인 이름과 매핑
     */
    private String determineChain(String currencyCode) {
        if ("USDT".equalsIgnoreCase(currencyCode) || "TRX".equalsIgnoreCase(currencyCode)) {
            return "TRON";
        } else if ("ETH".equalsIgnoreCase(currencyCode)) {
            // DB에는 "Ether"로 저장되어 있을 수 있으므로 둘 다 시도
            return "ETH"; // 우선 ETH로 시도, 없으면 "Ether"로 재시도
        } else if ("BTC".equalsIgnoreCase(currencyCode)) {
            return "BTC"; // BTC는 별도 체인
        } else if ("KORI".equalsIgnoreCase(currencyCode)) {
            return "TRON"; // KORI는 TRON 체인
        }
        return "TRON"; // 기본값
    }
    
    /**
     * 지갑 주소 생성.
     * KORI/TRX/USDT on TRON = 동일 주소 사용. 이미 TRON 지갑이 있으면 그 주소 재사용.
     * TRON/ETH/BTC = 블록체인 서비스 호출 또는 기존 주소 재사용.
     */
    private Future<String> generateWalletAddress(Long userId, com.foxya.coin.currency.entities.Currency currency, String currencyCode) {
        if ("TRON".equalsIgnoreCase(currency.getChain()) && TRON_SAME_ADDRESS_CURRENCIES.contains(currencyCode.toUpperCase())) {
            // KORI, TRX, USDT on TRON: 이미 사용 중인 TRON 주소가 있으면 동일 주소 재사용
            return walletRepository.getWalletsByUserId(pool, userId)
                .compose(wallets -> {
                    if (wallets == null) return Future.succeededFuture((String) null);
                    String existing = wallets.stream()
                        .filter(w -> "TRON".equalsIgnoreCase(w.getNetwork()))
                        .map(Wallet::getAddress)
                        .findFirst()
                        .orElse(null);
                    return Future.succeededFuture(existing);
                })
                .compose(existingAddress -> {
                    if (existingAddress != null && !existingAddress.isEmpty()) {
                        log.info("KORI/TRX/USDT TRON 동일 주소 재사용 - userId: {}, currencyCode: {}", userId, currencyCode);
                        return Future.succeededFuture(existingAddress);
                    }
                    if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
                        return callTronServiceToCreateWallet(userId, currencyCode);
                    }
                    return Future.failedFuture("블록체인 서비스 URL이 설정되지 않았습니다. 환경변수를 확인해주세요.");
                });
        }
        if ("TRON".equalsIgnoreCase(currency.getChain()) ||
            "BTC".equalsIgnoreCase(currency.getChain()) ||
            "ETH".equalsIgnoreCase(currency.getChain())) {
            if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
                return callTronServiceToCreateWallet(userId, currencyCode);
            }
            return Future.failedFuture("블록체인 서비스 URL이 설정되지 않았습니다. 환경변수를 확인해주세요.");
        }
        // INTERNAL 등: 더미 주소
        return Future.succeededFuture(generateDummyAddress(currencyCode, currency.getChain()));
    }
    
    /**
     * 블록체인 서비스(TRON 서비스)를 호출하여 지갑 생성
     * TRON, BTC, ETH 모두 지원
     */
    private Future<String> callTronServiceToCreateWallet(Long userId, String currencyCode) {
        String url = tronServiceUrl + "/api/wallet/create";
        JsonObject requestBody = new JsonObject()
            .put("userId", userId)
            .put("currencyCode", currencyCode);
        
        log.info("블록체인 서비스 호출 - URL: {}, userId: {}, currencyCode: {}", url, userId, currencyCode);
        
        return webClient.postAbs(url)
            .sendJsonObject(requestBody)
            .compose(response -> {
                if (response.statusCode() == 200) {
                    JsonObject body = response.bodyAsJsonObject();
                    if (body != null && body.containsKey("address")) {
                        String address = body.getString("address");
                        log.info("지갑 생성 성공 - currencyCode: {}, address: {}", currencyCode, address);
                        return Future.succeededFuture(address);
                    } else {
                        String errorMsg = "블록체인 서비스 응답에 address가 없습니다. 응답: " + (body != null ? body.encode() : "null");
                        log.error(errorMsg);
                        return Future.failedFuture(errorMsg);
                    }
                } else {
                    // 400 에러 등 상세 정보 로깅
                    String responseBody = "";
                    try {
                        if (response.body() != null) {
                            responseBody = response.bodyAsString();
                        }
                    } catch (Exception e) {
                        // 응답 본문 읽기 실패 시 무시
                    }
                    String errorMessage = String.format("블록체인 서비스 호출 실패 (status: %d, currencyCode: %s, response: %s)", 
                        response.statusCode(), currencyCode, responseBody);
                    log.error(errorMessage);
                    return Future.failedFuture(errorMessage);
                }
            })
            .recover(throwable -> {
                log.error("블록체인 서비스 네트워크 오류 - currencyCode: {}, error: {}", currencyCode, throwable.getMessage());
                return Future.failedFuture("블록체인 서비스 연결 실패: " + throwable.getMessage());
            });
    }
    
    /**
     * 더미 주소 생성 (TRON 서비스가 없거나 실패한 경우, 또는 BTC/ETH)
     */
    private String generateDummyAddress(String currencyCode, String chain) {
        // UUID 2개를 합쳐서 충분한 길이 확보
        String uuid1 = UUID.randomUUID().toString().replace("-", "");
        String uuid2 = UUID.randomUUID().toString().replace("-", "");
        String combined = uuid1 + uuid2; // 64자
        
        // 체인별 주소 형식
        if ("TRON".equalsIgnoreCase(chain)) {
            // TRON 주소: T로 시작, 총 34자 (T + 33자)
            return "T" + combined.substring(0, 33).toUpperCase();
        } else if ("ETH".equalsIgnoreCase(chain)) {
            // ETH 주소: 0x로 시작, 총 42자 (0x + 40자)
            return "0x" + combined.substring(0, 40).toLowerCase();
        } else if ("BTC".equalsIgnoreCase(currencyCode)) {
            // BTC 주소: bc1로 시작, 총 42자 (bc1 + 39자)
            return "bc1" + combined.substring(0, 39).toLowerCase();
        } else {
            // 기타 체인
            return "ADDR_" + currencyCode + "_" + uuid1.substring(0, 16).toUpperCase();
        }
    }

    /**
     * 시드 구문 존재 여부 확인
     * 사용자가 시드 기반 지갑(ETH, TRON, BTC)을 하나라도 등록했다면 시드 구문이 있다고 간주
     */
    public Future<Boolean> hasSeed(Long userId) {
        return walletRepository.getWalletsByUserId(pool, userId)
            .map(wallets -> {
                return wallets.stream()
                    .anyMatch(wallet -> wallet.getPrivateKey() != null && !wallet.getPrivateKey().isBlank());
            });
    }

    /**
     * 시드 구문으로 모든 지갑 자동 생성 (ETH, USDT(TRON), KORI(TRON), BTC)
     * 기존에 등록된 시드 기반 지갑 주소를 사용하여 나머지 지갑들을 생성
     * 같은 시드 구문에서 파생된 주소이므로 같은 주소를 사용
     */
    public Future<List<Wallet>> createAllWalletsFromSeed(Long userId) {
        return walletRepository.getWalletsByUserId(pool, userId)
            .compose(existingWallets -> {
                // ETH, TRON, BTC 체인의 지갑 주소 찾기
                String ethAddress = null;
                String tronAddress = null;
                String btcAddress = null;

                for (Wallet wallet : existingWallets) {
                    String network = wallet.getNetwork();
                    if (network != null) {
                        if ("ETH".equalsIgnoreCase(network) || "Ether".equalsIgnoreCase(network)) {
                            ethAddress = wallet.getAddress();
                        } else if ("TRON".equalsIgnoreCase(network)) {
                            tronAddress = wallet.getAddress();
                        } else if ("BTC".equalsIgnoreCase(network)) {
                            btcAddress = wallet.getAddress();
                        }
                    }
                }

                // 시드 구문이 없다면 에러
                if (ethAddress == null && tronAddress == null && btcAddress == null) {
                    return Future.failedFuture(new BadRequestException("시드 구문이 등록되지 않았습니다. 먼저 시드 구문으로 지갑을 등록해주세요."));
                }

                // 생성할 지갑 목록 (이미 존재하는 지갑은 제외)
                List<Future<Wallet>> walletFutures = new java.util.ArrayList<>();

                // ETH 지갑 생성 (없는 경우만)
                if (ethAddress != null) {
                    boolean hasEth = existingWallets.stream()
                        .anyMatch(w -> "ETH".equalsIgnoreCase(w.getCurrencyCode()) && 
                                      ("ETH".equalsIgnoreCase(w.getNetwork()) || "Ether".equalsIgnoreCase(w.getNetwork())));
                    if (!hasEth) {
                        walletFutures.add(createWalletWithAddress(userId, "ETH", "ETH", ethAddress));
                    }
                }

                // USDT(TRON) 지갑 생성 (없는 경우만)
                if (tronAddress != null) {
                    boolean hasTrx = existingWallets.stream()
                        .anyMatch(w -> "TRX".equalsIgnoreCase(w.getCurrencyCode()) && "TRON".equalsIgnoreCase(w.getNetwork()));
                    if (!hasTrx) {
                        walletFutures.add(createWalletWithAddress(userId, "TRX", "TRON", tronAddress));
                    }

                    boolean hasUsdt = existingWallets.stream()
                        .anyMatch(w -> "USDT".equalsIgnoreCase(w.getCurrencyCode()) && "TRON".equalsIgnoreCase(w.getNetwork()));
                    if (!hasUsdt) {
                        walletFutures.add(createWalletWithAddress(userId, "USDT", "TRON", tronAddress));
                    }
                    
                    boolean hasKori = existingWallets.stream()
                        .anyMatch(w -> "KORI".equalsIgnoreCase(w.getCurrencyCode()) && "TRON".equalsIgnoreCase(w.getNetwork()));
                    if (!hasKori) {
                        walletFutures.add(createWalletWithAddress(userId, "KORI", "TRON", tronAddress));
                    }
                }

                // KORI(INTERNAL) 지갑 생성 (채굴·에어드랍·래퍼럴 적립용, 없으면 생성)
                boolean hasKoriInternal = existingWallets.stream()
                    .anyMatch(w -> "KORI".equalsIgnoreCase(w.getCurrencyCode()) && "INTERNAL".equalsIgnoreCase(w.getNetwork()));
                if (!hasKoriInternal) {
                    String koriInternalAddress = "KORI_INTERNAL_" + userId;
                    walletFutures.add(createWalletWithAddress(userId, "KORI", "INTERNAL", koriInternalAddress));
                }

                // BTC 지갑 생성 (없는 경우만)
                if (btcAddress != null) {
                    boolean hasBtc = existingWallets.stream()
                        .anyMatch(w -> "BTC".equalsIgnoreCase(w.getCurrencyCode()) && "BTC".equalsIgnoreCase(w.getNetwork()));
                    if (!hasBtc) {
                        walletFutures.add(createWalletWithAddress(userId, "BTC", "BTC", btcAddress));
                    }
                }

                // 생성할 지갑이 없으면 빈 리스트 반환
                if (walletFutures.isEmpty()) {
                    return Future.succeededFuture(java.util.Collections.emptyList());
                }

                // 모든 지갑 생성 완료 대기
                return Future.all(walletFutures)
                    .map(results -> {
                        List<Wallet> createdWallets = new java.util.ArrayList<>();
                        for (int i = 0; i < results.size(); i++) {
                            try {
                                Wallet wallet = results.resultAt(i);
                                if (wallet != null) {
                                    createdWallets.add(wallet);
                                }
                            } catch (Exception e) {
                                // 이미 존재하는 지갑은 무시
                                log.debug("Wallet already exists, skipping: {}", e.getMessage());
                            }
                        }
                        return createdWallets;
                    });
            });
    }

    private record TronHotWalletContext(Long userId, String hotWalletAddress) {
    }

}
