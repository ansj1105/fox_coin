-- 테스트용 사용자 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다
-- 비밀번호: Test1234!@
-- 실제 BCrypt 해시 (rounds=10, salt 포함)

-- 기존 테스트 데이터 삭제 (referral_relations도 함께 삭제)
DELETE FROM referral_relations WHERE referrer_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user'));
DELETE FROM referral_relations WHERE referred_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user'));
DELETE FROM referral_stats_logs WHERE user_id IN (SELECT id FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user'));
DELETE FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user', 'referrer_user', 'no_code_user');

-- 테스트 사용자 추가
-- 아래 해시는 BCrypt.hashpw("Test1234!@", BCrypt.gensalt(10))로 생성됨
-- 비밀번호: Test1234!@
INSERT INTO users (login_id, password_hash, referral_code, status, created_at, updated_at)
VALUES 
    -- testuser (ID:1): 일반 사용자 (레퍼럴 코드 있음)
    ('testuser', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', 'REF001', 'ACTIVE', NOW(), NOW()),
    -- testuser2 (ID:2): 추가 테스트 사용자 (레퍼럴 코드 없음)
    ('testuser2', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', NULL , 'ACTIVE', NOW(), NOW()),
    -- admin_user (ID:3): 관리자
    ('admin_user', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', 'ADMIN001', 'ACTIVE', NOW(), NOW()),
    -- blocked_user (ID:4): 차단된 사용자 (레퍼럴 코드 없음 - 생성 테스트용)
    ('blocked_user', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', NULL, 'BLOCKED', NOW(), NOW()),
    -- referrer_user (ID:5): 추천인 역할 (레퍼럴 코드 있음)
    ('referrer_user', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', 'REFER123', 'ACTIVE', NOW(), NOW()),
    -- no_code_user (ID:6): 레퍼럴 코드 없는 사용자 (통계 조회 테스트용 피추천인)
    ('no_code_user', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', NULL, 'ACTIVE', NOW(), NOW())
ON CONFLICT (login_id) DO NOTHING;

-- 시퀀스 리셋 (ID 순서 보장)
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));

-- 테스트용 레퍼럴 관계 데이터 (통계 조회 테스트용)
-- no_code_user(ID:6)가 referrer_user(ID:5)의 피추천인으로 등록
INSERT INTO referral_relations (referrer_id, referred_id, level, status, created_at)
SELECT 
    (SELECT id FROM users WHERE login_id = 'referrer_user'),
    (SELECT id FROM users WHERE login_id = 'no_code_user'),
    1, 'ACTIVE', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM referral_relations 
    WHERE referrer_id = (SELECT id FROM users WHERE login_id = 'referrer_user')
    AND referred_id = (SELECT id FROM users WHERE login_id = 'no_code_user')
);
