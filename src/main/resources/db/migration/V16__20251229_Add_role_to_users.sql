-- 사용자 role 컬럼 추가 (INTEGER)
-- role 값: 1=ADMIN, 2=USER, 3=MANAGER (또는 다른 매핑)
ALTER TABLE users ADD COLUMN IF NOT EXISTS role INTEGER DEFAULT 2 NOT NULL;

COMMENT ON COLUMN users.role IS '사용자 권한 (1=ADMIN, 2=USER, 3=MANAGER)';

-- 기존 사용자들의 role을 USER(2)로 설정
UPDATE users SET role = 2 WHERE role IS NULL OR role = 0;

