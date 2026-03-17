# QueryBuilder Refactor Worklog

## 완료

### 2026-03-18

- 시드 구문 로그인 운영 장애 원인 확인
  - ETH 주소 mismatch 문제만이 아니라 삭제된 사용자/지갑 데이터도 복구 로그인 대상에 포함되어야 했음
- 운영 반영 완료
  - 커밋: `7705a82`
  - 내용: 삭제된 사용자/지갑도 시드 로그인 시 복구
- 운영 검증 완료
  - `POST /api/v1/auth/recovery/challenge` for `0x401AFd17f590a7740aC8F20eAEff620701537136` => `200`
  - `POST /api/v1/auth/login-with-seed` => `200`
  - 반환 `userId=23`, `loginId=dianainteen@gmail.com`

## 진행 중

- QueryBuilder / Op 통일 범위 정리
- repository raw condition inventory 작성
- 1차 정리 반영
  - `WalletRepository` address lookup을 builder + `whereLowerEqual(...)`로 전환
  - `WalletRepository` restore query를 `setNull(...)`로 전환
  - `WalletRepository` user wallet / managed wallet / deposit watch 조회를 builder 중심으로 정리
  - `ReferralRepository` soft delete OR 조건을 `whereOrEquals(...)`로 전환
  - `ReferralRepository` 최신 1건 조회를 `orderBy(...).limit(1)`로 전환
  - `ReferralRepository` join 후 raw `AND d.deleted_at IS NULL`를 builder `and(..., Op.IsNull)`로 전환
  - `ExchangeRepository` latest setting 조회를 `orderBy(...).limit(1)`로 전환
  - `UserExternalIdRepository` raw SQL을 QueryBuilder로 전환
  - `UpdateQueryBuilder`에 `set(...)`, `increaseByParam(...)`, `decreaseByParam(...)` 추가
  - `User/Referral/Mining/Transfer`의 반복 누적 갱신과 단순 대입을 helper로 치환
  - `UpdateQueryBuilder`에 `setCurrentTimestamp(...)`, `increaseByLiteral(...)` 추가
  - `Auth/Transfer/Wallet`의 `CURRENT_TIMESTAMP`, `+ 1`, restore `updated_at` 패턴을 helper로 치환
  - `EmailVerificationRepository`의 boolean literal update를 파라미터 기반 `set(...)`로 치환
  - `BaseQueryBuilder`에 `andWhereTrimNotEmpty(...)` 추가
  - `WalletRepository` managed wallet 조회의 `TRIM(private_key) <> ''`를 helper로 치환

## 다음 작업

- `Auth/Wallet/Transfer/User` repository 1차 정리
- `WalletRepository` managed wallet / deposit watch address 조회의 builder 전환 검토
- `RankingRepository` expression where용 helper 필요 여부 결정
- `ReferralRepository` 복잡한 grouped OR / EXISTS 쿼리 범위 분리
- arithmetic expression condition 패턴 분류

## 미작업

- `RankingRepository` expression where 정리
- `ReferralRepository` 복잡한 grouped OR raw condition 정리
- `RankingRepository` CTE + arithmetic condition은 raw CTE island로 유지
- `MiningRepository` / `ReferralRepository` / `TransferRepository`의 `setCustom(...)` 사용처 분류
- `WalletRepository`의 `IN`, `TRIM(...)`, `(c.chain IS NULL OR ...)`는 적절한 공통 helper가 생기기 전까지 raw predicate로 유지
- `EmailVerificationRepository`의 `expires_at >= CURRENT_TIMESTAMP`는 시간 비교 helper 없이 raw predicate로 유지
- 도메인별 repository inventory 문서화
- 빌더 확장 필요 여부 결정 문서화

## 비고

- 전체 `AuthHandlerTest`에는 기존 `LegacyTronSeedLoginRecoveryTest` 1건 실패가 여전히 있음
- 단일 테스트 재실행 시 로컬 `8089` 포트 충돌이 발생할 수 있어 재실행 전 포트 상태 확인 필요
