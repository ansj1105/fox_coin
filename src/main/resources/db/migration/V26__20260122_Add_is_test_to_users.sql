-- Add is_test flag to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_test SMALLINT NULL;
COMMENT ON COLUMN users.is_test IS '테스트 유저 여부 (NULL/0=아님, 1=테스트)';
