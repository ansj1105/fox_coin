package com.foxya.coin.wallet;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.common.exceptions.BadRequestException;
import com.foxya.coin.common.exceptions.NotFoundException;
import com.foxya.coin.currency.CurrencyRepository;
import com.foxya.coin.wallet.entities.Wallet;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
public class WalletService extends BaseService {
    
    private final WalletRepository walletRepository;
    private final CurrencyRepository currencyRepository;
    private final WebClient webClient;
    private final String tronServiceUrl;
    
    public WalletService(PgPool pool, WalletRepository walletRepository, CurrencyRepository currencyRepository, WebClient webClient, String tronServiceUrl) {
        super(pool);
        this.walletRepository = walletRepository;
        this.currencyRepository = currencyRepository;
        this.webClient = webClient;
        this.tronServiceUrl = tronServiceUrl;
    }
    
    public Future<List<Wallet>> getUserWallets(Long userId) {
        return walletRepository.getWalletsByUserId(pool, userId);
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
        
        // 1. 통화 조회 (체인별로 조회)
        String chain = determineChain(currencyCode);
        return currencyRepository.getCurrencyByCodeAndChain(pool, currencyCode, chain)
            .compose(currency -> {
                if (currency == null) {
                    // 체인별 조회 실패 시 일반 조회 시도
                    return currencyRepository.getCurrencyByCode(pool, currencyCode)
                        .compose(generalCurrency -> {
                            if (generalCurrency == null) {
                                return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyCode));
                            }
                            return Future.succeededFuture(generalCurrency);
                        });
                }
                return Future.succeededFuture(currency);
            })
            .compose(currency -> {
                // 2. 이미 해당 통화의 지갑이 있는지 확인
                return walletRepository.existsByUserIdAndCurrencyId(pool, userId, currency.getId())
                    .compose(exists -> {
                        if (exists) {
                            return Future.failedFuture(new BadRequestException("이미 해당 통화의 지갑이 존재합니다: " + currencyCode));
                        }
                        
                        // 3. 지갑 주소 생성 (TRON.js 서버 호출 또는 더미 주소)
                        return generateWalletAddress(currency, currencyCode)
                            .compose(address -> {
                                // 4. 지갑 생성
                                return walletRepository.createWallet(pool, userId, currency.getId(), address);
                            });
                    });
            });
    }
    
    /**
     * 통화 코드에 따른 체인 결정
     */
    private String determineChain(String currencyCode) {
        if ("USDT".equalsIgnoreCase(currencyCode) || "TRX".equalsIgnoreCase(currencyCode)) {
            return "TRON";
        } else if ("ETH".equalsIgnoreCase(currencyCode)) {
            return "ETH";
        } else if ("BTC".equalsIgnoreCase(currencyCode)) {
            return "BTC"; // BTC는 별도 체인
        } else if ("KORI".equalsIgnoreCase(currencyCode)) {
            return "TRON"; // KORI는 TRON 체인
        }
        return "TRON"; // 기본값
    }
    
    /**
     * 지갑 주소 생성
     * TRON, BTC, ETH 모두 블록체인 서비스(TRON 서비스)를 호출하여 실제 주소 생성
     */
    private Future<String> generateWalletAddress(com.foxya.coin.currency.entities.Currency currency, String currencyCode) {
        // TRON, BTC, ETH 모두 블록체인 서비스 호출
        if ("TRON".equalsIgnoreCase(currency.getChain()) || 
            "BTC".equalsIgnoreCase(currency.getChain()) || 
            "ETH".equalsIgnoreCase(currency.getChain())) {
            if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
                return callTronServiceToCreateWallet(currencyCode)
                    .recover(throwable -> {
                        log.warn("블록체인 서비스 호출 실패, 더미 주소 생성: {}", throwable.getMessage());
                        return Future.succeededFuture(generateDummyAddress(currencyCode, currency.getChain()));
                    });
            } else {
                log.warn("블록체인 서비스 URL이 설정되지 않음, 더미 주소 생성");
                return Future.succeededFuture(generateDummyAddress(currencyCode, currency.getChain()));
            }
        } else {
            // INTERNAL 등 다른 체인은 더미 주소 생성
            return Future.succeededFuture(generateDummyAddress(currencyCode, currency.getChain()));
        }
    }
    
    /**
     * 블록체인 서비스(TRON 서비스)를 호출하여 지갑 생성
     * TRON, BTC, ETH 모두 지원
     */
    private Future<String> callTronServiceToCreateWallet(String currencyCode) {
        String url = tronServiceUrl + "/api/wallet/create";
        JsonObject requestBody = new JsonObject()
            .put("currencyCode", currencyCode);
        
        log.info("블록체인 서비스 호출 - URL: {}, currencyCode: {}", url, currencyCode);
        
        return webClient.postAbs(url)
            .sendJsonObject(requestBody)
            .compose(response -> {
                if (response.statusCode() == 200) {
                    JsonObject body = response.bodyAsJsonObject();
                    if (body.containsKey("address")) {
                        String address = body.getString("address");
                        log.info("지갑 생성 성공 - currencyCode: {}, address: {}", currencyCode, address);
                        return Future.succeededFuture(address);
                    } else {
                        return Future.failedFuture("블록체인 서비스 응답에 address가 없습니다");
                    }
                } else {
                    String errorMessage = "블록체인 서비스 호출 실패 (status: " + response.statusCode() + ")";
                    log.error(errorMessage);
                    return Future.failedFuture(errorMessage);
                }
            });
    }
    
    /**
     * 더미 주소 생성 (TRON 서비스가 없거나 실패한 경우, 또는 BTC/ETH)
     */
    private String generateDummyAddress(String currencyCode, String chain) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 체인별 주소 형식
        if ("TRON".equalsIgnoreCase(chain)) {
            return "T" + uuid.substring(0, 33).toUpperCase(); // TRON 주소 형식 (T로 시작, 34자)
        } else if ("ETH".equalsIgnoreCase(chain) || "BTC".equalsIgnoreCase(currencyCode)) {
            if ("BTC".equalsIgnoreCase(currencyCode)) {
                return "bc1" + uuid.substring(0, 39).toLowerCase(); // BTC 주소 형식 (bc1로 시작)
            } else {
                return "0x" + uuid.substring(0, 40).toLowerCase(); // ETH 주소 형식 (0x로 시작, 42자)
            }
        } else {
            return "ADDR_" + currencyCode + "_" + uuid.substring(0, 16).toUpperCase();
        }
    }
}

