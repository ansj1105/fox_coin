# TRON Node.js 서비스 설계 문서

## 📋 개요

Foxya Coin Service의 외부 전송(출금) 기능을 위한 Node.js 기반 TRON 네트워크 연동 서비스입니다.

## 🏗 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Foxya Coin Service                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐         ┌─────────────────┐               │
│  │   Vert.x API    │         │  Node.js TRON   │               │
│  │    Server       │◄───────►│    Service      │               │
│  └────────┬────────┘  Redis  └────────┬────────┘               │
│           │           Event           │                         │
│           │                           │                         │
│           ▼                           ▼                         │
│  ┌─────────────────┐         ┌─────────────────┐               │
│  │   PostgreSQL    │         │  TRON Network   │               │
│  │    Database     │         │  (TronGrid API) │               │
│  └─────────────────┘         └─────────────────┘               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 통신 방식

### Redis 이벤트 기반 통신

1. **Vert.x → Node.js** (출금 요청)
   - Redis Stream: `withdrawal:requested`
   - Payload: `{ transferId, userId, toAddress, amount, currencyCode, chain }`

2. **Node.js → Vert.x** (처리 결과)
   - Redis Stream: `withdrawal:processing`, `withdrawal:completed`, `withdrawal:failed`
   - Payload: `{ transferId, txHash, status, errorCode, errorMessage }`

## 📁 Node.js 프로젝트 구조

```
foxya-tron-service/
├── src/
│   ├── config/
│   │   ├── index.js          # 설정 로더
│   │   └── tron.config.js    # TRON 네트워크 설정
│   │
│   ├── services/
│   │   ├── TronService.js    # TronWeb 래퍼
│   │   ├── TokenService.js   # TRC-20 토큰 전송
│   │   └── WalletService.js  # 지갑 관리
│   │
│   ├── workers/
│   │   ├── WithdrawalWorker.js    # 출금 처리 워커
│   │   ├── DepositWorker.js       # 입금 감지 워커
│   │   └── ConfirmationWorker.js  # 컨펌 확인 워커
│   │
│   ├── utils/
│   │   ├── redis.js          # Redis 클라이언트
│   │   ├── logger.js         # 로깅
│   │   └── crypto.js         # 암호화 유틸
│   │
│   └── index.js              # 메인 엔트리
│
├── package.json
├── .env.example
└── README.md
```

## 🔧 주요 컴포넌트

### 1. TronService.js

```javascript
const TronWeb = require('tronweb');

class TronService {
  constructor(config) {
    this.tronWeb = new TronWeb({
      fullHost: config.fullNode,
      headers: { 'TRON-PRO-API-KEY': config.apiKey },
      privateKey: config.masterPrivateKey
    });
  }

  // TRX 전송
  async sendTRX(toAddress, amount) {
    const tx = await this.tronWeb.trx.sendTransaction(toAddress, amount);
    return tx;
  }

  // TRC-20 토큰 전송
  async sendTRC20(contractAddress, toAddress, amount) {
    const contract = await this.tronWeb.contract().at(contractAddress);
    const tx = await contract.transfer(toAddress, amount).send();
    return tx;
  }

  // 트랜잭션 조회
  async getTransaction(txHash) {
    return await this.tronWeb.trx.getTransaction(txHash);
  }

  // 트랜잭션 정보 조회
  async getTransactionInfo(txHash) {
    return await this.tronWeb.trx.getTransactionInfo(txHash);
  }

  // 주소 유효성 검사
  isValidAddress(address) {
    return this.tronWeb.isAddress(address);
  }
}

module.exports = TronService;
```

### 2. WithdrawalWorker.js

```javascript
const { Redis } = require('ioredis');
const TronService = require('../services/TronService');

class WithdrawalWorker {
  constructor(redis, tronService) {
    this.redis = redis;
    this.tronService = tronService;
    this.consumerGroup = 'withdrawal-group';
    this.consumerName = 'withdrawal-worker-1';
  }

  async start() {
    // Consumer Group 생성
    try {
      await this.redis.xgroup('CREATE', 'withdrawal:requested', this.consumerGroup, '0', 'MKSTREAM');
    } catch (e) {
      if (!e.message.includes('BUSYGROUP')) throw e;
    }

    // 메시지 소비 루프
    while (true) {
      const messages = await this.redis.xreadgroup(
        'GROUP', this.consumerGroup, this.consumerName,
        'COUNT', 10, 'BLOCK', 5000,
        'STREAMS', 'withdrawal:requested', '>'
      );

      if (messages) {
        for (const [stream, entries] of messages) {
          for (const [id, fields] of entries) {
            await this.processWithdrawal(id, fields);
          }
        }
      }
    }
  }

  async processWithdrawal(messageId, fields) {
    const data = this.parseFields(fields);
    const { transferId, toAddress, amount, currencyCode } = data;

    try {
      // 1. 주소 유효성 검사
      if (!this.tronService.isValidAddress(toAddress)) {
        throw new Error('INVALID_ADDRESS');
      }

      // 2. Processing 이벤트 발행
      await this.publishEvent('withdrawal:processing', { transferId, status: 'PROCESSING' });

      // 3. 토큰 전송 실행
      const tx = await this.tronService.sendTRC20(
        process.env.TOKEN_CONTRACT_ADDRESS,
        toAddress,
        amount
      );

      // 4. Submitted 이벤트 발행
      await this.publishEvent('withdrawal:submitted', {
        transferId,
        txHash: tx.txid,
        status: 'SUBMITTED'
      });

      // 5. ACK
      await this.redis.xack('withdrawal:requested', this.consumerGroup, messageId);

    } catch (error) {
      // 실패 이벤트 발행
      await this.publishEvent('withdrawal:failed', {
        transferId,
        errorCode: error.code || 'UNKNOWN',
        errorMessage: error.message,
        status: 'FAILED'
      });

      await this.redis.xack('withdrawal:requested', this.consumerGroup, messageId);
    }
  }

  async publishEvent(channel, data) {
    await this.redis.xadd(channel, '*', 'data', JSON.stringify(data));
  }

  parseFields(fields) {
    const data = {};
    for (let i = 0; i < fields.length; i += 2) {
      const key = fields[i];
      const value = fields[i + 1];
      if (key === 'data') {
        Object.assign(data, JSON.parse(value));
      } else {
        data[key] = value;
      }
    }
    return data;
  }
}

module.exports = WithdrawalWorker;
```

