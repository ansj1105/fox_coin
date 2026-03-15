# 출금(송금) 추적 흐름

> 2026-03 기준 운영 기준
>
> - `coin_publish`는 입금 스캐너 전용으로 축소한다.
> - 실제 출금 상태머신/브로드캐스트/재시도는 `korion-service(coin_manage)` 기준으로 운영한다.
> - 아래 문서는 레거시 `coin_publish` 출금 흐름을 설명하는 참고 문서이며, 신규 운영 기준으로는 출금 worker 소유권을 더 이상 `coin_publish`에 두지 않는다.

출금 요청은 코인 서비스(Java)에서 `external_transfers`에 PENDING으로 생성되고, Redis `withdrawal:requested` 스트림으로 이벤트가 발행된다.  
coin_publish의 **WithdrawalWorker**가 이벤트를 소비해 온체인 전송을 실행한 뒤, **내부 API**로 제출(txHash)을 알리고, **ConfirmationWorker** 스케줄러가 주기적으로 컨펌 수를 확인해 완료 시 내부 API로 컨펌 처리한다.

## 흐름 요약

1. 사용자 출금 요청 → 코인 서비스가 `external_transfers` PENDING 생성 + Redis `withdrawal:requested` 발행
2. WithdrawalWorker: 스트림 소비 → 온체인 전송 → **POST /api/v1/internal/withdrawals/:transferId/submit** (txHash)
3. ConfirmationWorker: 주기 실행 → **GET /api/v1/internal/withdrawals?status=SUBMITTED** 로 목록 조회 → 체인에서 컨펌 수 확인 → 필요 컨펌 도달 시 **POST /api/v1/internal/withdrawals/:transferId/confirm**

## 코인 서비스 내부 API (출금)

- 인증: `X-Internal-Api-Key` = `DEPOSIT_SCANNER_API_KEY` (입금과 동일)
- 베이스 경로: `/api/v1/internal/withdrawals`

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | /?status=SUBMITTED&limit=100 | 상태별 출금 목록 (컨펌 추적용) |
| POST | /:transferId/submit | Body: `{ "txHash": "..." }` — 온체인 제출 완료 알림 |
| POST | /:transferId/confirm | Body: `{ "confirmations": 20 }` — 컨펌 완료 (잔액 잠금 해제 등) |
| POST | /:transferId/fail | Body: `{ "errorCode", "errorMessage" }` — 실패 처리 (잔액 복구) |

## coin_publish 측

- **COIN_SERVICE_URL**, **COIN_SERVICE_INTERNAL_API_KEY** 가 설정되어 있으면:
  - WithdrawalWorker: 온체인 전송 성공 후 submit API 호출, 실패 시 fail API 호출
  - ConfirmationWorker: SUBMITTED 목록을 코인 서비스 GET로 조회하고, 컨펌 완료 시 confirm API 호출
- 미설정 시 기존처럼 로컬 DB(`external_transfers`)만 사용 (동일 DB 연동 환경용 폴백).

## 관련 코드

- Java: `InternalWithdrawalHandler`, `TransferService.submitExternalTransfer` / `confirmExternalTransfer` / `failExternalTransferAndRefund`
- coin_publish: `WithdrawalWorker`, `ConfirmationWorker`, `src/utils/coinServiceClient.js`
