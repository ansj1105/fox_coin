-- Default sweep policy (admin can adjust)
INSERT INTO app_config (config_key, config_value)
VALUES
    ('sweep_enabled.TRON.FOXYA', 'true'),
    ('sweep_min_amount.TRON.FOXYA', '100'),
    ('sweep_enabled.TRON.USDT', 'true'),
    ('sweep_min_amount.TRON.USDT', '100'),
    ('sweep_enabled.ETH.USDT', 'true'),
    ('sweep_min_amount.ETH.USDT', '100'),
    ('sweep_enabled.ETH.ETH', 'true'),
    ('sweep_min_amount.ETH.ETH', '0.1'),
    ('sweep_enabled.BTC.BTC', 'true'),
    ('sweep_min_amount.BTC.BTC', '0.001'),
    ('sweep_gas_payer', 'ADMIN')
ON CONFLICT (config_key) DO NOTHING;