### 3. ConfirmationWorker.js

```javascript
class ConfirmationWorker {
  constructor(redis, tronService, db) {
    this.redis = redis;
    this.tronService = tronService;
    this.db = db;
    this.requiredConfirmations = 20; // TRON 권장 컨펌 수
  }

  async start() {
    // 10초마다 확인
    setInterval(() => this.checkConfirmations(), 10000);
  }

  async checkConfirmations() {
    // SUBMITTED 상태인 전송 조회
    const pendingTransfers = await this.db.query(
      `SELECT * FROM external_transfers 
       WHERE status = 'SUBMITTED' AND tx_hash IS NOT NULL`
    );

    for (const transfer of pendingTransfers) {
      try {
        const txInfo = await this.tronService.getTransactionInfo(transfer.tx_hash);
        
        if (txInfo && txInfo.blockNumber) {
          const currentBlock = await this.tronService.getCurrentBlock();
          const confirmations = currentBlock - txInfo.blockNumber;

          if (confirmations >= this.requiredConfirmations) {
            // 컨펌 완료 이벤트 발행
            await this.publishEvent('withdrawal:completed', {
              transferId: transfer.transfer_id,
              txHash: transfer.tx_hash,
              confirmations,
              status: 'CONFIRMED'
            });
          }
        }
      } catch (error) {
        console.error(`Failed to check confirmation for ${transfer.transfer_id}:`, error);
      }
    }
  }

  async publishEvent(channel, data) {
    await this.redis.xadd(channel, '*', 'data', JSON.stringify(data));
  }
}

module.exports = ConfirmationWorker;
```

## 🔐 보안 고려사항

### 1. Private Key 관리
```javascript
// .env (절대 Git에 커밋하지 않음)
TRON_MASTER_PRIVATE_KEY=encrypted_private_key
ENCRYPTION_KEY=your_encryption_key

// crypto.js
const crypto = require('crypto');

function decryptPrivateKey(encryptedKey, encryptionKey) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey, iv);
  // ...
}
```

### 2. Hot/Cold Wallet 분리
```
┌─────────────────┐     ┌─────────────────┐
│   Hot Wallet    │     │   Cold Wallet   │
│  (자동 출금용)   │     │  (대량 자금 보관) │
│  최소 잔액 유지  │◄────│  수동 충전       │
└─────────────────┘     └─────────────────┘
```

### 3. 출금 한도 설정
```javascript
const WITHDRAWAL_LIMITS = {
  perTransaction: 10000,  // 건당 최대
  perDay: 50000,          // 일일 최대
  perMonth: 500000        // 월간 최대
};
```

## 📊 모니터링

### 메트릭 수집
- 출금 요청 수
- 평균 처리 시간
- 실패율
- 네트워크 수수료

### 알림 설정
- 대량 출금 감지
- 연속 실패 감지
- Hot Wallet 잔액 부족
- 네트워크 지연

## 🚀 배포

### Docker Compose
```yaml
version: '3.8'

services:
  tron-service:
    build: .
    environment:
      - NODE_ENV=production
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - TRON_FULL_NODE=https://api.trongrid.io
      - TRON_API_KEY=${TRON_API_KEY}
    depends_on:
      - redis
    restart: unless-stopped
```

### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tron-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: tron-service
  template:
    spec:
      containers:
        - name: tron-service
          image: foxya/tron-service:latest
          env:
            - name: TRON_API_KEY
              valueFrom:
                secretKeyRef:
                  name: tron-secrets
                  key: api-key
```

## 📝 API 참고

### TronGrid API
- Full Node: `https://api.trongrid.io`
- Solidity Node: `https://api.trongrid.io`
- Event Server: `https://api.trongrid.io`

### TronWeb 문서
- https://developers.tron.network/docs/tronweb

### TRC-20 표준
- https://developers.tron.network/docs/trc20

## ⚠️ 주의사항

1. **테스트넷 먼저**: Nile/Shasta 테스트넷에서 충분히 테스트
2. **가스비 관리**: TRX 잔액 모니터링 (에너지/대역폭)
3. **재시도 로직**: 네트워크 오류 시 지수 백오프
4. **중복 방지**: 동일 요청 중복 처리 방지 (idempotency)
5. **감사 로그**: 모든 전송 기록 보관

