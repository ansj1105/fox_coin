-- 테스트용 통화 및 지갑 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다

-- 기존 테스트 데이터 삭제
DELETE FROM mining_history WHERE user_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user'));
DELETE FROM internal_transfers;
DELETE FROM external_transfers;
DELETE FROM user_wallets WHERE user_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user'));
DELETE FROM currency WHERE code IN ('FOXYA', 'USDT', 'TRX', 'ETH', 'KRWT', 'BLUEDIA', 'KRC');

-- 테스트용 통화 추가
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES 
    ('FOXYA', 'Foxya Token', 'TRON', true, NOW(), NOW()),
    ('USDT', 'Tether USD', 'TRON', true, NOW(), NOW()),
    ('TRX', 'TRON', 'TRON', true, NOW(), NOW()),
    ('FOXYA', 'Foxya Token', 'INTERNAL', true, NOW(), NOW()),
    ('ETH', 'Ethereum', 'Ether', true, NOW(), NOW()),
    ('USDT', 'Tether USD', 'Ether', true, NOW(), NOW()),
    ('KRWT', 'Korean Won Token', 'INTERNAL', true, NOW(), NOW()),
    ('BLUEDIA', 'Blue Diamond', 'INTERNAL', true, NOW(), NOW()),
    ('KRC', 'Korean Coin', 'INTERNAL', true, NOW(), NOW())
ON CONFLICT (code, chain) DO NOTHING;

-- 시퀀스 리셋
SELECT setval('currency_id_seq', (SELECT COALESCE(MAX(id), 1) FROM currency));

-- 테스트용 지갑 추가
-- testuser (ID:1) 지갑 - 잔액 1000 FOXYA
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    'TADDR_TESTUSER_001',
    1000.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL')
);

-- testuser2 (ID:2) 지갑 - 잔액 500 FOXYA
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser2'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    'TADDR_TESTUSER2_001',
    500.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser2')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL')
);

-- admin_user (ID:3) 지갑 - 잔액 10000 FOXYA
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'admin_user'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    'TADDR_ADMIN_001',
    10000.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'admin_user')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL')
);

-- referrer_user (ID:5) 지갑 - 잔액 2000 FOXYA
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'referrer_user'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    'TADDR_REFERRER_001',
    2000.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'referrer_user')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL')
);

-- no_code_user (ID:6) 지갑 - 잔액 100 FOXYA
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'no_code_user'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    'TADDR_NOCODE_001',
    100.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'no_code_user')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL')
);

-- TRON 체인 지갑 (외부 전송 테스트용)
-- testuser (ID:1) TRON 지갑 - 잔액 500 FOXYA
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'TRON'),
    'TTestUserAddress123456789012345678901',
    500.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'TRON')
);

-- no_code_user (ID:6) TRON 지갑 - 잔액 50 FOXYA (잔액 부족 테스트용)
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'no_code_user'),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'TRON'),
    'TNoCodeUserAddress123456789012345678',
    50.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'no_code_user')
    AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'TRON')
);

-- 환전 테스트용 지갑
-- testuser (ID:1) KRWT 지갑 - 잔액 10000 KRWT
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM currency WHERE code = 'KRWT' AND chain = 'INTERNAL'),
    'TADDR_TESTUSER_KRWT_001',
    10000.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND currency_id = (SELECT id FROM currency WHERE code = 'KRWT' AND chain = 'INTERNAL')
);

-- testuser (ID:1) BLUEDIA 지갑 - 잔액 0 BLUEDIA (환전 후 받을 지갑)
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM currency WHERE code = 'BLUEDIA' AND chain = 'INTERNAL'),
    'TADDR_TESTUSER_BLUEDIA_001',
    0.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND currency_id = (SELECT id FROM currency WHERE code = 'BLUEDIA' AND chain = 'INTERNAL')
);

-- 스왑 테스트용 지갑
-- testuser (ID:1) ETH 지갑 (Ether 체인) - 잔액 10 ETH
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM currency WHERE code = 'ETH' AND chain = 'Ether'),
    '0xTestUserEthAddress1234567890123456789012345678',
    10.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND currency_id = (SELECT id FROM currency WHERE code = 'ETH' AND chain = 'Ether')
);

