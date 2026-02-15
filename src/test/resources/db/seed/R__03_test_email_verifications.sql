-- 테스트용 이메일 인증 데이터

-- 기존 데이터 삭제
DELETE FROM email_verifications WHERE user_id IN (
    SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'no_code_user')
);

-- testuser (ID:1) - 이미 인증된 이메일
INSERT INTO email_verifications (user_id, email, verification_code, is_verified, verified_at, expires_at, created_at, updated_at)
SELECT
    (SELECT id FROM users WHERE login_id = 'testuser'),
    'testuser1@example.com',
    '111111',
    true,
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '7 days',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM email_verifications WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser')
);

-- testuser2 (ID:2) - 인증 완료 (레퍼럴 코드 등록/Hard Delete 테스트용)
INSERT INTO email_verifications (user_id, email, verification_code, is_verified, verified_at, expires_at, created_at, updated_at)
SELECT
    (SELECT id FROM users WHERE login_id = 'testuser2'),
    'testuser2@example.com',
    '222222',
    true,
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '7 days',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM email_verifications WHERE user_id = (SELECT id FROM users WHERE login_id = 'testuser2')
);

-- admin_user (ID:3) - 인증 완료 (Hard Delete 테스트용)
INSERT INTO email_verifications (user_id, email, verification_code, is_verified, verified_at, expires_at, created_at, updated_at)
SELECT
    (SELECT id FROM users WHERE login_id = 'admin_user'),
    'admin@example.com',
    '333333',
    true,
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '7 days',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM email_verifications WHERE user_id = (SELECT id FROM users WHERE login_id = 'admin_user')
);

-- blocked_user (ID:4) - 인증 완료 (Hard Delete after Soft Delete 테스트용)
INSERT INTO email_verifications (user_id, email, verification_code, is_verified, verified_at, expires_at, created_at, updated_at)
SELECT
    (SELECT id FROM users WHERE login_id = 'blocked_user'),
    'blocked@example.com',
    '444444',
    true,
    NOW() - INTERVAL '1 day',
    NOW() + INTERVAL '7 days',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
WHERE NOT EXISTS (
    SELECT 1 FROM email_verifications WHERE user_id = (SELECT id FROM users WHERE login_id = 'blocked_user')
);


