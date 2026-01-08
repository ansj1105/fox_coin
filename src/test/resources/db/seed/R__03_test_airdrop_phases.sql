-- 테스트용 에어드랍 Phase 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다

-- 기존 테스트 데이터 삭제
DELETE FROM airdrop_transfers WHERE user_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user'));
DELETE FROM airdrop_phases WHERE user_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user'));

-- 테스트용 에어드랍 Phase 추가
INSERT INTO airdrop_phases (user_id, phase, status, amount, unlock_date, days_remaining, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    1,
    'RELEASED',
    20000.0,
    NOW() - INTERVAL '10 days',  -- 이미 해제됨
    NULL,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM airdrop_phases 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND phase = 1
);

INSERT INTO airdrop_phases (user_id, phase, status, amount, unlock_date, days_remaining, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    2,
    'PROCESSING',
    20000.0,
    NOW() + INTERVAL '30 days',  -- 30일 후 해제
    30,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM airdrop_phases 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND phase = 2
);

INSERT INTO airdrop_phases (user_id, phase, status, amount, unlock_date, days_remaining, created_at, updated_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'testuser'),
    3,
    'PROCESSING',
    20000.0,
    NOW() + INTERVAL '90 days',  -- 90일 후 해제
    90,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM airdrop_phases 
    WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
    AND phase = 3
);

-- 시퀀스 리셋 (ID 순서 보장)
SELECT setval('airdrop_phases_id_seq', (SELECT COALESCE(MAX(id), 1) FROM airdrop_phases));
SELECT setval('airdrop_transfers_id_seq', (SELECT COALESCE(MAX(id), 1) FROM airdrop_transfers));

