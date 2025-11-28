-- 테스트용 사용자 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다
-- 비밀번호: Test1234!@
-- 실제 BCrypt 해시 (rounds=10, salt 포함)

-- 기존 테스트 데이터 삭제
DELETE FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user');

-- 테스트 사용자 추가
-- 아래 해시는 BCrypt.hashpw("Test1234!@", BCrypt.gensalt(10))로 생성됨
-- 비밀번호: Test1234!@
INSERT INTO users (login_id, password_hash, referral_code, status, created_at, updated_at)
VALUES 
    -- testuser: 일반 사용자
    ('testuser', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', NULL , 'ACTIVE', NOW(), NOW()),
    -- testuser2: 추가 테스트 사용자  
    ('testuser2', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', NULL , 'ACTIVE', NOW(), NOW()),
    -- admin_user: 관리자
    ('admin_user', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', 'ADMIN001', 'ACTIVE', NOW(), NOW()),
    -- blocked_user: 차단된 사용자
    ('blocked_user', '$2a$10$W84fmGkwKoh6wyU83VwQ8Ovw1wqZYxZ8E6RvWZzpwwZMLGNlSGtwa', 'BLOCK001', 'BLOCKED', NOW(), NOW())
ON CONFLICT (login_id) DO NOTHING;
