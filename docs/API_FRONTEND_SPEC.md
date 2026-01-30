# Foxya Coin API - 프론트엔드 연동 스펙

프론트엔드에서 API 변경사항을 반영하고, AI가 타입/API 클라이언트/훅을 생성할 수 있도록 정리한 스펙 문서입니다.

---

## 1. 공통 사항

### 1.1 Base URL
- 개발: `{API_BASE}/api/v1` (예: `https://api.example.com/api/v1`)
- 모든 경로는 위 prefix 기준 상대 경로로 기술합니다.

### 1.2 인증
- **Bearer JWT**: `Authorization: Bearer {accessToken}`
- 인증이 필요한 API는 요청 시 위 헤더를 반드시 포함합니다.

### 1.3 공통 응답 래퍼
모든 성공/실패 응답은 아래 구조로 감싸집니다.

```ts
// 성공
interface ApiResponse<T> {
  status: 'OK';
  message: string;
  data: T;
}

// 에러 (4xx/5xx)
interface ApiError {
  status: 'ERROR';
  message: string;
  code?: number;
}
```

### 1.4 Content-Type
- 요청: `Content-Type: application/json`
- 응답: `Content-Type: application/json`

---

## 2. 레퍼럴 (Referral)

### 2.1 레퍼럴 코드 등록
**POST** `/referrals/register`

- **설명**: 로그인한 유저가 다른 유저의 추천인 코드를 입력해 피추천인이 됩니다.  
  중복 추천 방지를 위해 **선택적으로** 아래 헤더를 보내면, 동일 추천인 하에서 같은 IP/기기로 이미 등록된 피추천인이 있는지 검사합니다.

- **인증**: Bearer JWT 필수

- **요청 헤더 (선택)**
  | 헤더 | 타입 | 설명 |
  |------|------|------|
  | `X-Forwarded-For` | string | 클라이언트 IP (프록시 경유 시). 콤마 구분 시 첫 번째 값 사용 |
  | `X-Real-IP` | string | 클라이언트 IP (대안) |
  | `X-Device-Id` | string | 기기 식별자 (중복 추천 판단용). 가능하면 항상 전달 권장 |

- **요청 Body**
```ts
interface RegisterReferralRequest {
  referralCode: string;  // 6~20자
}
```

- **응답 200**
```ts
interface RegisterReferralData {
  referrerId: number;
  referredId: number;
  status: string;
}
// ApiResponse<RegisterReferralData>
```

- **에러**
  - `400`: 이미 등록됨 / 유효하지 않은 코드 / 자신의 코드 / 중복 IP·기기 감지
  - `401`: 인증 실패

---

### 2.2 현재 추천인 코드 조회
**GET** `/referrals/current`

- **설명**: 나를 추천한 사용자의 추천인 코드를 조회. 추천인이 없으면 `referralCode`는 `null`.

- **응답 200**
```ts
interface CurrentReferralCodeData {
  referralCode: string | null;
}
// ApiResponse<CurrentReferralCodeData>
```

---

### 2.3 레퍼럴 관계 삭제 (Soft Delete)
**DELETE** `/referrals/`

- **설명**: 현재 로그인 사용자의 추천인 관계를 Soft Delete.
- **응답 200**: `ApiResponse<null>` 또는 data 없음
- **에러**: `400` 등록된 레퍼럴 관계 없음, `401` 인증 실패

---

### 2.4 레퍼럴 관계 완전 삭제 (Hard Delete)
**DELETE** `/referrals/hard`

- **설명**: DB에서 완전 삭제. 복구 불가.
- **응답 200**: `ApiResponse<null>`
- **에러**: `400` 레퍼럴 관계 없음, `401` 인증 실패

---

### 2.5 레퍼럴 통계 조회
**GET** `/referrals/{id}/stats`

- **Path**: `id` — 사용자 ID (number)
- **응답 200**
```ts
interface ReferralStatsData {
  userId: number;
  directCount: number;      // 직접 추천한 총 인원
  activeTeamCount: number;  // ACTIVE 팀원 수
  totalReward: number;      // 총 리워드
  todayReward: number;      // 오늘 리워드
}
// ApiResponse<ReferralStatsData>
```

