-- Sweep TRX gas top-up thresholds (admin-configurable)
INSERT INTO app_config (config_key, config_value)
VALUES
    ('sweep_trx_min_balance.TRON', '5'),
    ('sweep_trx_topup_amount.TRON', '10')
ON CONFLICT (config_key) DO NOTHING;
