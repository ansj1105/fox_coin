# 거래 유형·상태 세분화 점검

## 1. 거래 유형 (TransactionType)

| value | 백엔드 description | 프론트 라벨 | 비고 |
|-------|---------------------|-------------|------|
| WITHDRAW | 출금 | 출금 | ✅ |
| TOKEN_DEPOSIT | 토큰 입금 | 토큰 입금 | ✅ |
| REFERRAL_REWARD | 래퍼럴 수익 | 래퍼럴 수익 | ✅ |
| AIRDROP_TRANSFER | 에어드랍 전송 | 에어드랍 전송 | ✅ |
| PAYMENT_DEPOSIT | 결제 입금 | 에어드랍 | ✅ (요청대로 PAYMENT_DEPOSIT은 에어드랍 라벨 사용) |
| SWAP | 스왑 | 스왑 | ✅ |
| EXCHANGE | 환전 | 환전 | ✅ (필요 시 "포인트 환전"으로 통일 가능) |

## 2. 상태 (출금: 전송중 / 완료 / 실패)

**내부 전송 (InternalTransfer)**  
- PENDING, COMPLETED, FAILED, CANCELLED  
- 출금 시: COMPLETED=완료, FAILED=실패, PENDING=검토중

**외부 전송 (ExternalTransfer)**  
- PENDING, PROCESSING, SUBMITTED, CONFIRMED, FAILED, CANCELLED  
- **세분화 부족**: 프론트에서 출금 상태가 COMPLETED / PENDING / FAILED 만 처리됨  
  - CONFIRMED → 출금완료  
  - PROCESSING, SUBMITTED → 전송중(처리중)  
  - 위 매핑 추가 필요

## 3. 수정 적용 사항 (반영 완료)

- PAYMENT_DEPOSIT: 프론트 라벨 "에어드랍" 유지 (paymentDeposit 없애고 에어드랍이 맞음) ✅  
- 출금 상태: CONFIRMED → 출금완료, PROCESSING/SUBMITTED → 전송중, CANCELLED → 취소됨 매핑 ✅  
- getStatusColor: CONFIRMED=완료(성공색), PROCESSING/SUBMITTED=전송중(primary), CANCELLED=실패/취소(neutral) ✅  
- EXCHANGE: 백엔드 description 및 프론트 기본 라벨 "포인트 환전"으로 통일 ✅
