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

## 3. 입금·스왑·외부전송 정리

### 3.1 유저 노출 = 내부 지갑만

- 유저에게 보여지는 잔액/이력은 **내부 지갑**(`user_wallets`)만 해당.
- 외부 지갑(온체인 주소)은 입금 주소 생성·입금 매칭용으로만 사용.

### 3.2 BTC / ETH / TRX(Tron) / USDT 입금 → 내부 지갑 반영

- **흐름**: 입금 조회(블록 스캐너 등) → `token_deposits` 생성/갱신(**userId 매칭 필수**) → `TokenDepositService.completeTokenDeposit(depositId)` 호출 → 트랜잭션 내 `user_wallets` 잔액 추가 + 입금 상태 COMPLETED.
- **전제**: 입금이 “어느 유저 입금 주소로 들어왔는지” 매칭되어 `token_deposits.user_id`가 설정된 뒤에만 `completeTokenDeposit` 호출 가능. 이 매칭(주소 → 유저)은 Node/스캐너 등 **외부 시스템**에서 수행해야 함.

### 3.3 스왑 = 반영된 금액(내부 지갑) 기반, 코인별 환율·수수료

- **흐름**: `SwapService.executeSwap` → `getWalletByUserIdAndCurrencyId`(내부 지갑) → `CurrencyService.getExchangeRate(from, to)` 환율 → 수수료·스프레드 적용 → `deductBalance` / `addBalance`(내부 지갑만).
- **환율**: `CurrencyService.getRateForCurrency(code)` — 현재 **ETH, USDT, KRWT**만 명시. BTC/TRX/KORI 등은 default 1(1:1). 실제 코인별 환율 반영 시 `getRateForCurrency` 확장 필요.

### 3.4 외부 전송 = 전부 메인 지갑(관리자/플랫폼 지갑) 기반

- 유저 출금 요청 → 유저 **내부 지갑** 잔액 잠금 → `external_transfers` PENDING → **메인 지갑**에서 실제 온체인 전송. 유저 외부 지갑에서 보내는 로직 없음.

---

## 4. 참고

- 내부 전송: `TransferService.executeInternalTransfer`, `executeInternalTransferTransaction`.
- 외부 전송: `TransferService.requestExternalTransfer`, `createExternalTransferRequest` → 이벤트 `WITHDRAWAL_REQUESTED` → 별도 워커가 중앙지갑으로 전송.
- TRON 동일 주소: `WalletService.TRON_SAME_ADDRESS_CURRENCIES`, `generateWalletAddress` 내 TRON 주소 재사용 로직.
