-- 전송한 금액만 차감하고 남은 전송가능 금액 유지 (amount - transferred_amount)
ALTER TABLE airdrop_phases ADD COLUMN IF NOT EXISTS transferred_amount DECIMAL(20, 8) NOT NULL DEFAULT 0;
COMMENT ON COLUMN airdrop_phases.transferred_amount IS '이미 지갑으로 전송한 금액. 전송가능 = amount - transferred_amount';
