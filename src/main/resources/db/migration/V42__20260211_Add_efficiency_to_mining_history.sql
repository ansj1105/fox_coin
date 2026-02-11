-- mining_history.efficiency 없을 수 있는 환경 호환 (체굴 내역 조회 시 column "efficiency" does not exist 방지)
ALTER TABLE mining_history ADD COLUMN IF NOT EXISTS efficiency INT NULL;
COMMENT ON COLUMN mining_history.efficiency IS '채굴 효율';
CREATE INDEX IF NOT EXISTS IDX_mining_history_efficiency ON mining_history(efficiency);
