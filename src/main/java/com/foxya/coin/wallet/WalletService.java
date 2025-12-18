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
        
        // 1. 통화 조회 (체인별로 조회, 여러 체인 시도)
        String chain = determineChain(currencyCode);
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
                            return Future.failedFuture(new BadRequestException("이미 해당 통화의 지갑이 존재합니다: " + currencyCode));
                        }
                        
                        // 3. 지갑 주소 생성 (TRON.js 서버 호출 또는 더미 주소)
                        return generateWalletAddress(userId, currency, currencyCode)
                            .compose(address -> walletRepository.createWallet(pool, userId, currency.getId(), address))
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
     * 지갑 주소 생성
     * TRON, BTC, ETH 모두 블록체인 서비스(TRON 서비스)를 호출하여 실제 주소 생성
     */
    private Future<String> generateWalletAddress(Long userId, com.foxya.coin.currency.entities.Currency currency, String currencyCode) {
        // TRON, BTC, ETH 모두 블록체인 서비스 호출
        if ("TRON".equalsIgnoreCase(currency.getChain()) || 
            "BTC".equalsIgnoreCase(currency.getChain()) || 
            "ETH".equalsIgnoreCase(currency.getChain())) {
            if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
                // 블록체인 서비스가 설정되어 있다면 실패 시에도 더미로 넘기지 않고 에러 반환
                return callTronServiceToCreateWallet(userId, currencyCode);
            } else {
                // 설정이 없으면 명확하게 실패 반환 (운영에서는 실주소 필요)
                return Future.failedFuture("블록체인 서비스 URL이 설정되지 않았습니다. 환경변수를 확인해주세요.");
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
}

