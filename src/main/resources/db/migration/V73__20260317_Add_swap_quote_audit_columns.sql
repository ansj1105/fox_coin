ALTER TABLE swaps
    ADD COLUMN IF NOT EXISTS exchange_rate DECIMAL(36, 18) NULL,
    ADD COLUMN IF NOT EXISTS fee_rate DECIMAL(18, 8) NULL,
    ADD COLUMN IF NOT EXISTS fee_amount DECIMAL(36, 18) NULL,
    ADD COLUMN IF NOT EXISTS spread_rate DECIMAL(18, 8) NULL,
    ADD COLUMN IF NOT EXISTS spread_amount DECIMAL(36, 18) NULL;

COMMENT ON COLUMN swaps.exchange_rate IS '스왑 실행 시 적용된 환율';
COMMENT ON COLUMN swaps.fee_rate IS '스왑 실행 시 적용된 수수료율';
COMMENT ON COLUMN swaps.fee_amount IS '스왑 실행 시 차감된 수수료 금액';
COMMENT ON COLUMN swaps.spread_rate IS '스왑 실행 시 적용된 스프레드율';
COMMENT ON COLUMN swaps.spread_amount IS '스왑 실행 시 차감된 스프레드 금액';