---

### 2.6 팀 정보 조회
**GET** `/referrals/team`

- **Query**
  | 이름 | 타입 | 기본값 | 설명 |
  |------|------|--------|------|
  | `tab` | `'MEMBERS' \| 'REVENUE'` | `'MEMBERS'` | MEMBERS: 멤버 목록, REVENUE: 수익 목록 |
  | `period` | `'ALL' \| 'TODAY' \| 'WEEK' \| 'MONTH' \| 'YEAR'` | `'TODAY'` | 기간 필터 |
  | `limit` | number | 20 | 조회 개수 |
  | `offset` | number | 0 | 시작 위치 |

- **응답 200**
```ts
interface TeamSummaryInfo {
  totalRevenue: number;   // 팀의 총 래퍼럴 수익
  todayRevenue: number;
  weekRevenue: number;
  monthRevenue: number;
  yearRevenue: number;
  periodRevenue?: number;
  totalMembers: number;
  newMembersToday: number;
}

interface TeamMemberInfo {
  userId: number;
  level: number;
  nickname: string;
  registeredAt: string;  // ISO 8601
}

interface TeamRevenueInfo {
  userId: number;
  level: number;
  nickname: string;
  date: string | null;   // ISO 8601
  todayRevenue: number;
  totalRevenue: number;
}

interface TeamInfoData {
  summary: TeamSummaryInfo;
  members: TeamMemberInfo[] | null;   // tab=MEMBERS일 때
  revenues: TeamRevenueInfo[] | null; // tab=REVENUE일 때
  total: number;
  limit: number;
  offset: number;
}
// ApiResponse<TeamInfoData>
```

---

## 3. 채굴 (Mining)

### 3.1 일일 최대 채굴량 조회
**GET** `/mining/daily-limit`

- **응답 200**
```ts
interface DailyLimitData {
  currentLevel: number;
  dailyMaxMining: number;
  todayMiningAmount: number;
  resetAt: string;       // 다음 리셋 시각
  isLimitReached: boolean;
}
// ApiResponse<DailyLimitData>
```

---

### 3.2 레벨별 일일 최대 채굴량 정보
**GET** `/mining/level-info`

- **응답 200**
```ts
interface LevelInfoItem {
  level: number;
  dailyMaxMining: number;
  dailyMaxVideos?: number;
}
interface LevelInfoData {
  levels: LevelInfoItem[];
}
// ApiResponse<LevelInfoData>
```

---

### 3.3 채굴 정보 조회
**GET** `/mining/info`

- **설명**: 오늘 채굴량, 잔액, 채굴효율, 남은 시간, **초대 보너스 배율**, **유효 직접 초대 수** 등.

- **응답 200**
```ts
interface MiningInfoData {
  todayMiningAmount: number;
  totalBalance: number;
  bonusEfficiency: number;        // 채굴효율 (%)
  remainingTime: string;         // "HH:MM:SS"
  isActive: boolean;
  dailyMaxMining: number;
  currentLevel: number;
  nextLevelRequired: number;     // 다음 레벨까지 필요 EXP
  adWatchCount: number;
  maxAdWatchCount: number;
  // 아래 두 필드는 초대 보너스·래퍼럴 구간 표시용 (API 변경 반영)
  inviteBonusMultiplier: number;   // 1.0 ~ 1.22, 영상 1회당 채굴량에 곱함
  validDirectReferralCount: number; // 유효 직접 초대 수 (이메일 인증+채굴 기록 있는 referred)
}
// ApiResponse<MiningInfoData>
```

---

### 3.4 광고 시청
**POST** `/mining/watch-ad`

- **설명**: 광고 시청 후 채굴효율 증가. Body 없음.
- **응답 200**: `ApiResponse<null>` 또는 data 없음
- **에러**: `400` 일일 최대 광고 시청 횟수 초과, `401` 인증 실패

---

