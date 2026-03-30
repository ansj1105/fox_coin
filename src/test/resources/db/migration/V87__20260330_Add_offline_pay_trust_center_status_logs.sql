ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP NULL;

ALTER TABLE offline_pay_trust_center_snapshots
    ADD COLUMN IF NOT EXISTS sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE TABLE IF NOT EXISTS offline_pay_trust_center_status_logs (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    log_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_status VARCHAR(32) NOT NULL,
    message TEXT NULL,
    reason_code VARCHAR(64) NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, log_id)
);

CREATE INDEX IF NOT EXISTS idx_offline_pay_trust_center_status_logs_user_created_at
    ON offline_pay_trust_center_status_logs (user_id, created_at DESC);
