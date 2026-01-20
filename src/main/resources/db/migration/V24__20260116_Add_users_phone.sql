-- 연락처 (내 정보 조회/수정용). verify-phone과 별도.
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20) NULL;
COMMENT ON COLUMN users.phone IS '연락처 (내 정보). 010-1234-5678 등';
