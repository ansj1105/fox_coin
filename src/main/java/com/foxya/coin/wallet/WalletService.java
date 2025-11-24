package com.foxya.coin.wallet;

import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import com.foxya.coin.common.BaseService;
import com.foxya.coin.wallet.entities.Wallet;

import java.util.List;

public class WalletService extends BaseService {
    
    private final WalletRepository walletRepository;
    
    public WalletService(PgPool pool, WalletRepository walletRepository) {
        super(pool);
        this.walletRepository = walletRepository;
    }
    
    public Future<List<Wallet>> getUserWallets(Long userId) {
        return walletRepository.getWalletsByUserId(pool, userId);
    }
}

