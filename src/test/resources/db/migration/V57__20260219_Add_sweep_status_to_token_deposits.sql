-- Add sweep tracking columns for token deposits
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS sweep_status VARCHAR(20) NULL;
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS sweep_tx_hash VARCHAR(255) NULL;
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS sweep_requested_at TIMESTAMP NULL;
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS sweep_submitted_at TIMESTAMP NULL;
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS sweep_failed_at TIMESTAMP NULL;
ALTER TABLE token_deposits ADD COLUMN IF NOT EXISTS sweep_error_message VARCHAR(255) NULL;

CREATE INDEX IF NOT EXISTS IDX_token_deposits_sweep_status ON token_deposits(sweep_status);
