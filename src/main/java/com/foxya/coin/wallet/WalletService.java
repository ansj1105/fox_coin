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
     * @param currencyCode 통화 코드 (예: KRWT, USDT, ETH 등)
     * @return 생성된 지갑
     */
    public Future<Wallet> createWallet(Long userId, String currencyCode) {
        // 1. 통화 조회
        return currencyRepository.getCurrencyByCode(pool, currencyCode)
            .compose(currency -> {
                if (currency == null) {
                    return Future.failedFuture(new NotFoundException("통화를 찾을 수 없습니다: " + currencyCode));
                }
                
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
     * 지갑 주소 생성
     * TRON 체인인 경우 TRON.js 서버를 호출하고, 그 외에는 더미 주소 생성
     */
    private Future<String> generateWalletAddress(com.foxya.coin.currency.entities.Currency currency, String currencyCode) {
        // TRON 체인인 경우 TRON.js 서버 호출
        if ("TRON".equalsIgnoreCase(currency.getChain())) {
            if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
                return callTronServiceToCreateWallet(currencyCode)
                    .recover(throwable -> {
                        log.warn("TRON 서비스 호출 실패, 더미 주소 생성: {}", throwable.getMessage());
                        return Future.succeededFuture(generateDummyAddress(currencyCode));
                    });
            } else {
                log.warn("TRON 서비스 URL이 설정되지 않음, 더미 주소 생성");
                return Future.succeededFuture(generateDummyAddress(currencyCode));
            }
        } else {
            // INTERNAL 등 다른 체인은 더미 주소 생성
            return Future.succeededFuture(generateDummyAddress(currencyCode));
        }
    }
    
    /**
     * TRON.js 서버를 호출하여 지갑 생성
     */
    private Future<String> callTronServiceToCreateWallet(String currencyCode) {
        String url = tronServiceUrl + "/api/wallet/create";
        JsonObject requestBody = new JsonObject()
            .put("currencyCode", currencyCode);
        
        log.info("TRON 서비스 호출 - URL: {}, currencyCode: {}", url, currencyCode);
        
        return webClient.postAbs(url)
            .sendJsonObject(requestBody)
            .compose(response -> {
                if (response.statusCode() == 200) {
                    JsonObject body = response.bodyAsJsonObject();
                    if (body.containsKey("address")) {
                        String address = body.getString("address");
                        log.info("TRON 지갑 생성 성공 - address: {}", address);
                        return Future.succeededFuture(address);
                    } else {
                        return Future.failedFuture("TRON 서비스 응답에 address가 없습니다");
                    }
                } else {
                    String errorMessage = "TRON 서비스 호출 실패 (status: " + response.statusCode() + ")";
                    log.error(errorMessage);
                    return Future.failedFuture(errorMessage);
                }
            });
    }
    
    /**
     * 더미 주소 생성 (TRON 서비스가 없거나 실패한 경우)
     */
    private String generateDummyAddress(String currencyCode) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "TADDR_" + currencyCode + "_" + uuid.substring(0, 16).toUpperCase();
    }
}

