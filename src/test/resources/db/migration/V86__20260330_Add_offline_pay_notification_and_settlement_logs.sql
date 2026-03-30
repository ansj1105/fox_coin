CREATE TABLE IF NOT EXISTS offline_pay_notification_logs (
    user_id BIGINT NOT NULL,
    log_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    title VARCHAR(255) NULL,
    message TEXT NULL,
    reason_code VARCHAR(100) NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_offline_pay_notification_logs PRIMARY KEY (user_id, log_id)
);

CREATE TABLE IF NOT EXISTS offline_pay_settlement_logs (
    user_id BIGINT NOT NULL,
    log_id VARCHAR(100) NOT NULL,
    settlement_status VARCHAR(30) NOT NULL,
    title VARCHAR(255) NULL,
    message TEXT NULL,
    reason_code VARCHAR(100) NULL,
    request_id VARCHAR(100) NULL,
    settlement_id VARCHAR(100) NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_offline_pay_settlement_logs PRIMARY KEY (user_id, log_id)
);
