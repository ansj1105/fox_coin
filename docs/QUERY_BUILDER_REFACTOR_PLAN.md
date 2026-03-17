# QueryBuilder Refactor Plan

## 목적

- `Repository` 계층의 쿼리 작성 방식을 `QueryBuilder`와 `Op` 중심으로 통일한다.
- raw SQL 문자열, raw condition, 직접 비교식이 섞여 있는 부분을 단계적으로 줄인다.
- `WHERE`, `AND`, `OR` 비교식은 가능한 한 `Op`와 빌더 메서드로 통일한다.
- 빌더가 표현하지 못하는 안전한 패턴은 먼저 빌더를 확장한 뒤 적용한다.

## 선행 완료 항목

- 시드 구문 로그인 운영 장애 해결
- ETH 시드 로그인 주소 mismatch fallback 반영
- 삭제된 사용자/지갑도 시드 소유 증명 시 복구 로그인 가능하도록 백엔드 반영
- 운영 검증 완료
  - `recovery/challenge` 200 확인
  - `login-with-seed` 200 확인
  - mnemonic: `disease airport interest spoon convince symbol cook black pause venue toe shove`

## 리팩토링 원칙

- `WHERE column = value` 형태는 `where(column, Op.Equal, mapping)` 사용
- `IS NULL`, `IS NOT NULL`은 `Op.IsNull`, `Op.IsNotNull` 사용
- 범용 비교 연산은 `Op.GreaterThan`, `Op.LessThan`, `Op.GreaterThanOrEqual`, `Op.LessThanOrEqual` 우선
- `LOWER(column) = LOWER(value)` 같은 패턴은 빌더 raw condition을 남기지 말고 전용 helper 후보로 관리
- `SET` 절의 누적 갱신, `NULL` 복원 등은 현재 `setCustom(...)` 유지 가능
- 단, 반복 패턴이 누적되면 `UpdateQueryBuilder` helper 확장 검토

## 범위 분류

### 1차 우선순위

- `Auth`
- `Wallet`
- `Transfer`
- `User`
- `VirtualWalletMapping`

이유:

- 로그인/회원/지갑 조회와 직접 연결됨
- 운영 장애 재발 방지 효과가 큼
- raw condition과 deleted 처리 규칙이 섞여 있음

### 2차 우선순위

- `Mining`
- `Referral`
- `Ranking`
- `Exchange`
- `Swap`
- `Deposit`
- `Payment`

이유:

- 계산식, 집계식, 누적 갱신이 많아 빌더 확장 포인트가 잘 드러남

### 3차 우선순위

- `Banner`
- `Notice`
- `Notification`
- `Review`
- `Inquiry`
- `Agency`
- `Client`
- `CountryCode`
- `AppConfig`

이유:

- 상대적으로 운영 리스크가 낮고 조회 패턴이 단순함

## 1차 확인된 정리 후보

- [src/main/java/com/foxya/coin/transfer/TransferRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/transfer/TransferRepository.java)
  - `where("LOWER(address) = LOWER(#{address})")`
  - 진행: `whereLowerEqual(...)`로 치환 완료
- [src/main/java/com/foxya/coin/wallet/WalletRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/wallet/WalletRepository.java)
  - `LOWER(uw.address) = LOWER(#{address})`
  - 진행: builder + `whereLowerEqual(...)`로 치환 완료
- [src/main/java/com/foxya/coin/ranking/RankingRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/ranking/RankingRepository.java)
  - `where("(mining_amount + referral_reward) > 0")`
- [src/main/java/com/foxya/coin/referral/ReferralRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/referral/ReferralRepository.java)
  - `where("(referrer_id = #{user_id} OR referred_id = #{user_id})")`
  - 진행: `whereOrEquals(...)`로 치환 완료
- [src/main/java/com/foxya/coin/user/UserRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/user/UserRepository.java)
  - `setCustom("deleted_at = NULL")`
  - 진행: `setNull(...)`로 치환 완료
- [src/main/java/com/foxya/coin/wallet/WalletRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/wallet/WalletRepository.java)
  - `setCustom("deleted_at = NULL")`
  - 진행: `setNull(...)`로 치환 완료
- [src/main/java/com/foxya/coin/exchange/ExchangeRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/exchange/ExchangeRepository.java)
  - `.build() + " ORDER BY id DESC LIMIT 1"`
  - 진행: `orderBy(...).limit(1)`로 치환 완료
- [src/main/java/com/foxya/coin/user/UserExternalIdRepository.java](/Users/anseojeong/IdeaProjects/fox_coin/src/main/java/com/foxya/coin/user/UserExternalIdRepository.java)
  - raw select/upsert SQL
  - 진행: QueryBuilder로 전환 완료

## 빌더 확장 후보

- case-insensitive equality helper
  - 예: `andWhereLowerEqual("address", "address")`
  - 진행: `Transfer`, `Wallet`에 우선 적용
- grouped OR equality helper 확장
  - 현재 `whereOrEquals`는 2개 컬럼만 다룸
  - 진행: `ReferralRepository` 1건 적용 완료
- arithmetic compare helper
  - 예: expression 기반 `whereExpr(...)`
  - 판단: `RankingRepository` 단독 용도로는 확장 이득이 작아 보류
- nullable set helper
  - 예: `setNull("deleted_at")`
  - 진행: `User`, `Wallet` 복구 쿼리에 우선 적용
- update helper
  - 예: `set(...)`, `setCurrentTimestamp(...)`
  - 진행: 반복 `updated_at`, `CURRENT_TIMESTAMP` 패턴에 우선 적용
- arithmetic update helper
  - 예: `increaseByParam(...)`, `decreaseByParam(...)`, `increaseByLiteral(...)`
  - 진행: `User/Referral/Mining/Transfer` 누적 갱신에 우선 적용

## 실행 순서

1. 1차 우선순위 repository inventory 정리
2. raw condition 유형 분류
3. 빌더 확장이 필요한 패턴과 바로 치환 가능한 패턴 분리
4. 작은 단위 커밋으로 도메인별 적용
5. 각 단계마다 compile + 해당 도메인 테스트 확인

## 검증 기준

- `compileJava` 통과
- 변경 도메인 targeted test 통과
- raw condition 제거 여부 확인
- 동작 회귀 없는지 운영 민감 경로 우선 점검
