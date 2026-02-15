# 친구 초대·래퍼럴·채굴 관련 DB 구조

REFERRAL_AND_INVITE_SPEC.md 기능 구현에 사용하는 테이블 정리입니다.

---

## 1. 사용자·레벨·경험치

| 테이블 | 용도 |
|--------|------|
| **users** | `referral_code`(추천인 코드), `level`, `exp`(경험치). 초대 1명당 +1 EXP 반영 |

---

## 2. 레퍼럴 관계·통계

| 테이블 | 용도 |
|--------|------|
| **referral_relations** | 추천인(referrer_id) ↔ 피추천인(referred_id), level=1(직접), status=ACTIVE, deleted_at(Soft Delete). 동일 IP/기기 중복 초대 시 무효 처리 대상 |
| **referral_stats_logs** | user_id별 direct_count, team_count, total_reward, today_reward. 래퍼럴 수익 집계용 |

- **유효 직접 초대 수**: referral_relations에서 해당 referrer_id의 referred_id 중, 이메일 인증 완료 + 채굴 기록 1건 이상인 사용자만 카운트 (채굴 보너스 %·수익률 구간 적용 시 사용).

---

## 3. 안전장치용

| 테이블 | 용도 |
|--------|------|
| **email_verifications** | user_id별 이메일, is_verified. “하루 유저” = 이메일 인증 필수 시 사용 |
| **devices** | user_id, device_id, last_ip, device_type, device_os. 동일 IP/동일 기기 중복 초대 무효 판단 시 사용 |

---

## 4. 채굴

| 테이블 | 용도 |
|--------|------|
| **mining_levels** | level별 daily_max_mining. 일일 최대 채굴량(캡) 유지 |
| **daily_mining** | user_id, mining_date, mining_amount. 일별 누적 채굴량 (캡 적용 후 기록) |
| **mining_history** | user_id, level, amount, type(BROADCAST_WATCH 등), status=COMPLETED. 영상 시청 1회당 채굴 기록. “채굴 인정 = 영상 시청 + 채굴 기록 필수” 검증용 |

### 4.1 세션 도중 최대 채굴량 도달 시 저장 규칙

- 정산(settle) 시 `todayMiningAmount + 이번_적립량`이 `dailyMaxMining`을 초과하면, 초과분은 적립하지 않고 `amountToCredit = min(이번_정산_량, dailyMaxMining - todayAmount)`만 반영.
- **amountToCredit = 0인 경우**: `mining_sessions.last_settled_at`만 갱신. 지갑·daily_mining·EXP는 변경 없음.
- **세션 종료 시**(`settleEnd >= session.ends_at`): **채굴내역(mining_history)은 적립량과 관계없이 1건 항상 삽입.** amountToCredit이 0이어도(한도 도달로 추가 적립이 없어도) 세션이 끝나면 `insertMiningHistory` 호출, 레퍼럴 보상·레벨 동기화 수행. `mining_history.amount`에는 해당 세션의 `rate_per_hour`(1시간당 채굴량) 저장.

---

## 5. 래퍼럴 수익 지급

| 테이블 | 용도 |
|--------|------|
| **internal_transfers** | transfer_type=REFERRAL_REWARD, sender_id=NULL(시스템), receiver_id=추천인, amount. 하부 채굴 KORI 기준 수익률(3%~13%) 적용 후 상위에게 지급한 금액 |
| **user_wallets** | KORI 지갑 balance. REFERRAL_REWARD 지급 시 receiver 잔액 증가 |

---

## 6. 요약

| 구분 | 내용 |
|------|------|
| 채굴 보너스 | 초대 인원(유효 직접 수) → 영상 1회당 채굴량 +3%~+22%, 일일 상한은 mining_levels 유지 |
| 래퍼럴 수익 | 하부 채굴 KORI × 수익률 → internal_transfers(REFERRAL_REWARD) + user_wallets 반영 |
| 안전장치 | email_verifications.is_verified, devices(last_ip, device_id) 중복 체크, mining_history 존재 여부로 “채굴 인정” 여부 판단 |
| EXP | 초대 1명당 users.exp +1 (유효 초대 시에만) |

*문서 버전: 1.0 | REFERRAL_AND_INVITE_SPEC 연동*
