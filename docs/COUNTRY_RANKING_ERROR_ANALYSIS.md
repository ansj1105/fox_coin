# /country-ranking 페이지 오류 원인 분석 (백엔드)

프론트에서 "오류가 발생했습니다"가 나오는 경우, 백엔드 기준으로 가능한 원인을 정리한 문서입니다.

---

## 1. API 경로 및 인증

- **경로**: `GET /api/v1/ranking/country` (Query: `period` 선택, 기본 TODAY)
- **인증**: **JWT 필수** (`JWTAuthHandler`), 역할 **USER 또는 ADMIN** (`AuthUtils.hasRole(USER, ADMIN)`)

### 1.1 인증/권한 실패 (가장 흔한 원인)

| 조건 | HTTP 상태 | 프론트 결과 |
|------|-----------|-------------|
| JWT 없음 / 만료 / 잘못됨 | 401 Unauthorized | 요청 실패 → `countryRankingData` 없음 → **오류 메시지** |
| USER, ADMIN 아님 (역할 불일치) | 403 Forbidden | 동일 |

**확인 방법**: 요청 헤더에 `Authorization: Bearer <accessToken>` 이 있고, 토큰이 만료되지 않았는지 확인.

---

## 2. 서버 내부 실패 (Future 실패)

`RankingHandler.getCountryRankings` → `rankingService.getCountryRankings(userId, period)` 를 호출하고, `BaseHandler.response(ctx, future)` 는 **Future 실패 시 `ctx.fail(throwable)`** 를 호출합니다.  
이때 Vert.x/API 레이어에서 보통 **500** 이 나가고, 프론트는 응답을 실패로 처리해 **오류 메시지**를 냅니다.

### 2.1 가능한 실패 지점

1. **`rankingRepository.getCountryRankings(pool, finalPeriod)`**
   - DB 연결 실패
   - SQL 실행 예외 (문법 오류, placeholder 오류, 타입 불일치 등)
   - `referral_relations`, `daily_mining`, `internal_transfers`, `users` 테이블/컬럼 없음 또는 스키마 불일치

2. **`rankingRepository.getUserCountryCode(pool, userId)`**
   - 동일하게 DB/SQL 예외
   - `users` 테이블에 `id`, `country_code` 등 없음

3. **`RankingService` 내부**
   - `getCountryRankings` 의 `compose`/`map` 체인 안에서 NPE 등 예외 발생 시 Future 실패
   - 현재 코드상 `getCountryName`/`getCountryFlag` 는 `CountryCode.fromCode()` 로 미지 코드 시 `UNKNOWN` 반환 → 예외 없음

### 2.2 국가 코드 (CountryCode enum)

- 랭킹/사용자 국가 코드가 **CountryCode 에 없으면** `fromCode()` 가 **UNKNOWN**("알 수 없음", 🏳️) 을 반환합니다.
- **NG(나이지리아)** 는 enum 에 없으면 UNKNOWN 처리되며, **예외는 나지 않습니다**. (이름만 "알 수 없음"으로 나옴)
- enum 에 NG 를 추가하면 랭킹/프로필에서 "나이지리아"로 표시됩니다.

---

## 3. 정상 응답이어도 프론트에서 오류로 보는 경우

- 백엔드가 **200 + body `{ "status": "FAIL", "message": "..." }`** 를 보내는 경우  
  → 프론트는 `response.status === 'OK'` 가 아니면 throw 하므로 **오류 메시지** 표시.
- 현재 `RankingHandler` 는 성공 시에만 `success(ctx, dto)` 를 호출하고, 실패 시에는 `ctx.fail()` 이므로 **FAIL body 가 나가는 경로는 일반적이지 않음**. (다만 공통 에러 핸들러에서 FAIL 로 감싸서 보낼 수는 있음)

---

## 4. 점검 체크리스트

1. **인증**
   - [ ] `/country-ranking` 접속 시 로그인 상태인지
   - [ ] accessToken 이 헤더에 포함되는지
   - [ ] 토큰 만료 여부 (재로그인 후 재시도)

2. **백엔드 로그**
   - [ ] `Getting country rankings for user: {userId}, period: {period}` 로그가 찍히는지 (핸들러 진입 여부)
   - [ ] `국가별 랭킹 조회 실패:` 로그 (RankingRepository)
   - [ ] `사용자 국가 랭킹 조회 실패:` 로그 (getCountryRankingByCode 실패 시, 단 이 경우는 .otherwise 로 잡아서 전체는 성공 응답)

3. **DB**
   - [ ] `users`, `referral_relations`, `daily_mining`, `internal_transfers` 테이블 존재 및 스키마
   - [ ] `users.country_code`, `users.status` 등 쿼리에서 사용하는 컬럼 존재

4. **네트워크**
   - [ ] 브라우저 개발자도구 Network 탭에서 `/api/v1/ranking/country` 요청의 상태 코드(401/403/500 등) 및 응답 body

---

## 5. 요약

| 원인 | 대응 |
|------|------|
| **401/403 (인증·권한)** | 로그인 유지, 토큰 갱신, 역할 확인 |
| **500 (DB/SQL/서버 예외)** | 백엔드 로그·스택트레이스 확인, DB/스키마/쿼리 점검 |
| **200 + status !== 'OK'** | 공통 에러 핸들러 및 응답 포맷 확인 |

프론트는 **`countryRankingData` 가 없을 때** "오류가 발생했습니다" 를 표시하므로, 위 조건 중 하나라도 만족하면 해당 메시지가 나옵니다.
