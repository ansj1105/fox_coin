-- Align hot wallet defaults with actual currency codes
INSERT INTO app_config (config_key, config_value)
VALUES
    ('hot_wallet_min.TRON.TRX', '1000'),
    ('hot_wallet_target.TRON.TRX', '5000'),
    ('hot_wallet_min.TRON.USDT', '1000'),
    ('hot_wallet_target.TRON.USDT', '5000'),
    ('hot_wallet_min.TRON.KORI', '1000'),
    ('hot_wallet_target.TRON.KORI', '5000'),
    ('hot_wallet_min.TRON.F_COIN', '1000'),
    ('hot_wallet_target.TRON.F_COIN', '5000')
ON CONFLICT (config_key) DO NOTHING;