-- testuser (ID:1) USDT 지갑 (Ether 체인) - 잔액 0 USDT (스왑 후 받을 지갑)
INSERT INTO user_wallets (user_id, currency_id, address, balance, locked_balance, status, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM currency WHERE code = 'USDT' AND chain = 'Ether'),
    '0xTestUserUsdtAddress123456789012345678901234567',
    0.000000000000000000,
    0.000000000000000000,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND currency_id = (SELECT id FROM currency WHERE code = 'USDT' AND chain = 'Ether')
);

-- 시퀀스 리셋
SELECT setval('user_wallets_id_seq', (SELECT COALESCE(MAX(id), 1) FROM user_wallets));

-- 테스트용 채굴 내역 데이터
-- testuser (ID:1)의 채굴 내역
INSERT INTO mining_history (user_id, level, amount, type, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    0.00012345,
    'BROADCAST_PROGRESS',
    'COMPLETED',
    NOW() - INTERVAL '1 hour'
WHERE NOT EXISTS (
    SELECT 1 FROM mining_history 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND type = 'BROADCAST_PROGRESS'
    AND created_at = NOW() - INTERVAL '1 hour'
);

INSERT INTO mining_history (user_id, level, amount, type, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    0.00012345,
    'BROADCAST_WATCH',
    'COMPLETED',
    NOW() - INTERVAL '2 hours'
WHERE NOT EXISTS (
    SELECT 1 FROM mining_history 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND type = 'BROADCAST_WATCH'
    AND created_at = NOW() - INTERVAL '2 hours'
);

-- 오늘 채굴 내역 추가
INSERT INTO mining_history (user_id, level, amount, type, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    0.00023456,
    'BROADCAST_PROGRESS',
    'COMPLETED',
    NOW() - INTERVAL '30 minutes'
WHERE NOT EXISTS (
    SELECT 1 FROM mining_history 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND type = 'BROADCAST_PROGRESS'
    AND created_at = NOW() - INTERVAL '30 minutes'
);

-- 일주일 전 채굴 내역
INSERT INTO mining_history (user_id, level, amount, type, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    0.00034567,
    'BROADCAST_WATCH',
    'COMPLETED',
    NOW() - INTERVAL '7 days'
WHERE NOT EXISTS (
    SELECT 1 FROM mining_history 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND type = 'BROADCAST_WATCH'
    AND created_at = NOW() - INTERVAL '7 days'
);

-- 한 달 전 채굴 내역
INSERT INTO mining_history (user_id, level, amount, type, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    0.00045678,
    'BROADCAST_PROGRESS',
    'COMPLETED',
    NOW() - INTERVAL '30 days'
WHERE NOT EXISTS (
    SELECT 1 FROM mining_history 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND type = 'BROADCAST_PROGRESS'
    AND created_at = NOW() - INTERVAL '30 days'
);

-- 시퀀스 리셋
SELECT setval('mining_history_id_seq', (SELECT COALESCE(MAX(id), 1) FROM mining_history));

-- 테스트용 일일 채굴량 데이터 (개인 랭킹 테스트용)
-- testuser (ID:1)의 오늘 채굴량
INSERT INTO daily_mining (user_id, mining_date, mining_amount, reset_at, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    CURRENT_DATE,
    1000.12345,
    (CURRENT_DATE + INTERVAL '1 day')::timestamp,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM daily_mining 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND mining_date = CURRENT_DATE
);

-- testuser2 (ID:2)의 오늘 채굴량
INSERT INTO daily_mining (user_id, mining_date, mining_amount, reset_at, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser2'),
    CURRENT_DATE,
    500.54321,
    (CURRENT_DATE + INTERVAL '1 day')::timestamp,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM daily_mining 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser2')
    AND mining_date = CURRENT_DATE
);

-- referrer_user (ID:5)의 오늘 채굴량
INSERT INTO daily_mining (user_id, mining_date, mining_amount, reset_at, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'referrer_user'),
    CURRENT_DATE,
    2000.98765,
    (CURRENT_DATE + INTERVAL '1 day')::timestamp,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM daily_mining 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'referrer_user')
    AND mining_date = CURRENT_DATE
);

-- 테스트용 레퍼럴 수익 데이터 (internal_transfers)
-- testuser (ID:1)이 받은 레퍼럴 수익
-- 참고: sender_id는 NOT NULL이므로 receiver_id를 sender_id로도 사용 (시스템 자동 지급)
INSERT INTO internal_transfers (transfer_id, sender_id, sender_wallet_id, receiver_id, receiver_wallet_id, currency_id, amount, fee, status, transfer_type, created_at, completed_at)
SELECT 
    gen_random_uuid()::text,
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser') AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL') LIMIT 1),
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser') AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL') LIMIT 1),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    500.12345,
    0,
    'COMPLETED',
    'REFERRAL_REWARD',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM internal_transfers 
    WHERE receiver_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND transfer_type = 'REFERRAL_REWARD'
    AND created_at::date = (NOW() - INTERVAL '1 day')::date
);