### 3.5 영상 시청 1회 채굴 적립 (신규)
**POST** `/mining/credit-video` (전체 경로: **POST** `/api/v1/mining/credit-video` — 404 발생 시 Nginx `location /api/` 프록시 및 백엔드 `MiningHandler` 라우트 등록 확인)

- **설명**: 부스터 영상 시청 1회당 채굴을 적립합니다.  
  **선행 조건**: 이메일 인증 완료, 부스터 영상 1회 이상 시청(watch-ad 완료).  
  일일 영상 시청 횟수·일일 최대 채굴량 제한이 적용되며, 초대 보너스 및 래퍼럴 수익이 연동됩니다.

- **요청**: Body 없음. 인증만 필요.

- **응답 200**
```ts
interface CreditVideoData {
  amount: number;    // 이번에 적립된 채굴량 (0이면 미적립)
  credited: boolean; // 실제 적립 여부 (true: 적립됨, false: 조건 미충족 또는 한도 도달)
}
// ApiResponse<CreditVideoData>
```

- **에러**
  - `400`: 조건 미충족(이메일 미인증, 부스터 영상 미시청) 또는 일일 한도 도달
  - `401`: 인증 실패

- **프론트 사용 예**
  - 부스터 영상 시청 완료 시 이 API를 1회 호출.
  - `data.credited === true`이면 적립 성공, 토스트/잔액 갱신 등 처리.
  - `data.credited === false`이면 조건 안내(이메일 인증, 부스터 영상 선시청, 한도 도달 등).

---

### 3.6 채굴 내역 조회
**GET** `/mining/history`

- **Query**
  | 이름 | 타입 | 기본값 | 설명 |
  |------|------|--------|------|
  | `period` | `'ALL' \| 'TODAY' \| 'WEEK' \| 'MONTH' \| 'YEAR'` | `'TODAY'` | 기간 |
  | `limit` | number | 20 | 조회 개수 |
  | `offset` | number | 0 | 시작 위치 |

- **응답 200**
```ts
interface MiningHistoryItem {
  id: number;
  level: number;
  nickname: string;
  amount: number;
  type: string;   // e.g. BROADCAST_PROGRESS, BROADCAST_WATCH
  status: string;
  createdAt: string;  // ISO 8601
}
interface MiningHistoryData {
  items: MiningHistoryItem[];
  total: number;
  totalAmount: number;
  limit: number;
  offset: number;
}
// ApiResponse<MiningHistoryData>
```

---

## 4. 변경·추가 요약 (프론트 반영 포인트)

| 구분 | 항목 | 반영 내용 |
|------|------|------------|
| **레퍼럴** | `POST /referrals/register` | 요청 시 선택 헤더 `X-Forwarded-For`, `X-Real-IP`, `X-Device-Id` 전달 가능. 가능하면 `X-Device-Id` 항상 전달 권장. |
| **채굴** | `GET /mining/info` | 응답에 `inviteBonusMultiplier`, `validDirectReferralCount` 필드 추가. |
| **채굴** | `POST /mining/credit-video` | **신규 API**. 부스터 영상 시청 완료 시 1회 호출. 응답 `{ amount, credited }`로 적립 여부 처리. |

---

## 5. AI 코드 생성 시 참고

- 위 타입 정의는 TypeScript 기준입니다. `ApiResponse<T>`, `ApiError`를 공통 타입으로 두고 각 API별 `T`만 정의하면 됩니다.
- 인증: 모든 보호된 API에 `Authorization: Bearer {accessToken}` 헤더를 붙입니다.
- 레퍼럴 등록 시: 동일 기기/IP 중복 방지를 위해 `X-Device-Id`(및 필요 시 `X-Forwarded-For`)를 설정하는 것을 권장합니다.
- 채굴 플로우: `GET /mining/info`로 상태 표시 → (부스터 영상 재생) → `POST /mining/credit-video` 호출 → `data.credited`로 성공 여부 분기 후 UI/잔액 갱신.

이 스펙을 기준으로 프론트엔드 API 클라이언트, 타입, React Query/SWR 훅 등을 생성할 수 있습니다.
