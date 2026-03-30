CREATE TABLE IF NOT EXISTS offline_pay_user_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    security_level_high_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    face_id_setting_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fingerprint_setting_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    payment_offline_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    payment_ble_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    payment_nfc_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    payment_approval_mode VARCHAR(32) NOT NULL DEFAULT 'LOW_TOUCH',
    settlement_auto_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    settlement_cycle_minutes SMALLINT NOT NULL DEFAULT 0,
    store_offline_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    store_ble_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    store_nfc_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    store_merchant_label VARCHAR(255) NOT NULL DEFAULT 'KORION Pay Store',
    payment_completed_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    incoming_request_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    settlement_completed_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS offline_pay_shared_details (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id VARCHAR(255) NOT NULL,
    payload_json JSONB NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