-- testuser2 (ID:2)이 받은 레퍼럴 수익
INSERT INTO internal_transfers (transfer_id, sender_id, sender_wallet_id, receiver_id, receiver_wallet_id, currency_id, amount, fee, status, transfer_type, created_at, completed_at)
SELECT 
    gen_random_uuid()::text,
    (SELECT id FROM users WHERE login_id = 'testuser2'),
    (SELECT id FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser2') AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL') LIMIT 1),
    (SELECT id FROM users WHERE login_id = 'testuser2'),
    (SELECT id FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser2') AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL') LIMIT 1),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    300.54321,
    0,
    'COMPLETED',
    'REFERRAL_REWARD',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days'
WHERE NOT EXISTS (
    SELECT 1 FROM internal_transfers 
    WHERE receiver_id = (SELECT id FROM users WHERE login_id = 'testuser2')
    AND transfer_type = 'REFERRAL_REWARD'
    AND created_at::date = (NOW() - INTERVAL '2 days')::date
);

-- referrer_user (ID:5)이 받은 레퍼럴 수익
INSERT INTO internal_transfers (transfer_id, sender_id, sender_wallet_id, receiver_id, receiver_wallet_id, currency_id, amount, fee, status, transfer_type, created_at, completed_at)
SELECT 
    gen_random_uuid()::text,
    (SELECT id FROM users WHERE login_id = 'referrer_user'),
    (SELECT id FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE login_id = 'referrer_user') AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL') LIMIT 1),
    (SELECT id FROM users WHERE login_id = 'referrer_user'),
    (SELECT id FROM user_wallets WHERE user_id = (SELECT id FROM users WHERE login_id = 'referrer_user') AND currency_id = (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL') LIMIT 1),
    (SELECT id FROM currency WHERE code = 'FOXYA' AND chain = 'INTERNAL'),
    1000.98765,
    0,
    'COMPLETED',
    'REFERRAL_REWARD',
    NOW() - INTERVAL '3 days',
    NOW() - INTERVAL '3 days'
WHERE NOT EXISTS (
    SELECT 1 FROM internal_transfers 
    WHERE receiver_id = (SELECT id FROM users WHERE login_id = 'referrer_user')
    AND transfer_type = 'REFERRAL_REWARD'
    AND created_at::date = (NOW() - INTERVAL '3 days')::date
);

-- 테스트용 레퍼럴 관계 데이터 (팀원 수 테스트용)
-- testuser (ID:1)이 referrer_user (ID:5)를 추천한 경우 (역방향, 테스트용)
INSERT INTO referral_relations (referrer_id, referred_id, level, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    (SELECT id FROM users WHERE login_id = 'referrer_user'),
    1,
    'ACTIVE',
    NOW() - INTERVAL '10 days'
WHERE NOT EXISTS (
    SELECT 1 FROM referral_relations 
    WHERE referrer_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND referred_id = (SELECT id FROM users WHERE login_id = 'referrer_user')
);

-- testuser2 (ID:2)이 testuser (ID:1)를 추천한 경우
INSERT INTO referral_relations (referrer_id, referred_id, level, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser2'),
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    'ACTIVE',
    NOW() - INTERVAL '5 days'
WHERE NOT EXISTS (
    SELECT 1 FROM referral_relations 
    WHERE referrer_id = (SELECT id FROM users WHERE login_id = 'testuser2')
    AND referred_id = (SELECT id FROM users WHERE login_id = 'testuser')
);

