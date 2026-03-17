ALTER TABLE mining_sessions
    ADD COLUMN IF NOT EXISTS credited_amount NUMERIC(38, 18) NOT NULL DEFAULT 0;

COMMENT ON COLUMN mining_sessions.credited_amount IS '이 세션 동안 실제로 지갑에 적립된 누적 채굴량';
