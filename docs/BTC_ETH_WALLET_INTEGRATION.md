# BTC/ETH 지갑 생성 통합 가이드

## 📋 개요

BTC와 ETH 지갑을 실제로 생성하기 위한 통합 방안입니다.

## 🔧 옵션 비교

### 옵션 1: TRON 서비스에 BTC/ETH 기능 추가 (권장) ⭐

**장점:**
- 기존 TRON 서비스와 일관성 유지
- 하나의 서비스로 모든 블록체인 지갑 관리
- Docker Compose 설정 단순화
- 코드 중복 최소화

**단점:**
- TRON 서비스에 의존성 추가 필요 (bitcoinjs-lib, ethers.js 등)

**구현:**
```javascript
// /Users/an/work/coin_publish/src/server/index.js

// BTC 지갑 생성
app.post("/api/wallet/create/btc", async (req, res) => {
  try {
    const { currencyCode } = req.body || {};
    const bitcoin = require('bitcoinjs-lib');
    const { ECPairFactory } = require('ecpair');
    const ecc = require('tiny-secp256k1');
    
    const ECPair = ECPairFactory(ecc);
    const keyPair = ECPair.makeRandom();
    const { address } = bitcoin.payments.p2pkh({ 
      pubkey: keyPair.publicKey,
      network: bitcoin.networks.bitcoin // 또는 testnet
    });
    
    res.json({ address });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ETH 지갑 생성
app.post("/api/wallet/create/eth", async (req, res) => {
  try {
    const { currencyCode } = req.body || {};
    const { ethers } = require('ethers');
    
    const wallet = ethers.Wallet.createRandom();
    
    res.json({ 
      address: wallet.address,
      // 필요시 privateKey도 저장 (암호화 필수)
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});
```

### 옵션 2: 별도 Blockchain Service 생성

**장점:**
- 서비스 분리로 독립적 관리
- 확장성 좋음

**단점:**
- Docker Compose 설정 복잡
- 서비스 간 통신 오버헤드

### 옵션 3: Java에서 직접 구현

**장점:**
- 외부 서비스 의존 없음

**단점:**
- Java 라이브러리 복잡 (bitcoinj, web3j)
- 메모리 사용량 증가
- 유지보수 어려움

## ✅ 권장 방안: TRON 서비스에 통합

### 1. TRON 서비스에 패키지 추가

```bash
cd /Users/an/work/coin_publish
npm install bitcoinjs-lib ecpair tiny-secp256k1 ethers
```

### 2. API 엔드포인트 추가

```javascript
// /Users/an/work/coin_publish/src/server/index.js

import { createTronWeb } from "../scripts/utils/tron.js";
import bitcoin from 'bitcoinjs-lib';
import { ECPairFactory } from 'ecpair';
import ecc from 'tiny-secp256k1';
import { ethers } from 'ethers';

const ECPair = ECPairFactory(ecc);

// 통합 지갑 생성 API (체인 자동 감지)
app.post("/api/wallet/create", async (req, res) => {
  try {
    const { currencyCode } = req.body || {};
    
    if (!currencyCode) {
      res.status(400).json({ error: "currencyCode is required" });
      return;
    }

    let address;

    // TRON 체인 (USDT, TRX, KORI)
    if (["USDT", "TRX", "KORI"].includes(currencyCode.toUpperCase())) {
      const tronWeb = createTronWeb();
      const account = await tronWeb.createAccount();
      address = account.address.base58;
    }
    // BTC
    else if (currencyCode.toUpperCase() === "BTC") {
      const keyPair = ECPair.makeRandom({ network: bitcoin.networks.bitcoin });
      const payment = bitcoin.payments.p2pkh({ 
        pubkey: keyPair.publicKey,
        network: bitcoin.networks.bitcoin
      });
      address = payment.address;
    }
    // ETH
    else if (currencyCode.toUpperCase() === "ETH") {
      const wallet = ethers.Wallet.createRandom();
      address = wallet.address;
    }
    else {
      res.status(400).json({ error: `Unsupported currency: ${currencyCode}` });
      return;
    }

    res.json({
      address: address,
      currencyCode: currencyCode
    });
  } catch (err) {
    console.error("Wallet creation error:", err);
    res.status(500).json({ error: err.message || "Internal server error" });
  }
});
```

### 3. Java 서비스 수정

`WalletService.java`에서 체인별로 적절히 처리:

```java
private Future<String> generateWalletAddress(Currency currency, String currencyCode) {
    // TRON 체인인 경우 TRON 서비스 호출
    if ("TRON".equalsIgnoreCase(currency.getChain())) {
        if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
            return callTronServiceToCreateWallet(currencyCode)
                .recover(throwable -> {
                    log.warn("TRON 서비스 호출 실패, 더미 주소 생성: {}", throwable.getMessage());
                    return Future.succeededFuture(generateDummyAddress(currencyCode, "TRON"));
                });
        }
    }
    // BTC, ETH도 TRON 서비스 호출 (통합 API)
    else if ("BTC".equalsIgnoreCase(currency.getChain()) || "ETH".equalsIgnoreCase(currency.getChain())) {
        if (tronServiceUrl != null && !tronServiceUrl.isEmpty()) {
            return callTronServiceToCreateWallet(currencyCode)
                .recover(throwable -> {
                    log.warn("블록체인 서비스 호출 실패, 더미 주소 생성: {}", throwable.getMessage());
                    return Future.succeededFuture(generateDummyAddress(currencyCode, currency.getChain()));
                });
        }
    }
    
    // 폴백: 더미 주소 생성
    return Future.succeededFuture(generateDummyAddress(currencyCode, currency.getChain()));
}
```

## 📦 필요한 npm 패키지

```json
{
  "dependencies": {
    "bitcoinjs-lib": "^6.1.5",
    "ecpair": "^2.0.1",
    "tiny-secp256k1": "^2.2.3",
    "ethers": "^6.8.0"
  }
}
```

## ⚙️ 환경 변수 설정

TRON 서비스의 `.env` 파일 또는 Docker Compose의 `env_file`에 다음 설정을 추가하세요:

```bash
# BTC Configuration
BTC_NETWORK=mainnet
# BTC_NETWORK=testnet

# ETH Configuration
ETH_NETWORK=mainnet
# ETH_NETWORK=sepolia
# ETH_NETWORK=goerli
ETH_RPC_URL=https://mainnet.infura.io/v3/your_infura_project_id
ETHERSCAN_API_KEY=your_etherscan_api_key
```

**주의**: 
- 프로덕션 배포 전에는 `testnet` 또는 `sepolia`로 테스트하세요
- Infura Project ID와 Etherscan API Key는 실제 값으로 변경하세요

## 🔒 보안 고려사항

1. **Private Key 관리**
   - Private Key는 절대 응답에 포함하지 않음
   - 필요시 암호화하여 별도 저장소에 보관
   - 사용자에게는 주소만 제공

2. **네트워크 선택**
   - BTC: mainnet vs testnet
   - ETH: mainnet vs testnet (Sepolia, Goerli 등)
   - 환경변수로 제어

3. **에러 처리**
   - 네트워크 오류 시 적절한 폴백
   - 로깅 및 모니터링

## 🚀 배포 순서

1. TRON 서비스에 패키지 설치
2. API 엔드포인트 추가
3. Java 서비스 코드 수정
4. 테스트 (testnet 사용 권장)
5. 프로덕션 배포

## 📝 참고

- **BitcoinJS**: https://github.com/bitcoinjs/bitcoinjs-lib
- **Ethers.js**: https://docs.ethers.org/
- **TRON Web**: https://developers.tron.network/docs/tronweb

