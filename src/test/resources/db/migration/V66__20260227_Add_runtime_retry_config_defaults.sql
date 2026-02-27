-- Runtime retry/tuning defaults (can be updated in DB without redeploy)
INSERT INTO app_config (config_key, config_value)
VALUES
    ('fcm_retry_max_retries', '5'),
    ('fcm_retry_processor_ms', '5000'),
    ('fcm_retry_batch', '20'),
    ('fcm_retry_block_ms', '700'),
    ('fcm_retry_consumer_group', 'fcm-retry-group'),
    ('exchange_rate_retry_max_retries', '6'),
    ('exchange_rate_retry_processor_ms', '10000'),
    ('exchange_rate_retry_batch', '5'),
    ('exchange_rate_retry_block_ms', '1000'),
    ('exchange_rate_retry_consumer_group', 'exchange-rate-retry-group'),
    ('external_http_connect_timeout_ms', '5000'),
    ('external_http_idle_timeout_seconds', '15')
ON CONFLICT (config_key) DO NOTHING;
