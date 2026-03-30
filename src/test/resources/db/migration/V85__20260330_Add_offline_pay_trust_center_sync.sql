CREATE TABLE IF NOT EXISTS offline_pay_trust_center_snapshots (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(32) NOT NULL DEFAULT 'unknown',
    device_name VARCHAR(255) NOT NULL DEFAULT '',
    tee_available BOOLEAN NOT NULL DEFAULT FALSE,
    key_signing_active BOOLEAN NOT NULL DEFAULT FALSE,
    device_registration_id VARCHAR(255) NOT NULL DEFAULT '',
    face_available BOOLEAN NOT NULL DEFAULT FALSE,
    fingerprint_available BOOLEAN NOT NULL DEFAULT FALSE,
    auth_binding_key VARCHAR(255) NOT NULL DEFAULT '',
    last_verified_auth_method VARCHAR(32) NOT NULL DEFAULT 'NONE',
    last_verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS offline_pay_proof_logs (
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

CREATE INDEX IF NOT EXISTS idx_offline_pay_proof_logs_user_created_at
    ON offline_pay_proof_logs (user_id, created_at DESC);
