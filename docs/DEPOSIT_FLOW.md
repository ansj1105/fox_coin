# 입금 감지 흐름 (DEPOSIT_DETECTED / DEPOSIT_CONFIRMED)

## 1. DEPOSIT_DETECTED는 누가 알아챔?

**이 Java 서비스에는 블록/지갑 주소를 주기적으로 조회하는 스캐너가 없습니다.**

- `DEPOSIT_DETECTED`는 **`TokenDepositService.registerTokenDeposit(deposit)`** 가 호출될 때만 발행됩니다.
- `registerTokenDeposit`을 **누가** 호출하느냐가 핵심입니다.
- **입금이 “요청된 게 있는지” 확인하려면, 어떤 컴포넌트가 “감시할 지갑 주소 목록”을 가지고 그 주소들에 대한 온체인 입금 트랜잭션을 주기적으로 조회**해야 합니다.

즉, **스캐너(또는 스케줄러 + 체인 조회)** 가 반드시 필요합니다.

---

## 2. 스캐너가 해야 할 일

1. **감시할 지갑 주소 목록 조회**  
   - 이 서비스의 **내부 API** `GET /api/v1/internal/deposits/watch-addresses` 로 “어느 주소를 감시할지” 목록을 가져옵니다.
2. **해당 주소들에 대한 온체인 입금 트랜잭션 주기적 조회**  
   - TRON/ETH/BTC 등 체인 API(TronGrid, Etherscan, Blockstream 등)로 각 주소의 **들어오는 tx** 를 조회합니다.
3. **입금 감지 시**  
   - 이 서비스의 **내부 API** `POST /api/v1/internal/deposits/register` 를 호출합니다.  
   - 서비스는 `token_deposits` 에 PENDING 레코드를 넣고 **DEPOSIT_DETECTED** 이벤트를 발행합니다. (클라이언트는 “처리 중” 상태를 알 수 있음)
4. **블록 확인(컨펌) 완료 시**  
   - 이 서비스의 **내부 API** `POST /api/v1/internal/deposits/:depositId/complete` 를 호출합니다.  
   - 서비스는 잔액 반영 + **DEPOSIT_CONFIRMED** 이벤트 발행. (클라이언트는 “입금 완료” 알림)

스캐너는 **이 서비스 밖**(Node.js 스크립트, Python 워커, 크론잡 등)에서 구현하고, 위 내부 API만 호출하면 됩니다.

---

## 3. 현재 구조 요약

| 구분 | 담당 | 비고 |
|------|------|------|
| 감시 주소 목록 | 이 서비스 | `GET /api/v1/internal/deposits/watch-addresses` |
| 지갑 주소 주기 조회 | **스캐너(별도 구현)** | 이 Java 서비스에는 **스케줄러/스캐너 없음** |
| 입금 감지 시 등록 | 스캐너 → 이 서비스 | `POST /api/v1/internal/deposits/register` → DEPOSIT_DETECTED |
| 입금 확인 완료 | 스캐너 → 이 서비스 | `POST /api/v1/internal/deposits/:id/complete` → DEPOSIT_CONFIRMED |

**정리:** “계속 지갑 주소들을 조회해서 요청된 게 있는지 확인”하는 주체는 **스캐너**이며, 스캐너는 위 내부 API로 “감시할 주소”만 이 서비스에서 받아오고, 실제 체인 조회는 스캐너가 수행합니다.

---

## 4. 스캐너 구현 위치

- **옵션 A:** 별도 서비스(Node.js, Python 등)에서 주기적으로  
  - `GET .../watch-addresses` 호출 →  
  - 체인 API로 입금 tx 조회 →  
  - `POST .../register`, `POST .../complete` 호출
- **옵션 B:** 이 서비스에 “입금 스캔” 스케줄러를 두고, 스케줄러는 **감시 주소 목록만 조회**한 뒤 **외부 스캐너 URL**에 그 목록을 넘겨주고, 실제 체인 조회·register/complete 호출은 외부 스캐너가 담당하도록 할 수 있습니다.

- **coin_publish** 저장소에 입금 스캔 스케줄러가 구현되어 있습니다.
  - `src/workers/DepositScanWorker.js`: 주기적으로 watch-addresses 조회 → 체인(TRON/ETH/BTC) 입금 tx 조회 → register, 컨펌 후 complete 호출.
  - 운영 기준 실행 명령은 `npm run worker:deposit` 입니다.
  - 누락분 재반영은 `npm run backfill:deposits -- --hours 720 --limit 30 --networks TRON` 처럼 별도 실행합니다.
  - 환경 변수: `COIN_SERVICE_URL`, `COIN_SERVICE_INTERNAL_API_KEY`, `DEPOSIT_SCAN_INTERVAL_MS`, `DEPOSIT_REQUIRED_CONFIRMATIONS`, `DEPOSIT_SCAN_LIMIT`, `DEPOSIT_BACKFILL_NETWORKS`.
- **foxya_coin_service** 쪽에는 내부 API만 제공. 인증: 헤더 `X-Internal-Api-Key` = `DEPOSIT_SCANNER_API_KEY` 또는 `depositScanner.apiKey`.
