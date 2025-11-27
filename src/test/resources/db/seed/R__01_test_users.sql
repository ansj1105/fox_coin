-- 테스트용 사용자 데이터
-- R__로 시작하는 파일은 Repeatable migration으로, 내용이 변경되면 다시 실행됩니다
-- 비밀번호: Test1234!@
-- BCrypt Hash (rounds=10): $2a$10$N9qo8uLOickgx2ZMRZoMye7Ej1qg7WW0gqKEjJJxw0KJhHQZPqPZe

-- 기존 테스트 데이터 삭제
DELETE FROM users WHERE login_id IN ('testuser', 'testuser2', 'admin_user', 'blocked_user');

-- 테스트 사용자 추가
INSERT INTO users (login_id, password_hash, referral_code, status, created_at, updated_at)
VALUES 
    -- testuser: 일반 사용자 (비밀번호: Test1234!@)
    ('testuser', '$2a$10$N9qo8uLOickgx2ZMRZoMye7Ej1qg7WW0gqKEjJJxw0KJhHQZPqPZe', 'REF001', 'ACTIVE', NOW(), NOW()),
    -- testuser2: 추가 테스트 사용자 (비밀번호: Test1234!@)
    ('testuser2', '$2a$10$N9qo8uLOickgx2ZMRZoMye7Ej1qg7WW0gqKEjJJxw0KJhHQZPqPZe', 'REF002', 'ACTIVE', NOW(), NOW()),
    -- admin_user: 관리자 (비밀번호: Test1234!@)
    ('admin_user', '$2a$10$N9qo8uLOickgx2ZMRZoMye7Ej1qg7WW0gqKEjJJxw0KJhHQZPqPZe', 'ADMIN001', 'ACTIVE', NOW(), NOW()),
    -- blocked_user: 차단된 사용자 (비밀번호: Test1234!@)
    ('blocked_user', '$2a$10$N9qo8uLOickgx2ZMRZoMye7Ej1qg7WW0gqKEjJJxw0KJhHQZPqPZe', 'BLOCK001', 'BLOCKED', NOW(), NOW())
ON CONFLICT (login_id) DO NOTHING;

