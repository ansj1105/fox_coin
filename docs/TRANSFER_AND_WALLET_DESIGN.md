# 전송·지갑 설계 (내부 vs 외부, TRON 동일 주소)

## 1. 내부 전송 vs 외부 전송

### 내부 전송 (Internal Transfer)

- **용도**: 앱 내 사용자 간 전송, 채굴 적립, 래퍼럴 보상, 에어드랍 등.
- **동작**: **DB만 사용**. 블록체인 트랜잭션 없음.
  - `user_wallets`: `balance` 증감 (`deductBalance` / `addBalance`).
  - `internal_transfers`: 전송 기록 저장.
- **통화**: 내부 전송은 항상 **INTERNAL 체인** (예: KORI INTERNAL). 실제 네트워크 주소 불필요(더미 주소 사용 가능).

### 외부 전송 (External Transfer, 출금)

- **용도**: 사용자가 외부 주소로 출금할 때.
- **동작**:
  1. **백엔드**: DB에 출금 요청 기록 (`external_transfers` STATUS=PENDING), 사용자 잔액 **잠금** (`lockBalance`).
  2. **실제 전송**: **중앙지갑**에서 Node.js 등 별도 서비스가 PENDING 건을 읽어 TRON/ETH/BTC 네트워크로 전송. 완료 시 `tx_hash`·STATUS 업데이트, 잔액 잠금 해제.
- **통화/체인**: TRON(KORI/TRX/USDT), ETH, BTC 등 실제 네트워크 사용.

---

## 2. KORI / TRX / USDT — TRON 동일 주소

- **정책**: TRON 체인에서 **KORI, TRX, USDT는 같은 지갑 주소**를 사용.
- **구현** (`WalletService.generateWalletAddress`):
  - KORI/TRX/USDT 중 하나로 TRON 지갑 생성 시, 이미 해당 사용자의 **TRON 지갑 주소가 있으면 그 주소를 재사용**.
  - 없을 때만 블록체인 서비스(TRON 서비스) 호출로 새 주소 생성.
- **DB**: `user_wallets`는 (user_id, currency_id)별로 행이 있지만, TRON의 KORI/TRX/USDT는 **동일한 `address`** 값을 가짐.

---

## 3. 참고

- 내부 전송: `TransferService.executeInternalTransfer`, `executeInternalTransferTransaction`.
- 외부 전송: `TransferService.requestExternalTransfer`, `createExternalTransferRequest` → 이벤트 `WITHDRAWAL_REQUESTED` → 별도 워커가 중앙지갑으로 전송.
- TRON 동일 주소: `WalletService.TRON_SAME_ADDRESS_CURRENCIES`, `generateWalletAddress` 내 TRON 주소 재사용 로직.
