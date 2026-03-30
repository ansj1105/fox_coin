ALTER TABLE users
    ADD COLUMN IF NOT EXISTS offline_pay_pin_failed_attempts SMALLINT DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS offline_pay_pin_locked_at TIMESTAMP NULL;

COMMENT ON COLUMN users.offline_pay_pin_failed_attempts IS '오프라인 페이 거래 비밀번호 실패 횟수';
COMMENT ON COLUMN users.offline_pay_pin_locked_at IS '오프라인 페이 거래 비밀번호 잠금 시각';
