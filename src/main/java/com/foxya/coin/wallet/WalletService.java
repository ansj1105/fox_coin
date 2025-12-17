package com.foxya.coin.wallet;

import io.vertx.core.Future;
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
    
    public WalletService(PgPool pool, WalletRepository walletRepository, CurrencyRepository currencyRepository) {
        super(pool);
        this.walletRepository = walletRepository;
        this.currencyRepository = currencyRepository;
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
                        
                        // 3. 지갑 주소 생성 (더미 주소 - 나중에 실제 블록체인 서비스로 교체 가능)
                        String address = generateWalletAddress(currencyCode);
                        
                        // 4. 지갑 생성
                        return walletRepository.createWallet(pool, userId, currency.getId(), address);
                    });
            });
    }
    
    /**
     * 지갑 주소 생성 (더미 구현)
     * 나중에 실제 블록체인 서비스 연동 시 이 메서드를 수정하면 됩니다.
     */
    private String generateWalletAddress(String currencyCode) {
        // UUID 기반 더미 주소 생성
        // 실제 구현에서는 블록체인 서비스를 호출하여 실제 주소를 생성해야 합니다
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "TADDR_" + currencyCode + "_" + uuid.substring(0, 16).toUpperCase();
    }
}

