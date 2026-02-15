-- 경고, 채굴정지, 계정차단 관리용 컬럼 (0 또는 null = false, 1 = true). coin_system_flyway V46 호환.
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_warning SMALLINT DEFAULT 0 NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_mining_suspended SMALLINT DEFAULT 0 NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_account_blocked SMALLINT DEFAULT 0 NOT NULL;

COMMENT ON COLUMN users.is_warning IS '경고 여부 (0=해제, 1=경고)';
COMMENT ON COLUMN users.is_mining_suspended IS '채굴정지 여부 (0=해제, 1=정지)';
COMMENT ON COLUMN users.is_account_blocked IS '계정차단 여부 (0=해제, 1=차단)';
