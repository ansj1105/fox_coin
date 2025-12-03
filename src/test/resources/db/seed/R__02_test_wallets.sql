-- 테스트용 통화 및 지갑 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다

-- 기존 테스트 데이터 삭제
DELETE FROM internal_transfers;
DELETE FROM external_transfers;
DELETE FROM user_wallets WHERE user_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user'));
DELETE FROM currency WHERE code IN ('FOXYA', 'USDT', 'TRX');

-- 테스트용 통화 추가
INSERT INTO currency (code, name, chain, is_active, created_at, updated_at)
VALUES 
    ('FOXYA', 'Foxya Token', 'TRON', true, NOW(), NOW()),
    ('USDT', 'Tether USD', 'TRON', true, NOW(), NOW()),
    ('TRX', 'TRON', 'TRON', true, NOW(), NOW()),
    ('FOXYA', 'Foxya Token', 'INTERNAL', true, NOW(), NOW())
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

-- 시퀀스 리셋
SELECT setval('user_wallets_id_seq', (SELECT COALESCE(MAX(id), 1) FROM user_wallets));

