ALTER TABLE external_transfers
    ADD COLUMN IF NOT EXISTS coin_manage_withdrawal_id VARCHAR(64) NULL;
